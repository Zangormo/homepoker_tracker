package com.zango.pokertracker.ui.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.data.repository.CreatePlayerResult
import com.zango.pokertracker.data.repository.DeletePlayerResult
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.data.repository.RenamePlayerResult
import com.zango.pokertracker.domain.model.NameRules
import com.zango.pokertracker.domain.model.PlayerStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The roster, with what each player has put in and taken out across every game they have played.
 *
 * This is the only place a player can be renamed or taken off the roster, so both sit behind a
 * per-row menu and a confirmation rather than anywhere a scrolling thumb can reach.
 */
@HiltViewModel
class PlayersViewModel @Inject constructor(
    private val repository: PokerRepository,
) : ViewModel() {

    private val editing = MutableStateFlow(Editing())

    private val listing = MutableStateFlow(Listing())

    private val eventChannel = Channel<PlayersEvent>(Channel.BUFFERED)
    val events: Flow<PlayersEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<PlayersUiState> =
        combine(repository.observePlayerStats(), editing, listing, ::buildState)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = PlayersUiState(),
            )

    // -----------------------------------------------------------------------------------------
    // Ordering and filtering
    // -----------------------------------------------------------------------------------------

    fun onSortSelected(sort: PlayerSort) = listing.update { it.copy(sort = sort) }

    fun onOnlyWithGamesChange(enabled: Boolean) =
        listing.update { it.copy(onlyWithGames = enabled) }

    // -----------------------------------------------------------------------------------------
    // Adding
    // -----------------------------------------------------------------------------------------

    fun onNewPlayerNameChange(value: String) {
        editing.update { it.copy(newPlayerName = value, newPlayerError = null) }
    }

    fun onAddRequested() {
        editing.update { it.copy(isAdding = true, newPlayerName = "", newPlayerError = null) }
    }

    fun onDismissAdd() {
        editing.update { it.copy(isAdding = false, newPlayerName = "", newPlayerError = null) }
    }

    fun onAddPlayer() {
        val name = editing.value.newPlayerName
        viewModelScope.launch {
            when (val result = repository.createPlayer(name)) {
                is CreatePlayerResult.Created -> {
                    editing.update {
                        it.copy(isAdding = false, newPlayerName = "", newPlayerError = null)
                    }
                    eventChannel.send(
                        PlayersEvent.Message(
                            UiText.of(R.string.message_player_added, result.player.name),
                        ),
                    )
                }

                is CreatePlayerResult.NameTaken -> editing.update {
                    it.copy(
                        newPlayerError = UiText.of(
                            R.string.error_name_taken,
                            result.existing.name,
                        ),
                    )
                }

                CreatePlayerResult.BlankName ->
                    editing.update {
                        it.copy(newPlayerError = UiText.of(R.string.error_name_required))
                    }

                CreatePlayerResult.NameTooLong -> editing.update {
                    it.copy(
                        newPlayerError = NameRules.playerNameTooLongMessage(),
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Renaming
    // -----------------------------------------------------------------------------------------

    fun onRenameRequested(playerId: Long) {
        val row = rowFor(playerId) ?: return
        editing.update { it.copy(renaming = RenameEditor(playerId, row.name, row.name)) }
    }

    fun onRenameTextChange(value: String) {
        editing.update { state ->
            state.copy(renaming = state.renaming?.copy(name = value, error = null))
        }
    }

    fun onDismissRename() {
        editing.update { it.copy(renaming = null) }
    }

    /**
     * A rename reaches every game the player has ever been in, which is the point: the roster row
     * and the settlements it produced are one person, so past results follow the new name rather
     * than keeping a spelling nobody uses any more.
     */
    fun onConfirmRename() {
        val editor = editing.value.renaming ?: return
        viewModelScope.launch {
            when (val result = repository.renamePlayer(editor.playerId, editor.name)) {
                is RenamePlayerResult.Renamed -> {
                    editing.update { it.copy(renaming = null) }
                    eventChannel.send(
                        PlayersEvent.Message(
                            UiText.of(
                                R.string.message_player_renamed,
                                editor.originalName,
                                result.player.name,
                            ),
                        ),
                    )
                }

                is RenamePlayerResult.NameTaken ->
                    setRenameError(UiText.of(R.string.error_name_taken, result.existing.name))

                RenamePlayerResult.BlankName ->
                    setRenameError(UiText.of(R.string.error_name_required))

                RenamePlayerResult.NameTooLong -> setRenameError(NameRules.playerNameTooLongMessage())

                RenamePlayerResult.NotFound -> {
                    editing.update { it.copy(renaming = null) }
                    eventChannel.send(PlayersEvent.Message(UiText.of(R.string.message_player_gone)))
                }
            }
        }
    }

    private fun setRenameError(message: UiText) {
        editing.update { state -> state.copy(renaming = state.renaming?.copy(error = message)) }
    }

    // -----------------------------------------------------------------------------------------
    // Removing
    // -----------------------------------------------------------------------------------------

    fun onDeleteRequested(playerId: Long) {
        val row = rowFor(playerId) ?: return
        editing.update { it.copy(deleting = DeleteEditor(playerId, row.name, row.gamesPlayed)) }
    }

    fun onDismissDelete() {
        editing.update { it.copy(deleting = null) }
    }

    /**
     * A player who has never sat down leaves for good. One with history is hidden instead: their
     * seats are what past settlements were calculated from, and removing them would quietly
     * change games that have already been paid out.
     */
    fun onConfirmDelete() {
        val editor = editing.value.deleting ?: return
        editing.update { it.copy(deleting = null) }
        viewModelScope.launch {
            if (editor.hasHistory) {
                hide(editor.playerId, UiText.of(R.string.message_player_hidden, editor.name))
                return@launch
            }
            when (repository.deletePlayer(editor.playerId)) {
                DeletePlayerResult.Deleted -> eventChannel.send(
                    PlayersEvent.Message(UiText.of(R.string.message_player_deleted, editor.name)),
                )

                // They were seated between the dialog opening and this tap.
                is DeletePlayerResult.HasHistory -> hide(
                    editor.playerId,
                    UiText.of(R.string.message_player_now_has_games, editor.name),
                )
            }
        }
    }

    fun onRestore(playerId: Long) {
        viewModelScope.launch { repository.setPlayerArchived(playerId, archived = false) }
    }

    private suspend fun hide(playerId: Long, message: UiText) {
        repository.setPlayerArchived(playerId, archived = true)
        eventChannel.send(PlayersEvent.Message(message))
    }

    private fun rowFor(playerId: Long): PlayerRow? =
        uiState.value.let { it.active + it.archived }.firstOrNull { it.playerId == playerId }

    private fun buildState(
        stats: List<PlayerStats>,
        editing: Editing,
        listing: Listing,
    ): PlayersUiState {
        val rows = stats.map { it.toRow() }
        // The hidden section is ordered and filtered alongside the roster proper: it is the same
        // list of people, and a host who asked for "most games first" means it there too.
        fun arrange(archived: Boolean) = rows
            .filter { it.isArchived == archived }
            .arrange(listing.sort, listing.onlyWithGames)

        return PlayersUiState(
            isLoading = false,
            active = arrange(archived = false),
            archived = arrange(archived = true),
            totalPlayers = rows.size,
            sort = listing.sort,
            onlyWithGames = listing.onlyWithGames,
            isAdding = editing.isAdding,
            newPlayerName = editing.newPlayerName,
            newPlayerError = editing.newPlayerError,
            renaming = editing.renaming,
            deleting = editing.deleting,
        )
    }

    /**
     * How the host wants the roster listed. Kept apart from [Editing] because it outlives any one
     * dialog, and apart from the database because it is a way of looking, not a fact about anyone.
     */
    private data class Listing(
        val sort: PlayerSort = PlayerSort.NAME_A_Z,
        val onlyWithGames: Boolean = false,
    )

    /** Whatever the host is part-way through, kept apart from what the database reports. */
    private data class Editing(
        val isAdding: Boolean = false,
        val newPlayerName: String = "",
        val newPlayerError: UiText? = null,
        val renaming: RenameEditor? = null,
        val deleting: DeleteEditor? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun PlayerStats.toRow(): PlayerRow = PlayerRow(
    playerId = player.id,
    name = player.name,
    gamesPlayed = gamesPlayed,
    buyInCount = buyInCount,
    totalPaidIn = totalPaidIn,
    netProfit = netProfit.takeIf { hasResults },
    openGames = openGames,
    isArchived = player.isArchived,
)
