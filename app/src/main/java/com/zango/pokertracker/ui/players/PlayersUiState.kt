package com.zango.pokertracker.ui.players

import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.Money

/** One roster player as the list shows them. */
data class PlayerRow(
    val playerId: Long,
    val name: String,
    val gamesPlayed: Int,
    val buyInCount: Int,
    val totalPaidIn: Money,
    /** Lifetime profit over settled games. Null until at least one result exists. */
    val netProfit: Money?,
    /** Games still waiting on a chip count, and so left out of [netProfit]. */
    val openGames: Int,
    val isArchived: Boolean,
) {
    val hasPlayed: Boolean get() = gamesPlayed > 0
}

/** The rename dialog, held open while the host types. */
data class RenameEditor(
    val playerId: Long,
    val originalName: String,
    val name: String,
    val error: UiText? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && name.trim() != originalName && error == null
}

/**
 * The delete confirmation. A player who has never played can simply go; one with history cannot,
 * because their seats are what past settlements were calculated from, so the same menu entry ends
 * up offering to hide them from the roster instead.
 */
data class DeleteEditor(
    val playerId: Long,
    val name: String,
    val gamesPlayed: Int,
) {
    val hasHistory: Boolean get() = gamesPlayed > 0
}

data class PlayersUiState(
    val isLoading: Boolean = true,
    val active: List<PlayerRow> = emptyList(),
    val archived: List<PlayerRow> = emptyList(),
    val isAdding: Boolean = false,
    val newPlayerName: String = "",
    val newPlayerError: UiText? = null,
    val renaming: RenameEditor? = null,
    val deleting: DeleteEditor? = null,
) {
    val isEmpty: Boolean get() = !isLoading && active.isEmpty() && archived.isEmpty()
}

sealed interface PlayersEvent {
    data class Message(val text: UiText) : PlayersEvent
}
