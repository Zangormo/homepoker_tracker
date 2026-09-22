package com.zango.pokertracker.ui.livegame

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.core.time.Clock
import com.zango.pokertracker.core.time.formatElapsed
import com.zango.pokertracker.core.time.tick
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.data.repository.CreatePlayerResult
import com.zango.pokertracker.data.local.FiretruckStore
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.NameRules
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.Seat
import com.zango.pokertracker.domain.model.bombPotSchedule
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveGameViewModel @Inject constructor(
    private val repository: PokerRepository,
    private val clock: Clock,
    private val firetruckStore: FiretruckStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val gameId: Long = requireNotNull(savedStateHandle.get<Long>(Routes.GAME_ID)) {
        "Live game screen opened without a game id"
    }

    private val draft = MutableStateFlow<DialogDraft?>(null)

    /** The player picked up on the table view. */
    private val tableSelection = MutableStateFlow<Long?>(null)

    // Stored rather than kept in memory, so the dots survive the app being swiped away mid-game.
    // A run that had already made a firetruck was announced before the app went away, so it
    // comes back cleared rather than as three dots with nothing left to do.
    private val streak = MutableStateFlow(
        firetruckStore.load(gameId).takeUnless { it.isFiretruck } ?: FiretruckStreak(),
    )

    private val _announcement = MutableStateFlow<LiveAnnouncement?>(null)

    /** The bomb pot or firetruck taking over the screen, until it is dismissed. */
    val announcement: StateFlow<LiveAnnouncement?> = _announcement.asStateFlow()

    /**
     * How many bomb pots had come round when the timer was last looked at. Null until the first
     * reading after the screen is shown, so one that passed while the host was elsewhere -- and
     * was notified about -- is not announced again the moment they come back.
     */
    private var bombPotsSeen: Long? = null

    private val eventChannel = Channel<LiveGameEvent>(Channel.BUFFERED)
    val events: Flow<LiveGameEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<LiveGameUiState> = combine(
        repository.observeGame(gameId),
        repository.observeRoster(),
        draft,
        tableSelection,
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

    /** The game tab: the bomb pot countdown and the firetruck count. Ticks every second. */
    val gameTab: StateFlow<GameTabUiState> = combine(
        repository.observeGame(gameId),
        streak,
        clock.tick(),
    ) { snapshot, streak, now ->
        watchBombPot(snapshot, now)
        buildGameTab(snapshot, streak, now)
    }
        .onStart { bombPotsSeen = null }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GameTabUiState(),
        )

    /**
     * A tap on a player at the table. The first picks them up, a second on the same player puts
     * them down, and a second on anyone else swaps the two.
     */
    fun onTableSeatTap(seatId: Long) {
        if (!uiState.value.canRearrangeTable) return
        val selected = tableSelection.value
        when (selected) {
            null -> tableSelection.value = seatId
            seatId -> tableSelection.value = null
            else -> {
                tableSelection.value = null
                launchWrite(UiText.of(R.string.error_swap_seats)) {
                    repository.swapTablePositions(selected, seatId)
                }
            }
        }
    }

    /** A tap anywhere that is not a player: whoever was picked up is put back down. */
    fun onClearTableSelection() {
        tableSelection.value = null
    }

    /**
     * One more win for the player at [seatId]. Anyone else winning wipes the previous run, and
     * the third in a row is a firetruck.
     */
    fun onFiretruckTap(seatId: Long) {
        if (_announcement.value != null) return
        val next = streak.value.tap(seatId)
        setStreak(next)
        if (next.isFiretruck) {
            val name = gameTab.value.firetruckRows?.firstOrNull { it.seatId == seatId }?.name
            _announcement.value = LiveAnnouncement.Firetruck(name.orEmpty())
        }
    }

    /** Closes the announcement. A firetruck has been called, so the dots start again. */
    fun onDismissAnnouncement() {
        if (_announcement.value is LiveAnnouncement.Firetruck) setStreak(FiretruckStreak())
        _announcement.value = null
    }

    private fun setStreak(value: FiretruckStreak) {
        streak.value = value
        firetruckStore.save(gameId, value)
    }

    private fun watchBombPot(snapshot: GameSnapshot?, now: Long) {
        val game = snapshot?.game
        val count = game?.takeIf { it.isInProgress }?.bombPotSchedule()?.countAt(now)
        val seen = bombPotsSeen
        bombPotsSeen = count
        if (count != null && seen != null && count > seen) {
            _announcement.value = LiveAnnouncement.BombPot
        }
    }

    private fun buildGameTab(
        snapshot: GameSnapshot?,
        streak: FiretruckStreak,
        now: Long,
    ): GameTabUiState {
        val game = snapshot?.game ?: return GameTabUiState()
        val bombPot = game.bombPotSchedule()?.let { schedule ->
            // A finished game shows the timer as it stood when the game ended.
            val at = if (game.isInProgress) now else game.endedAt ?: now
            val remaining = schedule.remainingAt(at)
            val interval = schedule.intervalMinutes * MILLIS_PER_MINUTE
            BombPotUi(
                intervalMinutes = schedule.intervalMinutes,
                remaining = formatCountdown(remaining),
                progress = (1f - remaining.toFloat() / interval).coerceIn(0f, 1f),
                isRunning = game.isInProgress,
            )
        }
        val firetruckRows = if (game.isFiretruckGame) {
            snapshot.seats.filter { !it.isCashedOut }
                .map { FiretruckRow(it.id, it.player.name, streak.winsFor(it.id)) }
        } else {
            null
        }
        return GameTabUiState(
            bombPot = bombPot,
            firetruckRows = firetruckRows,
            canTapFiretruck = game.isInProgress,
        )
    }

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
        launchWrite(UiText.of(R.string.error_record_buy_in)) { repository.addBuyIn(dialog.seatId, amount) }
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
        launchWrite(UiText.of(R.string.error_record_return)) {
            repository.returnChips(dialog.seatId, chips)
        }
    }

    /** Takes back the last return for a seat, for when the wrong figure went in. */
    fun onUndoLastReturn(seatId: Long) {
        val row = (uiState.value.activeSeats + uiState.value.cashedOutSeats)
            .firstOrNull { it.seatId == seatId } ?: return
        val returnId = row.lastReturnId ?: return
        launchWrite(UiText.of(R.string.error_undo_return)) { repository.undoChipReturn(returnId) }
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
        launchWrite(UiText.of(R.string.error_record_cash_out)) { repository.cashOut(dialog.seatId, chips) }
    }

    /** Puts a player who was cashed out by mistake back into the game, count and all. */
    fun onUndoCashOut(seatId: Long) =
        launchWrite(UiText.of(R.string.error_undo_cash_out)) { repository.undoCashOut(seatId) }

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
                    eventChannel.send(LiveGameEvent.Message(UiText.of(R.string.error_name_required)))
                    return@launch
                }

                CreatePlayerResult.NameTooLong -> {
                    eventChannel.send(
                        LiveGameEvent.Message(
                            NameRules.playerNameTooLongMessage(),
                        ),
                    )
                    return@launch
                }
            }
            runCatching { repository.seatPlayer(gameId, playerId, buyIn) }
                .onFailure { failure ->
                    eventChannel.send(
                        LiveGameEvent.Message(UiText.of(R.string.error_add_player)),
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

    /**
     * Runs a write and names what went wrong if it fails.
     *
     * The exception's own message is deliberately not shown: it comes from Room or from a
     * `check` in the repository, and is developer English that no translation covers.
     */
    private fun launchWrite(failureMessage: UiText, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { eventChannel.send(LiveGameEvent.Message(failureMessage)) }
        }
    }

    private fun buildState(
        snapshot: GameSnapshot?,
        roster: List<Player>,
        draft: DialogDraft?,
        tableSelection: Long?,
        now: Long,
    ): LiveGameUiState {
        if (snapshot == null) {
            return LiveGameUiState(isLoading = false, isMissing = true, gameId = gameId)
        }

        val game = snapshot.game
        val rate = game.chipRate
        val rows = snapshot.seats.map { seat -> seat.toRow(snapshot) }
        // Only people still playing have a chair. Anyone without a place (seated before the game
        // was set to random, which cannot happen today) goes last rather than disappearing.
        val table = if (game.isRandomSeating) {
            snapshot.activeSeats
                .sortedWith(compareBy<Seat>({ it.tablePosition ?: Int.MAX_VALUE }, { it.joinedAt }))
                .map { TableSeat(it.id, it.player.name) }
        } else {
            null
        }
        // A finished game freezes at the moment it ended rather than counting on forever.
        val until = game.endedAt ?: now

        return LiveGameUiState(
            isLoading = false,
            isMissing = false,
            gameId = game.id,
            gameName = game.name,
            stakes = "${game.smallBlind.format()} / ${game.bigBlind.format()}",
            chipValueLabel = UiText.of(R.string.chip_value_label, UiText.cash(rate.chipValue)),
            elapsed = formatElapsed(until - game.startedAt),
            isFinished = !game.isInProgress,
            hasSideGames = game.bombPotIntervalMinutes != null || game.isFiretruckGame,
            table = table,
            // A picked-up player who has since cashed out is no longer at the table to swap.
            selectedTableSeatId = tableSelection?.takeIf { picked ->
                table?.any { it.seatId == picked } == true
            },
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
                        ?: UiText.plural(
                            R.plurals.error_more_than_on_table,
                            onTable?.count?.toInt() ?: 0,
                            onTable?.count ?: 0,
                        ).takeIf { tooMany },
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
                    nameError = NameRules.playerNameTooLongMessage()
                        .takeIf { NameRules.isPlayerNameTooLong(newPlayerName) },
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
        const val MILLIS_PER_MINUTE = 60_000L
        val BUY_IN_LABEL = UiText.of(R.string.label_buy_in)
        val CHIP_COUNT_LABEL = UiText.of(R.string.label_chip_count)
        val RETURN_LABEL = UiText.of(R.string.live_return_field)
    }
}
