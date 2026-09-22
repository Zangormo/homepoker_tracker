package com.zango.pokertracker.ui.handoff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.transfer.GameTransferCodec
import com.zango.pokertracker.domain.transfer.toTransfer
import com.zango.pokertracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HandOffUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val gameName: String = "",
    /** The whole game as text, for the QR code and for the file. */
    val payload: String? = null,
) {
    /** A night long enough can outgrow one QR code; it still travels as a file. */
    val fitsInQrCode: Boolean
        get() = payload != null && payload.length <= GameTransferCodec.MAX_QR_LENGTH
}

sealed interface HandOffEvent {
    /** The game is gone from this phone; nothing is left to show here. */
    data object Removed : HandOffEvent
    data class Message(val text: UiText) : HandOffEvent
}

/**
 * Hands a running game to another host's phone.
 *
 * The code follows the game as stored, so it always carries the latest buy-in. Nothing tells this
 * phone that the other one has scanned it, so it is the host who says when it is done, and chooses
 * then whether to keep a copy.
 */
@HiltViewModel
class HandOffViewModel @Inject constructor(
    private val repository: PokerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val gameId: Long = requireNotNull(savedStateHandle.get<Long>(Routes.GAME_ID)) {
        "Hand-over screen opened without a game id"
    }

    private val eventChannel = Channel<HandOffEvent>(Channel.BUFFERED)
    val events: Flow<HandOffEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<HandOffUiState> = repository.observeGame(gameId)
        .map { snapshot ->
            if (snapshot == null) {
                HandOffUiState(isLoading = false, isMissing = true)
            } else {
                HandOffUiState(
                    isLoading = false,
                    gameName = snapshot.game.name,
                    payload = GameTransferCodec.encode(snapshot.toTransfer()),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HandOffUiState(),
        )

    /** The other phone has it: this one lets go, so the night is only recorded in one place. */
    fun onRemoveFromThisPhone() {
        viewModelScope.launch {
            runCatching { repository.deleteGame(gameId) }
                .onSuccess { eventChannel.send(HandOffEvent.Removed) }
                .onFailure {
                    eventChannel.send(HandOffEvent.Message(UiText.of(R.string.error_could_not_delete_game)))
                }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
