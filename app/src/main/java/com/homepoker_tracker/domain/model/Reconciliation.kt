package com.homepoker_tracker.domain.model

import com.homepoker_tracker.core.money.ChipConversion
import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money

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

    val isBalanced: Boolean
        get() = differenceChips.isZero && chipRemainder.isZero && !hasUncountedSeats

    /** True when every stack is counted but the totals still disagree. */
    val hasDiscrepancy: Boolean get() = !differenceChips.isZero || !chipRemainder.isZero
}

/**
 * Compares the chips counted in front of the players against the chips their buy-ins paid for.
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
