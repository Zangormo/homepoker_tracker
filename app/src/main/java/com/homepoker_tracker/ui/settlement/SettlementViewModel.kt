package com.homepoker_tracker.ui.settlement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepoker_tracker.core.money.sum
import com.homepoker_tracker.data.repository.PokerRepository
import com.homepoker_tracker.domain.model.GameSnapshot
import com.homepoker_tracker.domain.settlement.notes
import com.homepoker_tracker.domain.settlement.settle
import com.homepoker_tracker.domain.settlement.toShareText
import com.homepoker_tracker.ui.common.toResultRows
import com.homepoker_tracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Read-only by design. The settlement is derived from the stored buy-ins and chip counts every
 * time it is shown, so reopening a game from history can never disagree with what was displayed
 * on the night.
 */
@HiltViewModel
class SettlementViewModel @Inject constructor(
    repository: PokerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val gameId: Long = requireNotNull(savedStateHandle.get<Long>(Routes.GAME_ID)) {
        "Settlement screen opened without a game id"
    }

    val uiState: StateFlow<SettlementUiState> = repository.observeGame(gameId)
        .map(::buildState)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettlementUiState(gameId = gameId),
        )

    private fun buildState(snapshot: GameSnapshot?): SettlementUiState {
        if (snapshot == null) {
            return SettlementUiState(isLoading = false, isMissing = true, gameId = gameId)
        }
        val settlement = snapshot.settle()
        return SettlementUiState(
            isLoading = false,
            isMissing = false,
            gameId = snapshot.game.id,
            gameName = snapshot.game.name,
            isFinished = !snapshot.game.isInProgress,
            payments = settlement.payments.map {
                PaymentLine(from = it.from.name, to = it.to.name, amount = it.amount)
            },
            notes = settlement.notes(),
            hasProblem = !settlement.isBalanced,
            results = snapshot.toResultRows(),
            totalMoved = settlement.payments.map { it.amount }.sum(),
            shareText = settlement.toShareText(snapshot.game.name),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
