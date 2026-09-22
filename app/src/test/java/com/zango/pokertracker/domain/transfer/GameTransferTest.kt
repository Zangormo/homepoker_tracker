package com.zango.pokertracker.domain.transfer

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.BuyIn
import com.zango.pokertracker.domain.model.ChipReturn
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTransferTest {

    private val start = 1_758_560_000_000L

    private fun night(players: Int = 3, rebuysEach: Int = 1): GameSnapshot {
        val names = listOf("Anna", "Boris", "Chris", "Dina", "Erik", "Farida", "Gus", "Hana", "Igor", "Jon")
        return GameSnapshot(
            game = Fixture.game().copy(
                startedAt = start,
                bombPotIntervalMinutes = 30,
                isFiretruckGame = true,
                isRandomSeating = true,
            ),
            seats = (0 until players).map { index ->
                Fixture.seat(index + 1L, names[index]).copy(
                    joinedAt = start + index,
                    tablePosition = players - 1 - index,
                    buyIns = (0..rebuysEach).map { n ->
                        BuyIn(id = index * 100L + n, amount = Money(1_000_000), createdAt = start + n * 1_800_000L)
                    },
                    chipReturns = listOf(ChipReturn(index.toLong(), Chips(40), start + 3_600_000L)),
                    cashedOutAt = if (index == 0) start + 7_200_000L else null,
                    finalChips = if (index == 0) Chips(350) else null,
                )
            },
        )
    }

    private fun roundTrip(transfer: GameTransfer): GameTransfer =
        (GameTransferCodec.decode(GameTransferCodec.encode(transfer)) as DecodedTransfer.Game).transfer

    @Test
    fun `a game survives the trip unchanged`() {
        val transfer = night().toTransfer()

        assertEquals(transfer, roundTrip(transfer))
    }

    @Test
    fun `times travel relative to the start and come back to the same moments`() {
        val snapshot = night()
        val transfer = roundTrip(snapshot.toTransfer())

        assertEquals(snapshot.game.startedAt, transfer.startedAt)
        val anna = transfer.seats.first()
        assertEquals(snapshot.seats.first().cashedOutAt, transfer.startedAt + anna.cashedOutAfter!!)
        assertEquals(1_800_000L, anna.buyIns[1].after)
        assertEquals(350L, anna.finalChips)
        assertEquals(2, anna.tablePosition)
    }

    @Test
    fun `side games and seating travel with the game`() {
        val transfer = roundTrip(night().toTransfer())

        assertEquals(30, transfer.bombPotIntervalMinutes)
        assertTrue(transfer.isFiretruckGame)
        assertTrue(transfer.isRandomSeating)
        assertTrue(transfer.isRunning)
        assertNull(transfer.endedAfter)
    }

    @Test
    fun `a full table with a long night still fits in one QR code`() {
        // Ten players who each bought in five times and sold chips back: a big night for a home game.
        val encoded = GameTransferCodec.encode(night(players = 10, rebuysEach = 4).toTransfer())

        assertTrue("${encoded.length} characters", encoded.length <= GameTransferCodec.MAX_QR_LENGTH)
    }

    @Test
    fun `something that is not ours is turned away`() {
        listOf("", "https://example.com", "PKTG1:not-base64!!", "PKTG1:AAAA").forEach { text ->
            assertEquals(text, DecodedTransfer.NotAGame, GameTransferCodec.decode(text))
        }
    }

    @Test
    fun `a game from a newer app is refused rather than misread`() {
        val newer = night().toTransfer().copy(version = GameTransfer.FORMAT_VERSION + 1)

        assertEquals(DecodedTransfer.TooNew, GameTransferCodec.decode(GameTransferCodec.encode(newer)))
    }

    @Test
    fun `a game with nonsense in it is turned away`() {
        val broken = night().toTransfer().copy(bigBlindMicros = 1)

        assertEquals(DecodedTransfer.NotAGame, GameTransferCodec.decode(GameTransferCodec.encode(broken)))
    }

    @Test
    fun `the table total nets off chips sold back to the bank`() {
        val transfer = night(players = 1, rebuysEach = 0).toTransfer()

        // One buy-in of 1.00, less 40 chips at 0.005 each.
        assertEquals(1_000_000L - 40 * Fixture.chipRate.chipValueMicros, transfer.totalOnTableMicros)
    }
}
