package com.zango.pokertracker.ui.endgame

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.reconcile
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.ui.common.parseChipCount
import com.zango.pokertracker.ui.common.toResultRows
import com.zango.pokertracker.ui.navigation.Routes
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

@HiltViewModel
class EndGameViewModel @Inject constructor(
    private val repository: PokerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val gameId: Long = requireNotNull(savedStateHandle.get<Long>(Routes.GAME_ID)) {
        "End game screen opened without a game id"
    }

    /**
     * What the host has typed, keyed by seat. A seat with no entry here falls back to whatever
     * count is already stored, so counts taken during play show up already filled in.
     */
    private val drafts = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val screen = MutableStateFlow(ScreenState())

    private val eventChannel = Channel<EndGameEvent>(Channel.BUFFERED)
    val events: Flow<EndGameEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<EndGameUiState> =
        combine(repository.observeGame(gameId), drafts, screen, ::buildState)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = EndGameUiState(gameId = gameId),
            )

    /**
     * Persists each count as it is typed rather than only at the end, so a host counting eight
     * stacks does not lose the lot if the app is killed on the seventh.
     */
    fun onCountChange(seatId: Long, text: String) {
        drafts.update { it + (seatId to text) }
        val trimmed = text.trim()
        val parsed = parseChipCount(trimmed, CHIP_COUNT_LABEL, allowZero = true)
        when {
            trimmed.isEmpty() -> persist(seatId, null)
            parsed.chips != null -> persist(seatId, parsed.chips)
            else -> Unit // Leave the stored value alone until the text becomes a real count.
        }
    }

    fun onFinish() {
        val state = uiState.value
        if (!state.canFinish) return
        if (state.reconciliation?.hasDiscrepancy == true) {
            screen.update { it.copy(isConfirmingMismatch = true) }
        } else {
            finish()
        }
    }

    /** Proceeding with a known discrepancy, because the host says the counts are right. */
    fun onConfirmMismatch() {
        screen.update { it.copy(isConfirmingMismatch = false) }
        finish()
    }

    fun onDismissMismatch() = screen.update { it.copy(isConfirmingMismatch = false) }

    private fun finish() {
        if (screen.value.isFinishing) return
        screen.update { it.copy(isFinishing = true) }
        viewModelScope.launch {
            val impliedZeros = uiState.value.seatsCountedAsZero
            runCatching { repository.endGame(gameId, impliedZeros) }
                .onSuccess { eventChannel.send(EndGameEvent.Finished(gameId)) }
                .onFailure {
                    eventChannel.send(
                        EndGameEvent.Message(UiText.of(R.string.error_could_not_end)),
                    )
                }
            screen.update { it.copy(isFinishing = false) }
        }
    }

    private fun persist(seatId: Long, chips: Chips?) {
        viewModelScope.launch {
            runCatching { repository.setFinalChipCount(seatId, chips) }
                .onFailure {
                    eventChannel.send(
                        EndGameEvent.Message(UiText.of(R.string.error_could_not_save_count)),
                    )
                }
        }
    }

    private fun buildState(
        snapshot: GameSnapshot?,
        drafts: Map<Long, String>,
        screen: ScreenState,
    ): EndGameUiState {
        if (snapshot == null) {
            return EndGameUiState(isLoading = false, isMissing = true, gameId = gameId)
        }

        val rate = snapshot.game.chipRate
        val reconciliation = snapshot.reconcile().toSummary()
        val impliedZero = reconciliation.uncountedAreImpliedZero
        val counts = snapshot.seats.map { seat ->
            val text = drafts[seat.id] ?: seat.finalChips?.count?.toString().orEmpty()
            val parsed = parseChipCount(text.trim(), CHIP_COUNT_LABEL, allowZero = true)
            // A blank field is "not counted yet", not an error to shout about.
            val error = if (text.isBlank()) null else parsed.error
            val soldBack = rate.cashFor(seat.returnedChips)
            val cashOut = parsed.chips?.let { rate.cashFor(it) + soldBack }
            CountRow(
                seatId = seat.id,
                name = seat.player.name,
                text = text,
                wasCashedOut = seat.isCashedOut,
                chips = parsed.chips,
                cashOutValue = cashOut,
                totalBuyIn = seat.totalBuyIn,
                returnedChips = seat.returnedChips,
                returnedCash = rate.cashFor(seat.returnedChips),
                net = cashOut?.let { it - seat.totalBuyIn }
                    ?: if (impliedZero) soldBack - seat.totalBuyIn else null,
                error = error,
                countedAsZero = impliedZero && parsed.chips == null,
            )
        }

        // Show the results the host is about to commit, not blanks: an implied zero is what will
        // actually be written, so the table reads the same before and after finishing.
        val results = snapshot.toResultRows().map { row ->
            if (impliedZero && row.chipsOut == null) {
                val seat = snapshot.seats.first { it.id == row.seatId }
                row.copy(
                    chipsOut = seat.returnedChips,
                    cashOut = rate.cashFor(seat.returnedChips),
                    net = rate.cashFor(seat.returnedChips) - row.totalBuyIn,
                )
            } else {
                row
            }
        }

        return EndGameUiState(
            isLoading = false,
            isMissing = false,
            gameId = snapshot.game.id,
            gameName = snapshot.game.name,
            alreadyFinished = !snapshot.game.isInProgress,
            chipValueLabel = UiText.of(R.string.chip_value_label, rate.chipValue.format()),
            counts = counts,
            results = results,
            reconciliation = reconciliation,
            isConfirmingMismatch = screen.isConfirmingMismatch,
            isFinishing = screen.isFinishing,
        )
    }

    private data class ScreenState(
        val isConfirmingMismatch: Boolean = false,
        val isFinishing: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val CHIP_COUNT_LABEL = UiText.of(R.string.label_chip_count)
    }
}
