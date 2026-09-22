package com.zango.pokertracker.ui.history

import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money

/** What the host sees before taking over a game from another phone. */
data class IncomingGame(
    val name: String,
    val dateLabel: String,
    val playerCount: Int,
    val buyInCount: Int,
    val totalOnTable: Money,
    /** This phone already has the game, and taking it over replaces that copy. */
    val replacesCopy: Boolean,
)

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
    /** A game handed over from another phone, shown for the host to confirm before it lands. */
    val incoming: IncomingGame? = null,
) {
    val isEmpty: Boolean get() = !isLoading && inProgress.isEmpty() && finished.isEmpty()
}

sealed interface HistoryEvent {
    data class Message(val text: UiText) : HistoryEvent

    /** A game was taken over: open it, since carrying on with it is the whole point. */
    data class OpenGame(val gameId: Long) : HistoryEvent
}
