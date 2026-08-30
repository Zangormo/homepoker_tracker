package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipConversion
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.money.sum
import com.zango.pokertracker.domain.settlement.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chips-ran-out case: a player sells chips back to the bank mid-game so the next buy-in can
 * be paid out in them, and keeps playing.
 *
 * Fixtures use the spec's table throughout: 0.005 a chip, so a 1.00 buy-in is 200 chips.
 */
class ChipReturnTest {

    private fun seatWithReturn(
        id: Long,
        name: String,
        buyIns: List<Money> = listOf(Fixture.buyIn),
        returned: Long? = null,
        finalChips: Long? = null,
    ) = Fixture.seat(
        id = id,
        name = name,
        buyIns = buyIns,
        finalChips = finalChips?.let { Chips(it) },
    ).copy(
        chipReturns = returned?.let { listOf(ChipReturn(id * 10, Chips(it), createdAt = 5_000L)) }
            ?: emptyList(),
    )

    @Test
    fun `selling chips back takes them off the table but leaves the buy-in standing`() {
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                seatWithReturn(1, "Anna", returned = 200),
                seatWithReturn(2, "Boris"),
            ),
        )

        // Both paid 1.00 in, so 400 chips were issued; Anna handed 200 straight back.
        assertEquals(Money(2_000_000), snapshot.totalBuyIns)
        assertEquals(Chips(200), snapshot.returnedChips)
        assertEquals(Money(1_000_000), snapshot.returnedCash)
        // The bank is holding 1.00, and 200 chips are still out there.
        assertEquals(Money(1_000_000), snapshot.totalOnTable)
        assertEquals(ChipConversion.Exact(Chips(200)), snapshot.chipsOnTable)
    }

    @Test
    fun `a player is credited for chips they sold back as well as the stack they end with`() {
        val anna = seatWithReturn(1, "Anna", returned = 200, finalChips = 150)
        val snapshot = GameSnapshot(Fixture.game(), listOf(anna))

        assertEquals(Chips(350), anna.chipsOut)
        // 350 chips at 0.005 is 1.75, against a 1.00 buy-in.
        assertEquals(Money(1_750_000), snapshot.cashOutValueOf(anna))
        assertEquals(Money(750_000), snapshot.netOf(anna))
    }

    @Test
    fun `the worked example from the table balances end to end`() {
        // Anna and Boris buy in for 1.00 each. The chips run out, so Anna sells 200 back and
        // Chris buys in for 1.00 and is paid out in those very chips.
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                seatWithReturn(1, "Anna", returned = 200, finalChips = 150),
                seatWithReturn(2, "Boris", finalChips = 50),
                seatWithReturn(3, "Chris", finalChips = 200),
            ),
        )

        // 3.00 bought in, 1.00 handed back, so the bank holds 2.00 and 400 chips are in play.
        assertEquals(Money(3_000_000), snapshot.totalBuyIns)
        assertEquals(Money(2_000_000), snapshot.totalOnTable)
        assertEquals(Chips(400), snapshot.countedChips)

        val reconciliation = snapshot.reconcile()
        assertEquals(Chips(400), reconciliation.expectedChips)
        assertTrue(reconciliation.isBalanced)

        val settlement = snapshot.settle()
        assertEquals(Money.ZERO, settlement.imbalance)
        assertTrue(settlement.isBalanced)
        // Anna is up 0.75 on 350 chips out, Boris down 0.75, Chris square.
        assertEquals(Money(750_000), settlement.nets.single { it.player.name == "Anna" }.net)
        assertEquals(Money(-750_000), settlement.nets.single { it.player.name == "Boris" }.net)
        assertEquals(Money.ZERO, settlement.nets.single { it.player.name == "Chris" }.net)
        assertEquals(listOf("Boris"), settlement.payments.map { it.from.name })
    }

    @Test
    fun `results never invent or destroy money, however often chips go back to the bank`() {
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                seatWithReturn(1, "Anna", returned = 120, finalChips = 40),
                seatWithReturn(2, "Boris", returned = 60, finalChips = 180),
                seatWithReturn(3, "Chris", finalChips = 200),
            ),
        )

        assertTrue(snapshot.reconcile().isBalanced)
        assertEquals(Money.ZERO, snapshot.seats.mapNotNull { snapshot.netOf(it) }.sum())
    }

    @Test
    fun `chips sold back are not expected back on the table at the end`() {
        // Anna sold 200 back, so only 200 of the 400 issued should still be countable.
        val snapshot = GameSnapshot(
            game = Fixture.game(),
            seats = listOf(
                seatWithReturn(1, "Anna", returned = 200, finalChips = 0),
                seatWithReturn(2, "Boris", finalChips = 200),
            ),
        )

        val reconciliation = snapshot.reconcile()
        assertEquals(Chips(200), reconciliation.expectedChips)
        assertEquals(Chips(200), reconciliation.countedChips)
        assertTrue(reconciliation.isBalanced)
    }

    @Test
    fun `a player who sold chips back and busted still shows the money they took`() {
        val anna = seatWithReturn(1, "Anna", returned = 200, finalChips = 0)
        val snapshot = GameSnapshot(Fixture.game(), listOf(anna))

        assertEquals(Chips(200), anna.chipsOut)
        assertEquals(Money(1_000_000), snapshot.cashOutValueOf(anna))
        // They put 1.00 in and took 1.00 back out, so they are square, not down a buy-in.
        assertEquals(Money.ZERO, snapshot.netOf(anna))
    }
}
