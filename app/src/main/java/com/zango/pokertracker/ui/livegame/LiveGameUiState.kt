package com.zango.pokertracker.ui.livegame

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.ui.common.AmountPreview

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
    /** Chips sold back to the bank mid-game. Already paid for, so no longer in play. */
    val returnedChips: Chips = Chips.ZERO,
    val returnedCash: Money = Money.ZERO,
    /** The most recent return, so a mistaken one can be taken back. */
    val lastReturnId: Long? = null,
) {
    val hasReturns: Boolean get() = !returnedChips.isZero
}

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

    /**
     * Selling chips back to the bank without leaving the table, for when the physical chips run
     * out and the next buy-in has to be paid out of somebody's stack.
     */
    data class ReturnChips(
        val seatId: Long,
        val playerName: String,
        val chips: String,
        val chipsOnTable: Chips?,
        val chipCount: Chips? = null,
        val cashValue: Money? = null,
        val error: String? = null,
    ) : LiveGameDialog {
        val canConfirm: Boolean get() = error == null && cashValue != null
    }

    data class CashOut(
        val seatId: Long,
        val playerName: String,
        val totalBuyIn: Money,
        val chips: String,
        /** The typed count, once it parses. Kept so the view never parses anything itself. */
        val chipCount: Chips? = null,
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
        /** A problem with the typed name, kept apart so it shows on the right field. */
        val nameError: String? = null,
    ) : LiveGameDialog {
        val hasSubject: Boolean get() = selectedPlayerId != null || newPlayerName.isNotBlank()
        val canConfirm: Boolean
            get() = error == null && nameError == null && !preview.isEmpty && hasSubject
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
    /** Chips bought back by the bank across the whole table, and what they cost it. */
    val returnedChips: Chips = Chips.ZERO,
    val returnedCash: Money = Money.ZERO,
    /** The game's standard buy-in, used to prefill the rebuy and add-player dialogs. */
    val defaultBuyIn: Money? = null,
    val activeSeats: List<SeatRow> = emptyList(),
    val cashedOutSeats: List<SeatRow> = emptyList(),
    val dialog: LiveGameDialog? = null,
) {
    val playerCount: Int get() = activeSeats.size + cashedOutSeats.size

    val hasReturns: Boolean get() = !returnedChips.isZero

    val canEndGame: Boolean get() = !isFinished && playerCount > 0
}

sealed interface LiveGameEvent {
    data class EndGame(val gameId: Long) : LiveGameEvent
    data class Message(val text: String) : LiveGameEvent
}
