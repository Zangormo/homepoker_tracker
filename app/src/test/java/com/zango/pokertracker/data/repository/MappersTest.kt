package com.zango.pokertracker.data.repository

import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.data.local.entity.BuyInEntity
import com.zango.pokertracker.data.local.entity.ChipReturnEntity
import com.zango.pokertracker.data.local.entity.GameEntity
import com.zango.pokertracker.data.local.entity.GamePlayerEntity
import com.zango.pokertracker.data.local.entity.GamePlayerWithDetails
import com.zango.pokertracker.data.local.entity.GameSummaryRow
import com.zango.pokertracker.data.local.entity.GameWithPlayers
import com.zango.pokertracker.data.local.entity.PlayerEntity
import com.zango.pokertracker.data.local.entity.PlayerGameResultRow
import com.zango.pokertracker.data.local.entity.SettlementPaymentEntity
import com.zango.pokertracker.domain.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The boundary where raw `Long` micros become typed [Money] and [Chips].
 *
 * Everything here is a pure function, and it is the one place a chip count could be read as a
 * cash amount or a blind as a buy-in, so each field is asserted by name rather than by comparing
 * whole objects that a copy-paste slip would satisfy just as well.
 */
class MappersTest {

    private fun gameEntity(
        id: Long = 7,
        endedAt: Long? = null,
        status: GameStatus = GameStatus.IN_PROGRESS,
        fullyPaid: Boolean = false,
    ) = GameEntity(
        id = id,
        name = "Thursday",
        smallBlindMicros = 5_000,
        bigBlindMicros = 10_000,
        chipValueMicros = 5_000,
        defaultBuyInMicros = 1_000_000,
        payoutRoundingMicros = 10_000,
        startedAt = 1_000,
        endedAt = endedAt,
        status = status,
        isFullyPaid = fullyPaid,
    )

    // -----------------------------------------------------------------------------------------
    // Players
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a player row carries its name, id and archive flag across`() {
        val player = PlayerEntity(id = 3, name = "Anna", createdAt = 42, isArchived = true).toDomain()

        assertEquals(3L, player.id)
        assertEquals("Anna", player.name)
        assertEquals(42L, player.createdAt)
        assertTrue(player.isArchived)
    }

    @Test
    fun `a player on the roster is not archived`() {
        assertFalse(PlayerEntity(id = 1, name = "Boris", createdAt = 0).toDomain().isArchived)
    }

    // -----------------------------------------------------------------------------------------
    // Games
    // -----------------------------------------------------------------------------------------

    @Test
    fun `every monetary column becomes the money it stands for, not a bare number`() {
        val game = gameEntity().toDomain()

        assertEquals(Money(5_000), game.smallBlind)
        assertEquals(Money(10_000), game.bigBlind)
        assertEquals(ChipRate(5_000), game.chipRate)
        assertEquals(Money(1_000_000), game.defaultBuyIn)
        assertEquals(Money(10_000), game.payoutRounding)
    }

    @Test
    fun `a running game has no end and reports itself in progress`() {
        val game = gameEntity().toDomain()

        assertNull(game.endedAt)
        assertTrue(game.isInProgress)
        assertFalse(game.isFullyPaid)
    }

    @Test
    fun `a finished game carries its end time and paid-up flag`() {
        val game = gameEntity(endedAt = 9_000, status = GameStatus.FINISHED, fullyPaid = true)
            .toDomain()

        assertEquals(9_000L, game.endedAt)
        assertFalse(game.isInProgress)
        assertTrue(game.isFullyPaid)
    }

    // -----------------------------------------------------------------------------------------
    // Transactions
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a buy-in row becomes cash`() {
        val buyIn = BuyInEntity(id = 5, gamePlayerId = 1, amountMicros = 2_500_000, createdAt = 9)
            .toDomain()

        assertEquals(5L, buyIn.id)
        assertEquals(Money(2_500_000), buyIn.amount)
        assertEquals(9L, buyIn.createdAt)
    }

