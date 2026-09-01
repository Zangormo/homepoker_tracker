package com.zango.pokertracker.ui.players

import androidx.annotation.StringRes
import com.zango.pokertracker.R
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

/**
 * The order the roster is listed in.
 *
 * Every order falls back to the name, so players who tie - and on a small roster plenty of them
 * do, at nought games or no result yet - keep a stable, readable order instead of shuffling about
 * whenever the list is rebuilt.
 */
enum class PlayerSort(@StringRes val label: Int) {
    NAME_A_Z(R.string.players_sort_name_a_z),
    NAME_Z_A(R.string.players_sort_name_z_a),
    WINNINGS_MOST(R.string.players_sort_winnings_most),
    WINNINGS_LEAST(R.string.players_sort_winnings_least),
    GAMES_MOST(R.string.players_sort_games_most),
    GAMES_FEWEST(R.string.players_sort_games_fewest),
    ;

    internal val comparator: Comparator<PlayerRow>
        get() = when (this) {
            NAME_A_Z -> byName
            NAME_Z_A -> byName.reversed()
            // A player with no settled game has no figure to rank, so they sit at the bottom of
            // the winnings orders rather than being read as having won or lost nothing.
            WINNINGS_MOST ->
                compareBy<PlayerRow, Money?>(nullsLast(reverseOrder())) { it.netProfit }
                    .then(byName)

            WINNINGS_LEAST ->
                compareBy<PlayerRow, Money?>(nullsLast(naturalOrder())) { it.netProfit }
                    .then(byName)
            GAMES_MOST -> compareByDescending<PlayerRow> { it.gamesPlayed }.then(byName)
            GAMES_FEWEST -> compareBy<PlayerRow> { it.gamesPlayed }.then(byName)
        }
}

/** Case is not part of a name for ordering purposes: "anna" belongs beside "Anna", not after Z. */
private val byName: Comparator<PlayerRow> = compareBy { it.name.lowercase() }

/**
 * Applies the host's chosen order, and their one filter: whether to hide everyone who has never
 * sat down. Hiding them is worth a switch of its own because a roster collects names entered for
 * a game that never happened, and they carry no figures to read.
 */
internal fun List<PlayerRow>.arrange(sort: PlayerSort, onlyWithGames: Boolean): List<PlayerRow> =
    filter { !onlyWithGames || it.hasPlayed }.sortedWith(sort.comparator)

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
    /** Listed in the chosen order, with the filter already applied. */
    val active: List<PlayerRow> = emptyList(),
    val archived: List<PlayerRow> = emptyList(),
    /** How many players there are before filtering, which is what tells an empty roster from a
     * filter that happens to match nobody. */
    val totalPlayers: Int = 0,
    val sort: PlayerSort = PlayerSort.NAME_A_Z,
    val onlyWithGames: Boolean = false,
    val isAdding: Boolean = false,
    val newPlayerName: String = "",
    val newPlayerError: UiText? = null,
    val renaming: RenameEditor? = null,
    val deleting: DeleteEditor? = null,
) {
    val isEmpty: Boolean get() = !isLoading && totalPlayers == 0

    /** Somebody is on the roster, but the filter is hiding all of them. */
    val isFilteredEmpty: Boolean
        get() = !isLoading && totalPlayers > 0 && active.isEmpty() && archived.isEmpty()
}

sealed interface PlayersEvent {
    data class Message(val text: UiText) : PlayersEvent
}
