package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipConversion
import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures model the spec's worked example: a 0.005/0.01 table whose chips are marked 1/2, so a
 * chip is worth 0.005 and a 100 BB buy-in of 1.00 is 200 chips.
 */
internal object Fixture {
    val chipRate = ChipRate(5_000)
    val bigBlind = Money(10_000)
    val buyIn = Money(1_000_000)

    fun game(
        defaultBuyIn: Money = buyIn,
        status: GameStatus = GameStatus.IN_PROGRESS,
    ) = Game(
        id = 1,
        name = "Thursday",
        smallBlind = Money(5_000),
        bigBlind = bigBlind,
        chipRate = chipRate,
        defaultBuyIn = defaultBuyIn,
        payoutRounding = Money(10_000),
        startedAt = 1_000L,
        endedAt = null,
        status = status,
    )

    fun player(id: Long, name: String) = Player(id, name, createdAt = 0L)

    fun seat(
        id: Long,
        name: String,
        buyIns: List<Money> = listOf(buyIn),
        finalChips: Chips? = null,
        cashedOutAt: Long? = null,
    ) = Seat(
        id = id,
        player = player(id, name),
        joinedAt = 1_000L + id,
        cashedOutAt = cashedOutAt,
        finalChips = finalChips,
        buyIns = buyIns.mapIndexed { index, amount ->
            BuyIn(id = id * 100 + index, amount = amount, createdAt = 1_000L + index)
        },
    )
}

class SeatTest {

    @Test
    fun `total buy in is the sum of the rows, never a stored running total`() {
        val seat = Fixture.seat(1, "Anna", buyIns = listOf(Money(1_000_000), Money(500_000)))
        assertEquals(Money(1_500_000), seat.totalBuyIn)
        assertEquals(2, seat.buyInCount)
    }

    @Test
    fun `a seat with no buy ins totals zero rather than failing`() {
        assertEquals(Money.ZERO, Fixture.seat(1, "Anna", buyIns = emptyList()).totalBuyIn)
    }

    @Test
    fun `active until cashed out`() {
        val active = Fixture.seat(1, "Anna")
        assertTrue(active.isActive)
        assertFalse(active.isCashedOut)

        val done = Fixture.seat(1, "Anna", finalChips = Chips(200), cashedOutAt = 5_000L)
        assertFalse(done.isActive)
        assertTrue(done.isCashedOut)
        assertTrue(done.hasChipCount)
    }
}

class GameSnapshotTest {

    @Test
    fun `total on the table is every buy in from every player`() {
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                Fixture.seat(1, "Anna"),
                Fixture.seat(2, "Boris", buyIns = listOf(Fixture.buyIn, Fixture.buyIn)),
                Fixture.seat(3, "Chris"),
            ),
        )
        assertEquals(Money(4_000_000), snapshot.totalOnTable)
        assertEquals(4, snapshot.totalBuyInCount)
    }

    @Test
    fun `the table total converts to chips at the game rate`() {
        val snapshot = GameSnapshot(Fixture.game(), listOf(Fixture.seat(1, "Anna")))
        // 1.00 of buy-ins at 0.005 a chip is 200 chips.
        assertEquals(ChipConversion.Exact(Chips(200)), snapshot.chipsOnTable)
    }

    @Test
    fun `a buy in that is not a whole number of chips reports the leftover`() {
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(Fixture.seat(1, "Anna", buyIns = listOf(Money(1_000_001)))),
        )
        assertEquals(ChipConversion.Inexact(Chips(200), Money(1)), snapshot.chipsOnTable)
    }

    @Test
    fun `active and cashed out players are partitioned`() {
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                Fixture.seat(1, "Anna"),
                Fixture.seat(2, "Boris", finalChips = Chips(400), cashedOutAt = 9_000L),
            ),
        )
        assertEquals(listOf("Anna"), snapshot.activeSeats.map { it.player.name })
        assertEquals(listOf("Boris"), snapshot.cashedOutSeats.map { it.player.name })
    }

    @Test
    fun `cash out value and net are derived from the chip count`() {
        val game = Fixture.game()
        val winner = Fixture.seat(1, "Anna", finalChips = Chips(300))
        val loser = Fixture.seat(2, "Boris", finalChips = Chips(100))
        val snapshot = GameSnapshot(game, listOf(winner, loser))

        assertEquals(Money(1_500_000), snapshot.cashOutValueOf(winner))
        assertEquals(Money(500_000), snapshot.cashOutValueOf(loser))
        assertEquals(Money(500_000), snapshot.netOf(winner))
        assertEquals(Money(-500_000), snapshot.netOf(loser))
    }

    @Test
    fun `an uncounted seat has no cash out value and no net`() {
        val seat = Fixture.seat(1, "Anna")
        val snapshot = GameSnapshot(Fixture.game(), listOf(seat))
        assertNull(snapshot.cashOutValueOf(seat))
        assertNull(snapshot.netOf(seat))
        assertEquals(listOf(seat.id), snapshot.seatsAwaitingCount.map { it.id })
    }

    @Test
    fun `an empty game has zero totals`() {
        val snapshot = GameSnapshot(Fixture.game(), emptyList())
        assertEquals(Money.ZERO, snapshot.totalOnTable)
        assertEquals(0, snapshot.totalBuyInCount)
        assertEquals(Chips.ZERO, snapshot.countedChips)
    }
}

