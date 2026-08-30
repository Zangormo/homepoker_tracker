package com.homepoker_tracker.ui.endgame

import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.domain.model.Reconciliation
import com.homepoker_tracker.ui.common.ResultRow

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
    val net: Money? = null,
    val error: String? = null,
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
    val headline: String,
) {
    val hasUncounted: Boolean get() = uncountedCount > 0
    val hasDiscrepancy: Boolean get() = !differenceChips.isZero || !chipRemainder.isZero
    val isClean: Boolean get() = !hasUncounted && !hasDiscrepancy
}

/**
 * Turns a [Reconciliation] into a sentence.
 *
 * Missing chips and surplus chips are worded differently on purpose: they point at different
 * mistakes. Missing usually means a stack was miscounted or someone pocketed a chip; surplus
 * usually means a rebuy was never recorded.
 */
fun Reconciliation.headline(): String = when {
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
     * A game cannot be ended with a stack still uncounted: booking an unknown stack as zero
     * would quietly hand that player's money to everyone else. A discrepancy, by contrast, only
     * needs the host to confirm they mean it.
     */
    val canFinish: Boolean
        get() = !isFinishing && !alreadyFinished && counts.isNotEmpty() &&
            reconciliation?.hasUncounted == false
}

sealed interface EndGameEvent {
    data class Finished(val gameId: Long) : EndGameEvent
    data class Message(val text: String) : EndGameEvent
}
