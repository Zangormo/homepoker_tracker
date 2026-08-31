package com.zango.pokertracker.ui.players

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.core.time.formatGameDate
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.PlayerStats
import com.zango.pokertracker.ui.navigation.Routes
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
 * One player's whole record. Read-only, and recomputed from the stored buy-ins and chip counts
 * every time, so a player's lifetime figures and the settlements they came from cannot drift.
 */
@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
    repository: PokerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playerId: Long = requireNotNull(savedStateHandle.get<Long>(Routes.PLAYER_ID)) {
        "Player screen opened without a player id"
    }

    val uiState: StateFlow<PlayerDetailUiState> = repository.observePlayerStats()
        .map { stats -> buildState(stats.firstOrNull { it.player.id == playerId }) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PlayerDetailUiState(),
        )

    private fun buildState(
        stats: PlayerStats?,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): PlayerDetailUiState {
        if (stats == null) return PlayerDetailUiState(isLoading = false, isMissing = true)
        return PlayerDetailUiState(
            isLoading = false,
            isMissing = false,
            name = stats.player.name,
            isArchived = stats.player.isArchived,
            gamesPlayed = stats.gamesPlayed,
            gamesUp = stats.gamesUp,
            buyInCount = stats.buyInCount,
            totalPaidIn = stats.totalPaidIn,
            cashedOut = stats.cashedOut,
            netProfit = stats.netProfit.takeIf { stats.hasResults },
            openGames = stats.openGames,
            games = stats.games.map { game ->
                PlayerGameRow(
                    gameId = game.gameId,
                    gameName = game.gameName,
                    dateLabel = formatGameDate(game.startedAt, zone, locale),
                    isInProgress = game.isInProgress,
                    buyInCount = game.buyInCount,
                    totalBuyIn = game.totalBuyIn,
                    cashOut = game.cashOut,
                    net = game.net,
                )
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
