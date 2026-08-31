package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one case where a blank stack may be written down as zero.
 *
 * When the stacks already counted use up every chip that was bought in, there is nothing left for
 * an uncounted seat to hold. That stack is not being guessed at: a chip count cannot be negative,
 * so zero is the only value it can take. This is what lets the end-game screen finish a night
 * where the busted players never bothered to say they had nothing.
 *
 * Everything else about reconciling is covered by `ReconciliationTest`; this is only the shortcut
 * and the conditions that must hold for it. Fixtures use the spec's table: 0.005 a chip, so a
 * 1.00 buy-in is 200 chips.
 */
class ImpliedZeroStacksTest {

    private fun snapshot(vararg seats: Seat) = GameSnapshot(Fixture.game(), seats.toList())

    @Test
    fun `one blank stack is implied zero when the counted ones use up every chip`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(400)),
            Fixture.seat(2, "Boris"),
        ).reconcile()

        assertTrue(result.uncountedAreImpliedZero)
        assertEquals(listOf(2L), result.uncountedSeatIds)
    }

    @Test
    fun `several blank stacks are all implied zero together`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(600)),
            Fixture.seat(2, "Boris"),
            Fixture.seat(3, "Chris"),
        ).reconcile()

        assertTrue(result.uncountedAreImpliedZero)
        assertEquals(listOf(2L, 3L), result.uncountedSeatIds)
    }

    @Test
    fun `chips still unaccounted for mean a blank stack cannot be assumed empty`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(300)),
            Fixture.seat(2, "Boris"),
        ).reconcile()

        assertFalse(result.uncountedAreImpliedZero)
        assertEquals(Chips(-100), result.differenceChips)
    }

    /** More chips counted than were ever issued is a problem to look at, not one to close over. */
    @Test
    fun `a surplus blocks the shortcut just as a shortfall does`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(500)),
            Fixture.seat(2, "Boris"),
        ).reconcile()

        assertFalse(result.uncountedAreImpliedZero)
    }

    /**
     * Buy-in cash that is not a whole number of chips means the settings themselves are off, and
     * no amount of assuming empty stacks will make the totals meet.
     */
    @Test
    fun `a leftover cash remainder blocks the shortcut`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", buyIns = listOf(Money(1_000_001)), finalChips = Chips(200)),
            Fixture.seat(2, "Boris", buyIns = listOf(Money.ZERO)),
        ).reconcile()

        assertTrue(result.hasUncountedSeats)
        assertEquals(Money(1), result.chipRemainder)
        assertFalse(result.uncountedAreImpliedZero)
    }

    @Test
    fun `a table with every stack counted has nothing to imply`() {
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(200)),
            Fixture.seat(2, "Boris", finalChips = Chips(200)),
        ).reconcile()

        assertFalse(result.uncountedAreImpliedZero)
        assertTrue(result.isBalanced)
    }

    @Test
    fun `an empty game implies nothing and balances trivially`() {
        val result = snapshot().reconcile()

        assertFalse(result.uncountedAreImpliedZero)
        assertFalse(result.hasUncountedSeats)
        assertTrue(result.isBalanced)
        assertEquals(Chips.ZERO, result.expectedChips)
    }

    /** A player who sold chips back and then busted is still implied zero, not implied owed. */
    @Test
    fun `chips sold back are already off the table when the shortcut is checked`() {
        val boris = Fixture.seat(2, "Boris")
            .copy(chipReturns = listOf(ChipReturn(id = 20, chips = Chips(200), createdAt = 5_000)))
        val result = snapshot(Fixture.seat(1, "Anna", finalChips = Chips(200)), boris).reconcile()

        // 400 issued, 200 handed back, 200 counted in front of Anna: Boris can only have nothing.
        assertEquals(Chips(200), result.expectedChips)
        assertTrue(result.uncountedAreImpliedZero)
    }
}
