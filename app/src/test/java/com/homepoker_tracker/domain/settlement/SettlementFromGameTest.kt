package com.homepoker_tracker.domain.settlement

import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.domain.model.Fixture
import com.homepoker_tracker.domain.model.GameSnapshot
import com.homepoker_tracker.domain.model.reconcile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end over the domain: buy-ins and chip counts in, payable sentences out.
 *
 * The table is the spec's worked example throughout — 0.005/0.01 on chips marked 1/2, so a chip
 * is worth 0.005 and the standard 1.00 buy-in is 200 chips.
 */
class SettlementFromGameTest {

    private val buyIn = Fixture.buyIn

    @Test
    fun `a game with a mid game joiner and a rebuy settles correctly`() {
        // Anna buys in twice, Boris once, Chris turns up later and buys in once.
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                Fixture.seat(1, "Anna", buyIns = listOf(buyIn, buyIn), finalChips = Chips(500)),
                Fixture.seat(2, "Boris", finalChips = Chips(100)),
                Fixture.seat(3, "Chris", finalChips = Chips(200)),
            ),
        )

        // 4.00 on the table is 800 chips, and 500 + 100 + 200 is exactly that.
        assertEquals(Money(4_000_000), snapshot.totalOnTable)
        assertEquals(4, snapshot.totalBuyInCount)
        assertTrue(snapshot.reconcile().isBalanced)

        val settlement = snapshot.settle()

        assertEquals(Money(500_000), settlement.nets.single { it.player.name == "Anna" }.net)
        assertEquals(Money(-500_000), settlement.nets.single { it.player.name == "Boris" }.net)
        assertEquals(Money.ZERO, settlement.nets.single { it.player.name == "Chris" }.net)
        assertEquals(listOf("Boris pays Anna 0.50"), settlement.payments.map { it.toSentence() })
        assertTrue(settlement.isBalanced)
    }

    @Test
    fun `the settlement uses the rounding unit stored on the game`() {
        val snapshot = GameSnapshot(
            game = Fixture.game().copy(payoutRounding = Money(1_000)),
            seats = listOf(
                Fixture.seat(1, "Anna", finalChips = Chips(201)),
                Fixture.seat(2, "Boris", finalChips = Chips(199)),
            ),
        )

        val settlement = snapshot.settle()

        assertEquals(Money(1_000), settlement.roundingUnit)
        assertEquals(listOf("Boris pays Anna 0.005"), settlement.payments.map { it.toSentence() })
    }

    @Test
    fun `a game the host ended with chips missing settles as far as it can`() {
        // 400 chips were bought but only 388 are counted: 12 chips, worth 0.06, are gone.
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                Fixture.seat(1, "Anna", finalChips = Chips(250)),
                Fixture.seat(2, "Boris", finalChips = Chips(138)),
            ),
        )

        val reconciliation = snapshot.reconcile()
        assertEquals(Chips(-12), reconciliation.differenceChips)
        assertEquals("-0.06", reconciliation.differenceCash.format())

        val settlement = snapshot.settle()

        assertFalse(settlement.isBalanced)
        assertEquals(Money(-60_000), settlement.imbalance)
        assertEquals(listOf("Boris pays Anna 0.25"), settlement.payments.map { it.toSentence() })
        // Boris is down 0.31 but only pays 0.25, because 0.06 of the table never came back.
        assertEquals(listOf("Boris"), settlement.unsettled.map { it.player.name })
        assertEquals(Money(-60_000), settlement.unsettled.single().net)
    }

    @Test
    fun `seats without a chip count are left out of the settlement`() {
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                Fixture.seat(1, "Anna", finalChips = Chips(400)),
                Fixture.seat(2, "Boris"),
            ),
        )

        val settlement = snapshot.settle()

        assertEquals(listOf("Anna"), settlement.nets.map { it.player.name })
        assertTrue(snapshot.reconcile().hasUncountedSeats)
    }

    @Test
    fun `the shared text reads as instructions a player can act on`() {
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                Fixture.seat(1, "Anna", buyIns = listOf(buyIn, buyIn), finalChips = Chips(100)),
                Fixture.seat(2, "Boris", finalChips = Chips(440)),
                Fixture.seat(3, "Chris", finalChips = Chips(260)),
            ),
        )

        val text = snapshot.settle().toShareText("Thursday")

        assertEquals(
            """
            Thursday — settlement

            Anna pays Boris 1.20
            Anna pays Chris 0.30
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `a game where nobody won or lost says so plainly`() {
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                Fixture.seat(1, "Anna", finalChips = Chips(200)),
                Fixture.seat(2, "Boris", finalChips = Chips(200)),
            ),
        )

        assertEquals(
            """
            Thursday — settlement

            Everyone broke even. No payments needed.
            """.trimIndent(),
            snapshot.settle().toShareText("Thursday"),
        )
    }

    @Test
    fun `a rounding adjustment is called out in the shared text`() {
        // Two players up 0.005 and one down 0.01: both winners round up to a cent, so the table
        // no longer cancels and the largest result has to take the difference.
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                Fixture.seat(1, "Anna", buyIns = listOf(Money(995_000)), finalChips = Chips(200)),
                Fixture.seat(2, "Boris", buyIns = listOf(Money(995_000)), finalChips = Chips(200)),
                Fixture.seat(3, "Chris", buyIns = listOf(Money(1_010_000)), finalChips = Chips(200)),
            ),
        )

        val settlement = snapshot.settle()

        assertEquals("Chris", settlement.adjustedPlayer?.name)
        assertTrue(settlement.hasRoundingAdjustment)
        assertTrue(
            settlement.toShareText("Thursday"),
            settlement.toShareText("Thursday")
                .contains("Rounded to the nearest 0.01. Chris absorbed 0.01."),
        )
    }
}
