package com.homepoker_tracker.ui.settlement

import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.ui.common.ResultRow

data class SettlementUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val gameId: Long = 0,
    val gameName: String = "",
    val isFinished: Boolean = false,
    /** "Anna pays Boris 4.50" — one instruction per line, nothing to interpret. */
    val payments: List<String> = emptyList(),
    /** Rounding and mismatch caveats, shown under the payments. */
    val notes: List<String> = emptyList(),
    val results: List<ResultRow> = emptyList(),
    val totalMoved: Money = Money.ZERO,
    /** Exactly what the share and copy buttons hand over. */
    val shareText: String = "",
) {
    val hasPayments: Boolean get() = payments.isNotEmpty()
}