    /** Chip returns are stored as chips, never as the cash they were worth on the night. */
    @Test
    fun `a chip return row becomes chips`() {
        val returned = ChipReturnEntity(id = 6, gamePlayerId = 1, chips = 200, createdAt = 11)
            .toDomain()

        assertEquals(6L, returned.id)
        assertEquals(Chips(200), returned.chips)
        assertEquals(11L, returned.createdAt)
    }

    // -----------------------------------------------------------------------------------------
    // Seats
    // -----------------------------------------------------------------------------------------

    private fun seatRow(
        cashedOutAt: Long? = null,
        finalChipCount: Long? = null,
        buyIns: List<BuyInEntity> = emptyList(),
        chipReturns: List<ChipReturnEntity> = emptyList(),
    ) = GamePlayerWithDetails(
        seat = GamePlayerEntity(
            id = 11,
            gameId = 7,
            playerId = 3,
            joinedAt = 1_000,
            cashedOutAt = cashedOutAt,
            finalChipCount = finalChipCount,
        ),
        player = PlayerEntity(id = 3, name = "Anna", createdAt = 0),
        buyIns = buyIns,
        chipReturns = chipReturns,
    )

    @Test
    fun `a seat brings its player, its buy-ins and its returns with it`() {
        val seat = seatRow(
            finalChipCount = 340,
            buyIns = listOf(BuyInEntity(1, 11, 1_000_000, createdAt = 10)),
            chipReturns = listOf(ChipReturnEntity(2, 11, 200, createdAt = 20)),
        ).toDomain()

        assertEquals(11L, seat.id)
        assertEquals("Anna", seat.player.name)
        assertEquals(Chips(340), seat.finalChips)
        assertEquals(Money(1_000_000), seat.totalBuyIn)
        assertEquals(Chips(200), seat.returnedChips)
    }

    @Test
    fun `an uncounted seat has no final chips rather than zero`() {
        val seat = seatRow().toDomain()

        assertNull(seat.finalChips)
        assertFalse(seat.hasChipCount)
        assertTrue(seat.isActive)
    }

    @Test
    fun `a cashed-out seat is no longer active`() {
        val seat = seatRow(cashedOutAt = 5_000, finalChipCount = 0).toDomain()

        assertEquals(5_000L, seat.cashedOutAt)
        assertTrue(seat.isCashedOut)
        assertEquals(Chips.ZERO, seat.finalChips)
    }

    /**
     * Room returns related rows in no particular order. Buy-ins are shown to the host as a
     * running list of what happened, so they are put back in the order they happened.
     */
    @Test
    fun `buy-ins and returns are ordered by when they were recorded`() {
        val seat = seatRow(
            buyIns = listOf(
                BuyInEntity(3, 11, 3_000_000, createdAt = 300),
                BuyInEntity(1, 11, 1_000_000, createdAt = 100),
                BuyInEntity(2, 11, 2_000_000, createdAt = 200),
            ),
            chipReturns = listOf(
                ChipReturnEntity(2, 11, 200, createdAt = 250),
                ChipReturnEntity(1, 11, 100, createdAt = 150),
            ),
        ).toDomain()

        assertEquals(
            listOf(Money(1_000_000), Money(2_000_000), Money(3_000_000)),
            seat.buyIns.map { it.amount },
        )
        assertEquals(listOf(Chips(100), Chips(200)), seat.chipReturns.map { it.chips })
    }

