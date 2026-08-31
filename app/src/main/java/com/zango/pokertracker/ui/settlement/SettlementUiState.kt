package com.zango.pokertracker.ui.settlement

import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.ui.common.ResultRow

/**
 * One payment, kept as its parts rather than a finished sentence.
 *
 * The screen sets the amount in the numeric face while the names stay in the text face, which a
 * pre-joined string cannot express. The plain-text export still comes from the domain renderer,
 * so what is shared and what is shown cannot drift apart.
 *
 * The player ids ride along because a tick against this payment is stored by the pair it moves
 * between: payments are derived from the results, so there is no row for a mark to point at.
 */
data class PaymentLine(
    val fromPlayerId: Long,
    val from: String,
    val toPlayerId: Long,
    val to: String,
    val amount: Money,
    /** Whether the host has ticked this one off as actually handed over. */
    val isPaid: Boolean = false,
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
    /** Every buy-in and rebuy the table made between them. */
    val buyInCount: Int = 0,
    /** How long the night ran, or null while the game is still going. */
    val durationLabel: String? = null,
    /** What the bank was holding at the end: everything paid in, less chips bought back. */
    val totalOnTable: Money = Money.ZERO,
    /** Exactly what the share and copy buttons hand over. */
    val shareText: String = "",
) {
    val hasPayments: Boolean get() = payments.isNotEmpty()

    /** True once every payment has been ticked off, which is what marks the game square. */
    val isFullyPaid: Boolean get() = hasPayments && payments.all { it.isPaid }

    val paidCount: Int get() = payments.count { it.isPaid }
}
