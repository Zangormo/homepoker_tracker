package com.zango.pokertracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.core.time.formatElapsed
import com.zango.pokertracker.core.time.formatGameDate
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.GameSummary
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
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * The app's home. Games still running are listed first so a host who closed the app mid-game
 * lands straight back on it, with finished games below as the read-only record.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: PokerRepository,
) : ViewModel() {

    private val pendingDeleteId = MutableStateFlow<Long?>(null)

    private val eventChannel = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<HistoryUiState> =
        combine(repository.observeGameSummaries(), pendingDeleteId, ::buildState)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = HistoryUiState(),
            )

    fun onDeleteRequested(gameId: Long) {
        pendingDeleteId.value = gameId
    }

    fun onDismissDelete() {
        pendingDeleteId.value = null
    }

    /**
     * Only ever reached from the confirmation dialog. The game and everything recorded against
     * it go at once, and there is no undo, so nothing here happens on a single tap.
     */
    fun onConfirmDelete() {
        val gameId = pendingDeleteId.value ?: return
        pendingDeleteId.value = null
        viewModelScope.launch {
            runCatching { repository.deleteGame(gameId) }
                .onFailure {
                    eventChannel.send(
                        HistoryEvent.Message(it.message ?: "Could not delete the game"),
                    )
                }
        }
    }

    private fun buildState(summaries: List<GameSummary>, pendingId: Long?): HistoryUiState {
        val rows = summaries.map { it.toRow() }
        return HistoryUiState(
            isLoading = false,
            inProgress = rows.filter { it.isInProgress },
            finished = rows.filter { !it.isInProgress },
            pendingDeletion = rows.firstOrNull { it.gameId == pendingId },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun GameSummary.toRow(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): HistoryRow = HistoryRow(
    gameId = game.id,
    name = game.name,
    dateLabel = formatGameDate(game.startedAt, zone, locale),
    stakes = "${game.smallBlind.format()} / ${game.bigBlind.format()}",
    playerCount = playerCount,
    buyInCount = buyInCount,
    totalOnTable = totalOnTable,
    chipsOnTable = chipsOnTable,
    durationLabel = durationMillis?.let { formatElapsed(it) },
    isInProgress = game.isInProgress,
    isFullyPaid = game.isFullyPaid,
)