class ReconciliationTest {

    private fun snapshot(vararg seats: Seat) = GameSnapshot(Fixture.game(), seats.toList())

    @Test
    fun `a game where every chip is accounted for balances`() {
        // Two players in for 1.00 each is 400 chips; they count 250 and 150.
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(150)),
        ).reconcile()

        assertEquals(Chips(400), result.expectedChips)
        assertEquals(Chips(400), result.countedChips)
        assertEquals(Chips.ZERO, result.differenceChips)
        assertEquals(Money.ZERO, result.differenceCash)
        assertTrue(result.isBalanced)
        assertFalse(result.hasDiscrepancy)
    }

    @Test
    fun `missing chips are reported as a negative difference with a cash value`() {
        // The spec's example: 12 chips unaccounted for, worth 0.06 at 0.005 a chip.
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(138)),
        ).reconcile()

        assertEquals(Chips(-12), result.differenceChips)
        assertEquals(Money(-60_000), result.differenceCash)
        assertEquals("-0.06", result.differenceCash.format())
        assertFalse(result.isBalanced)
        assertTrue(result.hasDiscrepancy)
    }

    @Test
    fun `surplus chips are reported as a positive difference`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(160)),
        ).reconcile()

        assertEquals(Chips(10), result.differenceChips)
        assertEquals(Money(50_000), result.differenceCash)
        assertTrue(result.hasDiscrepancy)
    }

    @Test
    fun `rebuys are included in what the table is expected to hold`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", buyIns = listOf(Fixture.buyIn, Fixture.buyIn), finalChips = Chips(0)),
            Fixture.seat(2, "Boris", finalChips = Chips(600)),
        ).reconcile()

        assertEquals(Chips(600), result.expectedChips)
        assertTrue(result.isBalanced)
    }

    @Test
    fun `an uncounted seat is flagged rather than counted as zero`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(400)),
            Fixture.seat(2, "Boris"),
        ).reconcile()

        assertTrue(result.hasUncountedSeats)
        assertEquals(listOf(2L), result.uncountedSeatIds)
        assertFalse(result.isBalanced)
        // The counted chips happen to match the expected total, but the books are not closed.
        assertEquals(Chips.ZERO, result.differenceChips)
        assertFalse(result.hasDiscrepancy)
    }

    @Test
    fun `buy in cash that is not a whole number of chips is surfaced separately`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", buyIns = listOf(Money(1_000_001)), finalChips = Chips(200)),
        ).reconcile()

        assertEquals(Money(1), result.chipRemainder)
        assertFalse(result.isBalanced)
        assertTrue(result.hasDiscrepancy)
    }
}
