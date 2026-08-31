package com.zango.pokertracker.domain

import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.money.sum
import com.zango.pokertracker.domain.model.BuyIn
import com.zango.pokertracker.domain.model.ChipReturn
import com.zango.pokertracker.domain.model.Game
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.GameStatus
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.Seat
import com.zango.pokertracker.domain.model.reconcile
import com.zango.pokertracker.domain.settlement.notes
import com.zango.pokertracker.domain.settlement.settle
import com.zango.pokertracker.domain.settlement.toSentence
import com.zango.pokertracker.domain.settlement.toShareText
import com.zango.pokertracker.ui.common.toResultRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One night, start to finish, with every awkward thing that actually happens at a home game.
 *
 * The individual pieces are covered elsewhere; the point of this one is the joins between them —
 * that a rebuy, a mid-game chip sale and a player who arrives late all still land in a settlement
 * that adds up, and that the same arithmetic is reached whether you read it off the results
 * table, the reconciliation, or the message pasted into the group chat afterwards.
 *
 * The table is the spec's: 0.005/0.01 with chips marked 1/2, so a chip is worth 0.005 and a 1.00
 * buy-in is 200 chips.
 */
class WholeNightTest {

    private val rate = ChipRate(5_000)

    private val anna = Player(1, "Anna", createdAt = 0)
    private val boris = Player(2, "Boris", createdAt = 0)
    private val chris = Player(3, "Chris", createdAt = 0)
    private val dana = Player(4, "Dana", createdAt = 0)

    private val game = Game(
        id = 1,
        name = "Thursday",
        smallBlind = Money(5_000),
        bigBlind = Money(10_000),
        chipRate = rate,
        defaultBuyIn = Money(1_000_000),
        payoutRounding = Money(10_000),
        startedAt = 20_00_000,
        endedAt = 20_00_000 + 4 * 3_600_000,
        status = GameStatus.FINISHED,
    )

    private fun seat(
        id: Long,
        player: Player,
        joinedAt: Long,
        buyIns: List<Long>,
        returns: List<Long> = emptyList(),
        finalChips: Long?,
    ) = Seat(
        id = id,
        player = player,
        joinedAt = joinedAt,
        cashedOutAt = game.endedAt,
        finalChips = finalChips?.let { Chips(it) },
        buyIns = buyIns.mapIndexed { index, micros ->
            BuyIn(id = id * 100 + index, amount = Money(micros), createdAt = joinedAt + index)
        },
        chipReturns = returns.mapIndexed { index, chips ->
            ChipReturn(id = id * 1000 + index, chips = Chips(chips), createdAt = joinedAt + 500)
        },
    )

    /**
     * Anna bought in twice. Boris ran the table up and sold 300 chips back to the bank when the
     * physical chips ran short. Chris busted. Dana turned up two hours late for one buy-in.
     *
     * Paid in: 2.00 + 1.00 + 1.00 + 1.00 = 5.00, so 1000 chips were issued.
     * Sold back: 300 chips, so 700 should be on the table at the end.
     */
    private val night = GameSnapshot(
        game = game,
        seats = listOf(
            seat(1, anna, joinedAt = 20_00_000, buyIns = listOf(1_000_000, 1_000_000), finalChips = 150),
            seat(2, boris, joinedAt = 20_00_000, buyIns = listOf(1_000_000), returns = listOf(300), finalChips = 500),
            seat(3, chris, joinedAt = 20_00_000, buyIns = listOf(1_000_000), finalChips = 0),
            seat(4, dana, joinedAt = 20_00_000 + 2 * 3_600_000, buyIns = listOf(1_000_000), finalChips = 50),
        ),
    )

    // -----------------------------------------------------------------------------------------
    // What the bank is holding
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the bank holds everything paid in, less what it paid back out for chips`() {
        assertEquals(Money(5_000_000), night.totalBuyIns)
        assertEquals(Chips(300), night.returnedChips)
        assertEquals(Money(1_500_000), night.returnedCash)
        assertEquals(Money(3_500_000), night.totalOnTable)
        assertEquals(Chips(700), night.chipsOnTable.exactOrNull())
        assertEquals(5, night.totalBuyInCount)
    }

    @Test
    fun `the counted stacks account for every chip still in play`() {
        val result = night.reconcile()

        assertEquals(Chips(700), result.expectedChips)
        assertEquals(Chips(700), result.countedChips)
        assertTrue(result.isBalanced)
        assertFalse(result.hasUncountedSeats)
        assertEquals(Money.ZERO, result.chipRemainder)
    }

    // -----------------------------------------------------------------------------------------
    // What each player did
    // -----------------------------------------------------------------------------------------

    @Test
    fun `each player's result is what they took off the table minus what they put on it`() {
        val rows = night.toResultRows().associateBy { it.name }

        // Anna: 2.00 in, 150 chips out = 0.75.
        assertEquals(Money(2_000_000), rows.getValue("Anna").totalBuyIn)
        assertEquals(Money(-1_250_000), rows.getValue("Anna").net)

        // Boris: 1.00 in; 300 chips sold back plus 500 in hand is 800 chips = 4.00.
        assertEquals(Chips(800), rows.getValue("Boris").chipsOut)
        assertEquals(Money(4_000_000), rows.getValue("Boris").cashOut)
        assertEquals(Money(3_000_000), rows.getValue("Boris").net)

