package com.zango.pokertracker.ui.livegame

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.core.time.Clock
import com.zango.pokertracker.core.time.formatElapsed
import com.zango.pokertracker.core.time.tick
import com.zango.pokertracker.data.repository.CreatePlayerResult
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.Seat
import com.zango.pokertracker.ui.common.AmountPreview
import com.zango.pokertracker.ui.common.parseChipCount
import com.zango.pokertracker.ui.common.parsePositiveMoney
import com.zango.pokertracker.ui.common.wholeChipsError
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
class LiveGameViewModel @Inject constructor(
    private val repository: PokerRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val gameId: Long = requireNotNull(savedStateHandle.get<Long>(Routes.GAME_ID)) {
        "Live game screen opened without a game id"
    }

    private val draft = MutableStateFlow<DialogDraft?>(null)

    private val eventChannel = Channel<LiveGameEvent>(Channel.BUFFERED)
    val events: Flow<LiveGameEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<LiveGameUiState> = combine(
        repository.observeGame(gameId),
        repository.observeRoster(),
        draft,
        clock.tick(),
        ::buildState,
    )
        // The clock ticks every second but the readout only changes by the minute, so identical
        // states are collapsed and the list is not recomposed for nothing.
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LiveGameUiState(gameId = gameId),
        )

    fun onAddBuyIn(seatId: Long) {
        draft.value = DialogDraft.BuyIn(seatId, uiState.value.defaultBuyIn?.format().orEmpty())
    }

    fun onBuyInAmountChange(value: String) = draft.update {
        (it as? DialogDraft.BuyIn)?.copy(amount = value) ?: it
    }

    fun onConfirmBuyIn() {
        val dialog = uiState.value.dialog as? LiveGameDialog.AddBuyIn ?: return
        val amount = dialog.preview.cash?.takeIf { dialog.canConfirm } ?: return
        draft.value = null
        launchWrite("Could not add the buy-in") { repository.addBuyIn(dialog.seatId, amount) }
    }

    fun onReturnChips(seatId: Long) {
        draft.value = DialogDraft.ReturnChips(seatId, chips = "")
    }

    fun onReturnChipsChange(value: String) = draft.update {
        (it as? DialogDraft.ReturnChips)?.copy(chips = value) ?: it
    }

    fun onConfirmReturnChips() {
        val dialog = uiState.value.dialog as? LiveGameDialog.ReturnChips ?: return
        val chips = dialog.chipCount?.takeIf { dialog.canConfirm } ?: return
        draft.value = null
        launchWrite("Could not record the chip return") {
            repository.returnChips(dialog.seatId, chips)
        }
    }

    /** Takes back the last return for a seat, for when the wrong figure went in. */
    fun onUndoLastReturn(seatId: Long) {
        val row = (uiState.value.activeSeats + uiState.value.cashedOutSeats)
            .firstOrNull { it.seatId == seatId } ?: return
        val returnId = row.lastReturnId ?: return
        launchWrite("Could not undo the chip return") { repository.undoChipReturn(returnId) }
    }

    fun onCashOut(seatId: Long) {
        draft.value = DialogDraft.CashOut(seatId, chips = "")
    }

    fun onChipCountChange(value: String) = draft.update {
        (it as? DialogDraft.CashOut)?.copy(chips = value) ?: it
    }

    fun onConfirmCashOut() {
        val dialog = uiState.value.dialog as? LiveGameDialog.CashOut ?: return
        if (!dialog.canConfirm) return
        val chips = parseChipCount(dialog.chips, CHIP_COUNT_LABEL, allowZero = true).chips ?: return
        draft.value = null
        launchWrite("Could not cash the player out") { repository.cashOut(dialog.seatId, chips) }
    }

    /** Puts a player who was cashed out by mistake back into the game, count and all. */
    fun onUndoCashOut(seatId: Long) =
        launchWrite("Could not undo the cash-out") { repository.undoCashOut(seatId) }

    fun onAddPlayer() {
        draft.value = DialogDraft.AddPlayer(
            selectedPlayerId = null,
            newPlayerName = "",
            buyIn = uiState.value.defaultBuyIn?.format().orEmpty(),
        )
    }

    /** Picking someone from the roster and typing a new name are mutually exclusive. */
    fun onSelectCandidate(playerId: Long) = draft.update {
        (it as? DialogDraft.AddPlayer)?.copy(
            selectedPlayerId = if (it.selectedPlayerId == playerId) null else playerId,
            newPlayerName = "",
        ) ?: it
    }

    fun onNewPlayerNameChange(value: String) = draft.update {
        (it as? DialogDraft.AddPlayer)?.copy(newPlayerName = value, selectedPlayerId = null) ?: it
    }

    fun onAddPlayerBuyInChange(value: String) = draft.update {
        (it as? DialogDraft.AddPlayer)?.copy(buyIn = value) ?: it
    }

    fun onConfirmAddPlayer() {
        val dialog = uiState.value.dialog as? LiveGameDialog.AddPlayer ?: return
        if (!dialog.canConfirm) return
        val buyIn = dialog.preview.cash ?: return
        draft.value = null

        viewModelScope.launch {
            val playerId = dialog.selectedPlayerId ?: when (
                val created = repository.createPlayer(dialog.newPlayerName)
            ) {
                is CreatePlayerResult.Created -> created.player.id
                // Someone with that name is already on the roster: seat them rather than refuse.
                is CreatePlayerResult.NameTaken -> created.existing.id
                CreatePlayerResult.BlankName -> {
                    eventChannel.send(LiveGameEvent.Message("Enter a name"))
                    return@launch
                }
            }
            runCatching { repository.seatPlayer(gameId, playerId, buyIn) }
                .onFailure { failure ->
                    eventChannel.send(
                        LiveGameEvent.Message(failure.message ?: "Could not add the player"),
                    )
                }
        }
    }

    fun onDismissDialog() {
        draft.value = null
    }

    fun onEndGame() {
        viewModelScope.launch { eventChannel.send(LiveGameEvent.EndGame(gameId)) }
    }

    private fun launchWrite(failureMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure {
                eventChannel.send(LiveGameEvent.Message(it.message ?: failureMessage))
            }
        }
    }

    private fun buildState(
        snapshot: GameSnapshot?,
        roster: List<Player>,
        draft: DialogDraft?,
        now: Long,
    ): LiveGameUiState {
        if (snapshot == null) {
            return LiveGameUiState(isLoading = false, isMissing = true, gameId = gameId)
        }

        val game = snapshot.game
        val rate = game.chipRate
        val rows = snapshot.seats.map { seat -> seat.toRow(snapshot) }
        // A finished game freezes at the moment it ended rather than counting on forever.
        val until = game.endedAt ?: now

        return LiveGameUiState(
            isLoading = false,
            isMissing = false,
            gameId = game.id,
            gameName = game.name,
            stakes = "${game.smallBlind.format()} / ${game.bigBlind.format()}",
            chipValueLabel = "1 chip = ${rate.chipValue.format()}",
            elapsed = formatElapsed(until - game.startedAt),
            isFinished = !game.isInProgress,
            totalOnTable = AmountPreview.of(snapshot.totalOnTable, rate),
            buyInCount = snapshot.totalBuyInCount,
            returnedChips = snapshot.returnedChips,
            returnedCash = snapshot.returnedCash,
            defaultBuyIn = game.defaultBuyIn,
            activeSeats = rows.filter { !it.isCashedOut },
            cashedOutSeats = rows.filter { it.isCashedOut },
            dialog = draft?.toDialog(snapshot, roster),
        )
    }

    private fun Seat.toRow(snapshot: GameSnapshot): SeatRow = SeatRow(
        seatId = id,
        playerId = player.id,
        name = player.name,
        totalBuyIn = totalBuyIn,
        buyInChips = snapshot.game.chipRate.chipsFor(totalBuyIn).exactOrNull(),
        buyInCount = buyInCount,
        isCashedOut = isCashedOut,
        finalChips = finalChips,
        cashOutValue = snapshot.cashOutValueOf(this),
        net = snapshot.netOf(this),
        returnedChips = returnedChips,
        returnedCash = snapshot.game.chipRate.cashFor(returnedChips),
        lastReturnId = chipReturns.maxByOrNull { it.createdAt }?.id,
    )

    private fun DialogDraft.toDialog(
        snapshot: GameSnapshot,
        roster: List<Player>,
    ): LiveGameDialog? {
        val rate = snapshot.game.chipRate
        return when (this) {
            is DialogDraft.BuyIn -> {
                val seat = snapshot.seats.firstOrNull { it.id == seatId } ?: return null
                val parsed = parsePositiveMoney(amount, BUY_IN_LABEL)
                val chipError = parsed.money?.let { wholeChipsError(it, rate) }
                LiveGameDialog.AddBuyIn(
                    seatId = seatId,
                    playerName = seat.player.name,
                    amount = amount,
                    preview = if (chipError == null) AmountPreview.of(parsed.money, rate) else AmountPreview(),
                    error = parsed.error ?: chipError,
                )
            }

            is DialogDraft.ReturnChips -> {
                val seat = snapshot.seats.firstOrNull { it.id == seatId } ?: return null
                val onTable = snapshot.chipsOnTable.exactOrNull()
                val parsed = parseChipCount(chips, RETURN_LABEL, allowZero = false)
                // A player cannot hand back chips the table does not hold. Their own stack is
                // unknown -- winnings are never recorded -- so the table total is the only
                // bound that can honestly be enforced.
                val tooMany = parsed.chips != null && onTable != null && parsed.chips > onTable
                LiveGameDialog.ReturnChips(
                    seatId = seatId,
                    playerName = seat.player.name,
                    chips = chips,
                    chipsOnTable = onTable,
                    chipCount = parsed.chips?.takeIf { !tooMany },
                    cashValue = parsed.chips?.takeIf { !tooMany }?.let { rate.cashFor(it) },
                    error = parsed.error
                        ?: "Only $onTable chips are on the table".takeIf { tooMany },
                )
            }

            is DialogDraft.CashOut -> {
                val seat = snapshot.seats.firstOrNull { it.id == seatId } ?: return null
                val parsed = parseChipCount(chips, CHIP_COUNT_LABEL, allowZero = true)
                val cash = parsed.chips?.let { rate.cashFor(it) }
                LiveGameDialog.CashOut(
                    seatId = seatId,
                    playerName = seat.player.name,
                    totalBuyIn = seat.totalBuyIn,
                    chips = chips,
                    chipCount = parsed.chips,
                    cashValue = cash,
                    net = cash?.let { it - seat.totalBuyIn },
                    error = parsed.error,
                )
            }

            is DialogDraft.AddPlayer -> {
                val seated = snapshot.seats.map { it.player.id }.toSet()
                val parsed = parsePositiveMoney(buyIn, BUY_IN_LABEL)
                val chipError = parsed.money?.let { wholeChipsError(it, rate) }
                LiveGameDialog.AddPlayer(
                    candidates = roster.filter { it.id !in seated },
                    selectedPlayerId = selectedPlayerId,
                    newPlayerName = newPlayerName,
                    buyIn = buyIn,
                    preview = if (chipError == null) AmountPreview.of(parsed.money, rate) else AmountPreview(),
                    error = parsed.error ?: chipError,
                )
            }
        }
    }

    /** What the host has typed into the open dialog, before it is interpreted. */
    private sealed interface DialogDraft {
        data class BuyIn(val seatId: Long, val amount: String) : DialogDraft
        data class CashOut(val seatId: Long, val chips: String) : DialogDraft
        data class ReturnChips(val seatId: Long, val chips: String) : DialogDraft
        data class AddPlayer(
            val selectedPlayerId: Long?,
            val newPlayerName: String,
            val buyIn: String,
        ) : DialogDraft
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val BUY_IN_LABEL = "Buy-in"
        const val CHIP_COUNT_LABEL = "Chip count"
        const val RETURN_LABEL = "Chips returned"
    }
}
