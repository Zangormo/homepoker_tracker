package com.zango.pokertracker.ui.settlement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.core.time.formatElapsed
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.SettledPayment
import com.zango.pokertracker.domain.settlement.notes
import com.zango.pokertracker.domain.settlement.settle
import com.zango.pokertracker.domain.settlement.shareLines
import com.zango.pokertracker.ui.common.toResultRows
import com.zango.pokertracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Read-only about the game, and the one place the host records that the money actually moved.
 *
 * The settlement itself is derived from the stored buy-ins and chip counts every time it is
 * shown, so reopening a game from history can never disagree with what was displayed on the
 * night. The ticks against those payments are the only thing this screen writes.
 */
@HiltViewModel
class SettlementViewModel @Inject constructor(
    private val repository: PokerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val gameId: Long = requireNotNull(savedStateHandle.get<Long>(Routes.GAME_ID)) {
        "Settlement screen opened without a game id"
    }

    val uiState: StateFlow<SettlementUiState> = combine(
        repository.observeGame(gameId),
        repository.observeSettledPayments(gameId),
        ::buildState,
    )
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettlementUiState(gameId = gameId),
        )

    /**
     * Ticks one payment off, or puts it back.
     *
     * Whether that leaves the game square is worked out here and written with the mark, because
     * this is the only place the full list of payments exists; the game hub reads the conclusion
     * rather than settling every game it lists all over again.
     */
    fun onPaymentToggled(line: PaymentLine) {
        val settled = !line.isPaid
        // The pair identifies the payment: the greedy matching can never produce two transfers
        // between the same two people.
        val allSettled = uiState.value.payments.all { other ->
            val isThisOne = other.fromPlayerId == line.fromPlayerId &&
                other.toPlayerId == line.toPlayerId
            if (isThisOne) settled else other.isPaid
        }
        viewModelScope.launch {
            repository.setPaymentSettled(
                payment = SettledPayment(
                    gameId = gameId,
                    fromPlayerId = line.fromPlayerId,
                    toPlayerId = line.toPlayerId,
                    amount = line.amount,
                ),
                settled = settled,
                allSettled = allSettled,
            )
        }
    }

    private fun buildState(
        snapshot: GameSnapshot?,
        settledPayments: List<SettledPayment>,
    ): SettlementUiState {
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
            payments = settlement.payments.map { payment ->
                PaymentLine(
                    fromPlayerId = payment.from.id,
                    from = payment.from.name,
                    toPlayerId = payment.to.id,
                    to = payment.to.name,
                    amount = payment.amount,
                    // Matched on the amount as well as the pair, so a mark can only ever tick
                    // off the figure it was made against.
                    isPaid = settledPayments.any {
                        it.fromPlayerId == payment.from.id &&
                            it.toPlayerId == payment.to.id &&
                            it.amount == payment.amount
                    },
                )
            },
            notes = settlement.notes(),
            hasProblem = !settlement.isBalanced,
            results = snapshot.toResultRows(),
            buyInCount = snapshot.totalBuyInCount,
            durationLabel = snapshot.game.endedAt
                ?.let { formatElapsed(it - snapshot.game.startedAt) },
            totalOnTable = snapshot.totalOnTable,
            shareLines = settlement.shareLines(snapshot.game.name),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