        // Chris busted: 1.00 in, nothing out.
        assertEquals(Chips.ZERO, rows.getValue("Chris").chipsOut)
        assertEquals(Money(-1_000_000), rows.getValue("Chris").net)

        // Dana came late and left with 50 chips = 0.25.
        assertEquals(Money(-750_000), rows.getValue("Dana").net)
    }

    @Test
    fun `the night neither invented nor lost money`() {
        val nets = night.toResultRows().mapNotNull { it.net }

        assertEquals(4, nets.size)
        assertEquals(Money.ZERO, nets.sum())
    }

    // -----------------------------------------------------------------------------------------
    // Who pays whom
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the settlement squares everyone up in as few payments as possible`() {
        val settlement = night.settle()

        // Three losers, one winner: three payments, which is n-1 for the four players who moved.
        assertEquals(
            listOf(
                "Anna pays Boris 1.25",
                "Dana pays Boris 0.75",
                "Chris pays Boris 1.00",
            ).sorted(),
            settlement.payments.map { it.toSentence() }.sorted(),
        )
        assertTrue(settlement.isBalanced)
        assertTrue(settlement.unsettled.isEmpty())
        assertTrue(settlement.notes().isEmpty())
    }

    @Test
    fun `what Boris collects is exactly what he won`() {
        val settlement = night.settle()
        val collected = settlement.payments.filter { it.to.name == "Boris" }.map { it.amount }.sum()

        assertEquals(Money(3_000_000), collected)
    }

    @Test
    fun `every payment is a figure that can be handed over in cash`() {
        night.settle().payments.forEach { payment ->
            assertEquals(0L, payment.amount.micros % game.payoutRounding.micros)
        }
    }

    // -----------------------------------------------------------------------------------------
    // What gets pasted into the group chat
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the shared message stands on its own without the app`() {
        val text = night.settle().toShareText(game.name)
        val lines = text.lines()

        assertEquals("Thursday — settlement", lines.first())
        assertEquals("", lines[1])
        assertEquals(3, lines.drop(2).count { it.contains(" pays ") })
        // Nothing to caveat: the table reconciled and every figure is payable.
        assertFalse(text.contains("Chip counts came out"))
        assertFalse(text.contains("Rounded to"))
    }

    // -----------------------------------------------------------------------------------------
    // The same night with something gone wrong
    // -----------------------------------------------------------------------------------------

    /**
     * Twelve chips are missing at the end — knocked on the floor, or a stack miscounted. The host
     * can still finish, but every figure that follows has to say so.
     */
    @Test
    fun `a night that does not reconcile is settled with the shortfall named`() {
        val short = night.copy(
            seats = night.seats.map { seat ->
                if (seat.player.name == "Anna") seat.copy(finalChips = Chips(138)) else seat
            },
        )

        val reconciliation = short.reconcile()
        assertEquals(Chips(-12), reconciliation.differenceChips)
        assertEquals(Money(-60_000), reconciliation.differenceCash)
        assertTrue(reconciliation.hasDiscrepancy)

        val settlement = short.settle()
        assertFalse(settlement.isBalanced)
        assertEquals(Money(-60_000), settlement.imbalance)

        // The losers between them owe 0.06 more than Boris is owed, because that is where the
        // missing chips came off. Every winner is still paid in full and the leftover is left
        // sitting on the last debtor by name, rather than shaved off the winner quietly.
        assertEquals(Money(3_000_000), night.toResultRows().first { it.name == "Boris" }.net)
        assertEquals(
            Money(3_000_000),
            settlement.payments.filter { it.to.name == "Boris" }.map { it.amount }.sum(),
        )
        assertEquals(listOf("Dana"), settlement.unsettled.map { it.player.name })
        assertEquals(Money(-60_000), settlement.unsettled.single().net)

        val text = settlement.toShareText(game.name)
        assertTrue(text.contains("Chip counts came out 0.06 short of the buy-ins"))
        assertTrue(text.contains("Dana still owes 0.06."))
    }

    /**
     * A stack nobody counted is not zero. The player is left out of the settlement entirely
     * rather than being booked as having lost everything.
     */
    @Test
    fun `a player whose stack was never counted is left out rather than assumed busted`() {
        val unfinished = night.copy(
            seats = night.seats.map { seat ->
                if (seat.player.name == "Dana") seat.copy(finalChips = null) else seat
            },
        )

        assertEquals(listOf(4L), unfinished.reconcile().uncountedSeatIds)
        assertNull(unfinished.toResultRows().first { it.name == "Dana" }.net)
        assertTrue(
            unfinished.settle().payments.none { it.from.name == "Dana" || it.to.name == "Dana" },
        )
    }

    // -----------------------------------------------------------------------------------------
    // The night as it looks afterwards
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the game reads as finished, with a duration and a chip total`() {
        assertFalse(game.isInProgress)
        assertEquals(4 * 3_600_000L, game.endedAt!! - game.startedAt)
        assertEquals(Chips(700), night.chipsOnTable.exactOrNull())
        assertTrue(night.hasReturns)
        assertTrue(night.seatsAwaitingCount.isEmpty())
    }

    @Test
    fun `everyone is cashed out once the night is over`() {
        assertTrue(night.activeSeats.isEmpty())
        assertEquals(4, night.cashedOutSeats.size)
    }
}
