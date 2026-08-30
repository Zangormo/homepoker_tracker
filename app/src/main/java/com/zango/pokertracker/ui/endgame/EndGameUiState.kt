package com.zango.pokertracker.ui.endgame

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Reconciliation
import com.zango.pokertracker.ui.common.ResultRow

/** One player's chip-count field, with everything that follows from what has been typed. */
data class CountRow(
    val seatId: Long,
    val name: String,
    val text: String,
    /** True when this player was cashed out during play; their count can still be corrected. */
    val wasCashedOut: Boolean,
    val chips: Chips? = null,
    val cashOutValue: Money? = null,
    val totalBuyIn: Money = Money.ZERO,
    /** Chips this player already sold back to the bank during play. */
    val returnedChips: Chips = Chips.ZERO,
    val returnedCash: Money = Money.ZERO,
    val net: Money? = null,
    val error: String? = null,
    /** Blank, but the other stacks already account for every chip, so this one must be empty. */
    val countedAsZero: Boolean = false,
) {
    val isCounted: Boolean get() = chips != null
}

/**
 * The reconciliation as the host reads it: the raw figures plus one sentence that says what,
 * if anything, is wrong.
 */
data class ReconciliationSummary(
    val expectedChips: Chips,
    val countedChips: Chips,
    val differenceChips: Chips,
    val differenceCash: Money,
    val chipRemainder: Money,
    val uncountedCount: Int,
    val uncountedAreImpliedZero: Boolean,
    val headline: String,
) {
    val hasUncounted: Boolean get() = uncountedCount > 0
    val hasDiscrepancy: Boolean get() = !differenceChips.isZero || !chipRemainder.isZero
    val isClean: Boolean get() = !hasUncounted && !hasDiscrepancy

    /** Nothing left to count, either because it was counted or because it must be zero. */
    val isComplete: Boolean get() = !hasUncounted || uncountedAreImpliedZero

    /** Green treatment: the chips add up, whether or not every field was filled in. */
    val addsUp: Boolean get() = !hasDiscrepancy && isComplete
}

/**
 * Turns a [Reconciliation] into a sentence.
 *
 * Missing chips and surplus chips are worded differently on purpose: they point at different
 * mistakes. Missing usually means a stack was miscounted or someone pocketed a chip; surplus
 * usually means a rebuy was never recorded.
 */
fun Reconciliation.headline(): String = when {
    uncountedAreImpliedZero && uncountedSeatIds.size == 1 ->
        "Every chip is accounted for — 1 empty stack recorded as 0"

    uncountedAreImpliedZero ->
        "Every chip is accounted for — ${uncountedSeatIds.size} empty stacks recorded as 0"

    hasUncountedSeats && uncountedSeatIds.size == 1 -> "1 player still needs a chip count"
    hasUncountedSeats -> "${uncountedSeatIds.size} players still need a chip count"
    !chipRemainder.isZero ->
        "Buy-ins include ${chipRemainder.format()} that is not a whole number of chips"

    differenceChips.isNegative ->
        "${differenceChips.abs().count} chips unaccounted for — worth ${differenceCash.abs().format()}"

    differenceChips.isPositive ->
        "${differenceChips.count} chips more than were bought in — worth ${differenceCash.format()}"

    else -> "Every chip is accounted for"
}

fun Reconciliation.toSummary(): ReconciliationSummary = ReconciliationSummary(
    expectedChips = expectedChips,
    countedChips = countedChips,
    differenceChips = differenceChips,
    differenceCash = differenceCash,
    chipRemainder = chipRemainder,
    uncountedCount = uncountedSeatIds.size,
    uncountedAreImpliedZero = uncountedAreImpliedZero,
    headline = headline(),
)

data class EndGameUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val gameId: Long = 0,
    val gameName: String = "",
    val alreadyFinished: Boolean = false,
    val chipValueLabel: String = "",
    val counts: List<CountRow> = emptyList(),
    val results: List<ResultRow> = emptyList(),
    val reconciliation: ReconciliationSummary? = null,
    val isConfirmingMismatch: Boolean = false,
    val isFinishing: Boolean = false,
) {
    /**
     * A game cannot be ended while a stack is genuinely unknown, because booking it as zero would
     * quietly hand that player's money to everyone else. A blank stack is only unknown while the
     * chips do not already add up; once they do, it is provably empty and the game can end. A
     * discrepancy, by contrast, only needs the host to confirm they mean it.
     */
    val canFinish: Boolean
        get() = !isFinishing && !alreadyFinished && counts.isNotEmpty() &&
            reconciliation?.isComplete == true

    /** Seats to record as zero on finishing, in the order they are shown. */
    val seatsCountedAsZero: List<Long>
        get() = counts.filter { it.countedAsZero }.map { it.seatId }
}

sealed interface EndGameEvent {
    data class Finished(val gameId: Long) : EndGameEvent
    data class Message(val text: String) : EndGameEvent
}
