package com.zango.pokertracker.ui.history

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money

/** One game in the history list. */
data class HistoryRow(
    val gameId: Long,
    val name: String,
    val dateLabel: String,
    val stakes: String,
    val playerCount: Int,
    val buyInCount: Int,
    val totalOnTable: Money,
    val chipsOnTable: Chips?,
    /** How long the game ran. Null while it is still going. */
    val durationLabel: String?,
    val isInProgress: Boolean,
    /** True once every settlement payment has been ticked off as handed over. */
    val isFullyPaid: Boolean = false,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val inProgress: List<HistoryRow> = emptyList(),
    val finished: List<HistoryRow> = emptyList(),
    /** The game the host has asked to delete, held until they confirm. */
    val pendingDeletion: HistoryRow? = null,
) {
    val isEmpty: Boolean get() = !isLoading && inProgress.isEmpty() && finished.isEmpty()
}

sealed interface HistoryEvent {
    data class Message(val text: String) : HistoryEvent
}
