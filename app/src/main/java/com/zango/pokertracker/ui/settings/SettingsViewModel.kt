package com.zango.pokertracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.data.repository.AddStakesResult
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.Stakes
import com.zango.pokertracker.ui.common.parsePositiveMoney
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
 * Settings. So far that means the stake levels the game picker offers.
 *
 * The list fills itself as games are played, which is convenient right up until a one-off night
 * at odd blinds is stuck in the picker for good. This is where the host prunes it, and where a
 * level can be added for a game not played yet.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PokerRepository,
) : ViewModel() {

    private val editing = MutableStateFlow<StakesEditor?>(null)

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(repository.observeStakeOptions(), editing) { stakes, editor ->
            SettingsUiState(
                isLoading = false,
                stakes = stakes.map { StakeRow(it, it.label()) },
                editor = editor,
            )
        }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SettingsUiState(),
            )

    fun onAddRequested() {
        if (uiState.value.isFull) {
            viewModelScope.launch {
                eventChannel.send(
                    SettingsEvent.Message(
                        "That is all ${Stakes.MAX_PRESETS} levels. Remove one to make room.",
                    ),
                )
            }
            return
        }
        editing.value = StakesEditor()
    }

    fun onSmallBlindChange(value: String) =
        editing.update { it?.copy(smallBlind = value, error = null) }

    fun onBigBlindChange(value: String) =
        editing.update { it?.copy(bigBlind = value, error = null) }

    fun onDismissAdd() {
        editing.value = null
    }

    /**
     * Validated here in the same terms the new-game form uses, so "0.05/0.02" is refused with the
     * same sentence wherever the host happens to type it.
     */
    fun onConfirmAdd() {
        val editor = editing.value ?: return
        val small = parsePositiveMoney(editor.smallBlind, "Small blind")
        val big = parsePositiveMoney(editor.bigBlind, "Big blind")
        val error = small.error
            ?: big.error
            ?: "Big blind must be larger than the small blind"
                .takeIf { small.money != null && big.money != null && big.money <= small.money }
        if (error != null || small.money == null || big.money == null) {
            editing.update { it?.copy(error = error) }
            return
        }

        val stakes = Stakes(small.money, big.money)
        viewModelScope.launch {
            when (repository.addStakes(stakes)) {
                AddStakesResult.Added -> {
                    editing.value = null
                    eventChannel.send(SettingsEvent.Message("${stakes.label()} added"))
                }

                AddStakesResult.AlreadyListed ->
                    editing.update { it?.copy(error = "${stakes.label()} is already on the list") }

                AddStakesResult.ListFull -> editing.update {
                    it?.copy(error = "That is all ${Stakes.MAX_PRESETS} levels. Remove one first.")
                }
            }
        }
    }

    fun onRemove(stakes: Stakes) {
        viewModelScope.launch {
            repository.removeStakes(stakes)
            eventChannel.send(SettingsEvent.Removed(stakes, "${stakes.label()} removed"))
        }
    }

    /** Puts back a level taken off by mistake, straight from the snackbar. */
    fun onUndoRemove(stakes: Stakes) {
        viewModelScope.launch {
            if (repository.addStakes(stakes) == AddStakesResult.ListFull) {
                eventChannel.send(
                    SettingsEvent.Message("The list filled up, so ${stakes.label()} stayed off."),
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
