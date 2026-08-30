package com.zango.pokertracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.core.time.formatElapsed
import com.zango.pokertracker.core.time.formatGameDate
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.GameSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * The app's home. Games still running are listed first so a host who closed the app mid-game
 * lands straight back on it, with finished games below as the read-only record.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: PokerRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = repository.observeGameSummaries()
        .map(::buildState)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HistoryUiState(),
        )

    private fun buildState(summaries: List<GameSummary>): HistoryUiState {
        val rows = summaries.map { it.toRow() }
        return HistoryUiState(
            isLoading = false,
            inProgress = rows.filter { it.isInProgress },
            finished = rows.filter { !it.isInProgress },
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
)
