package com.homepoker_tracker.ui.settlement

import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.ui.common.ResultRow

/**
 * One payment, kept as its three parts rather than a finished sentence.
 *
 * The screen sets the amount in the numeric face while the names stay in the text face, which a
 * pre-joined string cannot express. The plain-text export still comes from the domain renderer,
 * so what is shared and what is shown cannot drift apart.
 */
data class PaymentLine(
    val from: String,
    val to: String,
    val amount: Money,
)

data class SettlementUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val gameId: Long = 0,
    val gameName: String = "",
    val isFinished: Boolean = false,
    val payments: List<PaymentLine> = emptyList(),
    /** Rounding and mismatch caveats, shown under the payments. */
    val notes: List<String> = emptyList(),
    /** True when the chip counts never balanced, so the payments cannot square everyone up. */
    val hasProblem: Boolean = false,
    val results: List<ResultRow> = emptyList(),
    val totalMoved: Money = Money.ZERO,
    /** Exactly what the share and copy buttons hand over. */
    val shareText: String = "",
) {
    val hasPayments: Boolean get() = payments.isNotEmpty()
}
