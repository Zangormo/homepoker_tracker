package com.homepoker_tracker.ui.livegame

import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.domain.model.Player
import com.homepoker_tracker.ui.common.AmountPreview

/** One player's line in the live list. */
data class SeatRow(
    val seatId: Long,
    val playerId: Long,
    val name: String,
    val totalBuyIn: Money,
    val buyInChips: Chips?,
    val buyInCount: Int,
    val isCashedOut: Boolean,
    val finalChips: Chips? = null,
    val cashOutValue: Money? = null,
    val net: Money? = null,
)

/**
 * Only one of these is ever open. Each carries its own draft text plus the live preview and
 * error, so the dialog is a pure rendering of state the ViewModel already validated.
 */
sealed interface LiveGameDialog {

    data class AddBuyIn(
        val seatId: Long,
        val playerName: String,
        val amount: String,
        val preview: AmountPreview = AmountPreview(),
        val error: String? = null,
    ) : LiveGameDialog {
        val canConfirm: Boolean get() = error == null && !preview.isEmpty
    }

    data class CashOut(
        val seatId: Long,
        val playerName: String,
        val totalBuyIn: Money,
        val chips: String,
        val cashValue: Money? = null,
        val net: Money? = null,
        val error: String? = null,
    ) : LiveGameDialog {
        val canConfirm: Boolean get() = error == null && cashValue != null
    }

    data class AddPlayer(
        val candidates: List<Player>,
        val selectedPlayerId: Long?,
        val newPlayerName: String,
        val buyIn: String,
        val preview: AmountPreview = AmountPreview(),
        val error: String? = null,
    ) : LiveGameDialog {
        val hasSubject: Boolean get() = selectedPlayerId != null || newPlayerName.isNotBlank()
        val canConfirm: Boolean get() = error == null && !preview.isEmpty && hasSubject
    }
}

data class LiveGameUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val gameId: Long = 0,
    val gameName: String = "",
    val stakes: String = "",
    val chipValueLabel: String = "",
    /** "2h 47m", recomputed from the stored start time rather than counted. */
    val elapsed: String = "",
    val isFinished: Boolean = false,
    val totalOnTable: AmountPreview = AmountPreview(),
    val buyInCount: Int = 0,
    /** The game's standard buy-in, used to prefill the rebuy and add-player dialogs. */
    val defaultBuyIn: Money? = null,
    val activeSeats: List<SeatRow> = emptyList(),
    val cashedOutSeats: List<SeatRow> = emptyList(),
    val dialog: LiveGameDialog? = null,
) {
    val playerCount: Int get() = activeSeats.size + cashedOutSeats.size

    val canEndGame: Boolean get() = !isFinished && playerCount > 0
}

sealed interface LiveGameEvent {
    data class EndGame(val gameId: Long) : LiveGameEvent
    data class Message(val text: String) : LiveGameEvent
}