    /** Seats read in the order people sat down, which is how the live screen lists them. */
    @Test
    fun `a whole game orders its seats by when each player joined`() {
        fun seat(id: Long, name: String, joinedAt: Long) = GamePlayerWithDetails(
            seat = GamePlayerEntity(id = id, gameId = 7, playerId = id, joinedAt = joinedAt),
            player = PlayerEntity(id = id, name = name, createdAt = 0),
            buyIns = emptyList(),
            chipReturns = emptyList(),
        )

        val snapshot = GameWithPlayers(
            game = gameEntity(),
            seats = listOf(
                seat(3, "Chris", joinedAt = 3_000),
                seat(1, "Anna", joinedAt = 1_000),
                seat(2, "Boris", joinedAt = 2_000),
            ),
        ).toDomain()

        assertEquals(listOf("Anna", "Boris", "Chris"), snapshot.seats.map { it.player.name })
        assertEquals(7L, snapshot.game.id)
    }

    // -----------------------------------------------------------------------------------------
    // Aggregates
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a history row keeps the counts SQL worked out and types the total`() {
        val summary = GameSummaryRow(
            game = gameEntity(endedAt = 4_600_000, status = GameStatus.FINISHED),
            playerCount = 6,
            buyInCount = 9,
            totalBuyInMicros = 9_000_000,
        ).toDomain()

        assertEquals(6, summary.playerCount)
        assertEquals(9, summary.buyInCount)
        assertEquals(Money(9_000_000), summary.totalOnTable)
        // 9.00 at 0.005 a chip is exactly 1800 chips.
        assertEquals(Chips(1_800), summary.chipsOnTable)
        assertEquals(4_599_000L, summary.durationMillis)
    }

    @Test
    fun `a running game in history has no duration yet`() {
        val summary = GameSummaryRow(gameEntity(), 4, 4, 4_000_000).toDomain()

        assertNull(summary.durationMillis)
    }

    @Test
    fun `a table total that is not a whole number of chips reports no chip figure`() {
        val summary = GameSummaryRow(gameEntity(), 4, 4, 4_000_001).toDomain()

        assertNull(summary.chipsOnTable)
    }

    // -----------------------------------------------------------------------------------------
    // A player's own history
    // -----------------------------------------------------------------------------------------

    private fun resultRow(
        status: GameStatus = GameStatus.FINISHED,
        finalChipCount: Long? = 300,
        returnedChips: Long = 0,
    ) = PlayerGameResultRow(
        playerId = 3,
        gameId = 7,
        gameName = "Thursday",
        startedAt = 1_000,
        endedAt = 9_000,
        status = status,
        chipValueMicros = 5_000,
        finalChipCount = finalChipCount,
        buyInCount = 2,
        totalBuyInMicros = 2_000_000,
        returnedChips = returnedChips,
    )

    @Test
    fun `a player's game row carries the rate it must be read at`() {
        val result = resultRow(returnedChips = 100).toDomain()

        assertEquals(ChipRate(5_000), result.chipRate)
        assertEquals(Money(2_000_000), result.totalBuyIn)
        assertEquals(Chips(100), result.returnedChips)
        assertEquals(Chips(300), result.finalChips)
        // 400 chips out at 0.005 is 2.00 against 2.00 in.
        assertEquals(Money(2_000_000), result.cashOut)
        assertEquals(Money.ZERO, result.net)
    }

    @Test
    fun `a game still running is flagged and has no result`() {
        val result = resultRow(status = GameStatus.IN_PROGRESS, finalChipCount = null).toDomain()

        assertTrue(result.isInProgress)
        assertFalse(result.isSettled)
        assertNull(result.net)
    }

    // -----------------------------------------------------------------------------------------
    // Settlement marks
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a settled payment keeps the pair and the figure it was ticked against`() {
        val mark = SettlementPaymentEntity(
            id = 1,
            gameId = 7,
            fromPlayerId = 3,
            toPlayerId = 4,
            amountMicros = 4_500_000,
            markedAt = 99,
        ).toDomain()

        assertEquals(7L, mark.gameId)
        assertEquals(3L, mark.fromPlayerId)
        assertEquals(4L, mark.toPlayerId)
        assertEquals(Money(4_500_000), mark.amount)
    }
}
