package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipConversion
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money

/**
 * The result of checking counted stacks against chips actually bought in.
 *
 * [differenceChips] is counted minus expected, so a negative figure means chips are missing from
 * the table and a positive one means more were counted than were ever paid for. Either way the
 * host is told rather than having the books quietly balanced for them.
 */
data class Reconciliation(
    val expectedChips: Chips,
    val countedChips: Chips,
    val differenceChips: Chips,
    val differenceCash: Money,
    val uncountedSeatIds: List<Long>,
    /** Buy-in cash that is not a whole number of chips; non-zero means the game settings are off. */
    val chipRemainder: Money,
) {
    val hasUncountedSeats: Boolean get() = uncountedSeatIds.isNotEmpty()

    /**
     * True when the stacks already counted account for every chip bought in, which leaves no
     * chips for the uncounted ones to hold. Their stacks are not being guessed at: a chip count
     * cannot be negative, so zero is the only value they can take.
     */
    val uncountedAreImpliedZero: Boolean
        get() = hasUncountedSeats && differenceChips.isZero && chipRemainder.isZero

    val isBalanced: Boolean
        get() = differenceChips.isZero && chipRemainder.isZero && !hasUncountedSeats

    /** True when every stack is counted but the totals still disagree. */
    val hasDiscrepancy: Boolean get() = !differenceChips.isZero || !chipRemainder.isZero
}

/**
 * Compares the chips counted in front of the players against the chips still in play.
 *
 * Chips sold back to the bank mid-game are no longer on the table, so they are excluded from the
 * expected total; a player who returned chips is credited for them separately.
 *
 * Seats with no count yet are reported separately instead of being treated as zero, because a
 * missing count and a genuinely busted stack are very different situations for the host.
 */
fun GameSnapshot.reconcile(): Reconciliation {
    val onTable = chipsOnTable
    val expected = when (onTable) {
        is ChipConversion.Exact -> onTable.chips
        is ChipConversion.Inexact -> onTable.chips
    }
    val remainder = (onTable as? ChipConversion.Inexact)?.remainder ?: Money.ZERO
    val counted = countedChips
    val difference = counted - expected
    return Reconciliation(
        expectedChips = expected,
        countedChips = counted,
        differenceChips = difference,
        differenceCash = game.chipRate.cashFor(difference),
        uncountedSeatIds = seatsAwaitingCount.map { it.id },
        chipRemainder = remainder,
    )
}
