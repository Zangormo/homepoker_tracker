package com.zango.pokertracker.ui.creategame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.money.MoneyParser
import com.zango.pokertracker.core.money.sum
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.data.repository.CreatePlayerResult
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.NameRules
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.Stakes
import com.zango.pokertracker.ui.common.AmountPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateGameViewModel @Inject constructor(
    private val repository: PokerRepository,
) : ViewModel() {

    private val form = MutableStateFlow(CreateGameForm())
    private val editing = MutableStateFlow(EditingState())

    private val eventChannel = Channel<CreateGameEvent>(Channel.BUFFERED)
    val events: Flow<CreateGameEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<CreateGameUiState> =
        combine(
            form,
            editing,
            repository.observeRoster(),
            repository.observeStakeOptions(),
            ::buildState,
        )
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CreateGameUiState(),
            )

    init {
        // Blinds almost never change between one night and the next, so the form opens on the
        // stakes last played. Only ever written into fields the host has not touched, because
        // the read is asynchronous and must not overwrite anything typed while it was in flight.
        viewModelScope.launch {
            val last = repository.lastPlayedStakes() ?: return@launch
            form.update { current ->
                if (current.smallBlind.isBlank() && current.bigBlind.isBlank()) {
                    current.copy(
                        smallBlind = last.smallBlind.format(),
                        bigBlind = last.bigBlind.format(),
                    )
                } else {
                    current
                }
            }
        }
    }

    fun onNameChange(value: String) = form.update { it.copy(name = value) }

    fun onSmallBlindChange(value: String) = form.update { it.copy(smallBlind = value) }

    fun onBigBlindChange(value: String) = form.update { it.copy(bigBlind = value) }

    /** Fills both blind fields from one pick, because a stake level is one decision, not two. */
    fun onStakesSelected(option: StakeOption) = form.update {
        it.copy(
            smallBlind = option.stakes.smallBlind.format(),
            bigBlind = option.stakes.bigBlind.format(),
        )
    }

    fun onChipValueChange(value: String) = form.update { it.copy(chipValue = value) }


    fun onChipsPerBigBlindChange(value: String) = form.update { it.copy(chipsPerBigBlind = value) }

    fun onDeriveChipValueChange(derive: Boolean) = form.update { it.copy(deriveChipValue = derive) }

    fun onBuyInModeChange(mode: BuyInMode) = form.update { it.copy(buyInMode = mode) }

    fun onBuyInBigBlindsChange(value: String) = form.update { it.copy(buyInBigBlinds = value) }

    fun onBuyInCashChange(value: String) = form.update { it.copy(buyInCash = value) }

    fun onPayoutRoundingChange(value: String) = form.update { it.copy(payoutRounding = value) }

    /**
     * Tapping a player toggles them in or out. Deselecting drops any override they had, so a
     * player removed by mistake and re-added comes back on the standard buy-in rather than
     * silently keeping a figure the host can no longer see.
     */
    fun onTogglePlayer(playerId: Long) = form.update { current ->
        val selection = LinkedHashMap(current.selection)
        if (selection.containsKey(playerId)) selection.remove(playerId) else selection[playerId] = null
        current.copy(selection = selection)
    }

    fun onNewPlayerNameChange(value: String) =
        editing.update { it.copy(newPlayerName = value, newPlayerError = null) }

    /** Creates a roster player and selects them straight away, as the host expects. */
    fun onAddNewPlayer() {
        val name = editing.value.newPlayerName.trim()
        viewModelScope.launch {
            when (val result = repository.createPlayer(name)) {
                is CreatePlayerResult.Created -> {
                    selectPlayer(result.player.id)
                    editing.update { it.copy(newPlayerName = "", newPlayerError = null) }
                }

                is CreatePlayerResult.NameTaken -> {
                    // Already on the roster, so just select them rather than blocking the host.
                    selectPlayer(result.existing.id)
                    editing.update { it.copy(newPlayerName = "", newPlayerError = null) }
                    eventChannel.send(
                        CreateGameEvent.Message(
                            UiText.of(R.string.error_name_taken, result.existing.name),
                        ),
                    )
                }

                CreatePlayerResult.BlankName ->
                    editing.update {
                        it.copy(newPlayerError = UiText.of(R.string.error_name_required))
                    }

                CreatePlayerResult.NameTooLong -> editing.update {
                    it.copy(
                        newPlayerError = NameRules.playerNameTooLongMessage(),
                    )
                }
            }
        }
    }

    fun onEditOverride(playerId: Long) {
        val state = uiState.value
        val row = state.roster.firstOrNull { it.player.id == playerId } ?: return
        val bigBlind = state.validation.bigBlind
        val current = state.form.selection[playerId]
        // Reopen the dialog in the same terms the amount was set in: a round multiple of the big
        // blind reads back as big blinds, anything else as the cash figure the host typed.
        val asBigBlinds = current.inBigBlinds(bigBlind)

        editing.update {
            it.copy(
                editor = EditorDraft(
                    playerId = playerId,
                    playerName = row.player.name,
                    mode = if (current == null || asBigBlinds != null) {
                        BuyInMode.BIG_BLINDS
                    } else {
                        BuyInMode.CASH
                    },
                    bigBlinds = (asBigBlinds ?: state.validation.defaultBuyIn.inBigBlinds(bigBlind))
                        ?.toString().orEmpty(),
                    cash = (current ?: state.validation.defaultBuyIn)?.format().orEmpty(),
                ),
            )
        }
    }

    fun onOverrideModeChange(mode: BuyInMode) =
        editing.update { it.copy(editor = it.editor?.copy(mode = mode)) }

    fun onOverrideBigBlindsChange(value: String) =
        editing.update { it.copy(editor = it.editor?.copy(bigBlinds = value)) }

    fun onOverrideCashChange(value: String) =
        editing.update { it.copy(editor = it.editor?.copy(cash = value)) }

    fun onDismissOverride() = editing.update { it.copy(editor = null) }

    fun onApplyOverride() {
        val editor = uiState.value.overrideEditor ?: return
        if (!editor.canApply) return
        val amount = editor.preview.cash ?: return
        form.update { current ->
            current.copy(selection = LinkedHashMap(current.selection).apply { put(editor.playerId, amount) })
        }
        onDismissOverride()
    }

    /** Puts a player back on the game default. */
    fun onClearOverride(playerId: Long) {
        form.update { current ->
            current.copy(selection = LinkedHashMap(current.selection).apply { put(playerId, null) })
        }
        onDismissOverride()
    }

    fun onStartGame() {
        val setup = uiState.value.validation.setup ?: return
        if (editing.value.isStarting) return
        editing.update { it.copy(isStarting = true) }
        viewModelScope.launch {
            val result = runCatching { repository.createGame(setup) }
            editing.update { it.copy(isStarting = false) }
            result
                .onSuccess { eventChannel.send(CreateGameEvent.GameStarted(it)) }
                .onFailure {
                    eventChannel.send(
                        CreateGameEvent.Message(UiText.of(R.string.error_could_not_start)),
                    )
                }
        }
    }

    private fun selectPlayer(playerId: Long) = form.update { current ->
        if (current.selection.containsKey(playerId)) {
            current
        } else {
            current.copy(selection = LinkedHashMap(current.selection).apply { put(playerId, null) })
        }
    }

    private fun buildState(
        form: CreateGameForm,
        editing: EditingState,
        roster: List<Player>,
        stakes: List<Stakes>,
    ): CreateGameUiState {
        val validation = form.validate()
        val rate = validation.chipRate
        val defaultBuyIn = validation.defaultBuyIn

        val rows = roster.map { player ->
            val isSelected = form.selection.containsKey(player.id)
            val override = form.selection[player.id]
            val buyIn = if (isSelected) override ?: defaultBuyIn else null
            RosterRow(
                player = player,
                isSelected = isSelected,
                buyIn = buyIn,
                chips = buyIn?.let { rate?.chipsFor(it)?.exactOrNull() },
                isOverridden = isSelected && override != null,
                error = validation.overrideErrors[player.id],
            )
        }

        val total = rows.filter { it.isSelected }.mapNotNull { it.buyIn }
            .takeIf { it.size == form.selection.size && it.isNotEmpty() }
            ?.sum()

        val stakeOptions = stakes.map { StakeOption(it.label(), it) }
        val typed = validation.smallBlind?.let { small ->
            validation.bigBlind?.let { Stakes(small, it) }
        }

        return CreateGameUiState(
            form = form,
            validation = validation,
            stakeOptions = stakeOptions,
            selectedStake = stakeOptions.firstOrNull { it.stakes == typed },
            roster = rows,
            newPlayerName = editing.newPlayerName,
            newPlayerError = editing.newPlayerError,
            derivedChipValue = if (form.deriveChipValue) rate?.chipValue else null,
            defaultBuyInPreview = AmountPreview.of(defaultBuyIn, rate),
            defaultBuyInBigBlinds = defaultBuyIn.inBigBlinds(validation.bigBlind),
            totalOnTable = AmountPreview.of(total, rate),
            overrideEditor = editing.editor?.toUiModel(validation),
            isStarting = editing.isStarting,
        )
    }

    /** State that belongs to the screen rather than to the game being described. */
    private data class EditingState(
        val newPlayerName: String = "",
        val newPlayerError: UiText? = null,
        val editor: EditorDraft? = null,
        val isStarting: Boolean = false,
    )

    private data class EditorDraft(
        val playerId: Long,
        val playerName: String,
        val mode: BuyInMode,
        val bigBlinds: String,
        val cash: String,
    )

    private fun EditorDraft.toUiModel(validation: CreateGameValidation): OverrideEditor {
        val bigBlind = validation.bigBlind
        val rate = validation.chipRate
        val (amount, error: UiText?) = when (mode) {
            BuyInMode.CASH -> when (val parsed = MoneyParser.parseOrNull(cash)) {
                null -> null to UiText.of(R.string.error_enter_amount_like)
                else -> if (parsed.isPositive) {
                    parsed to null
                } else {
                    null to UiText.of(R.string.error_must_be_positive)
                }
            }

            BuyInMode.BIG_BLINDS -> {
                val multiple = bigBlinds.trim().toLongOrNull()
                when {
                    bigBlind == null -> null to UiText.of(R.string.error_blinds_first)
                    multiple == null -> null to UiText.of(R.string.error_whole_big_blinds)
                    multiple <= 0 -> null to UiText.of(R.string.error_must_be_positive)
                    else -> runCatching { bigBlind * multiple }.getOrNull() to null
                }
            }
        }
        val chipError = amount?.let { value ->
            rate?.chipsFor(value)?.let { conversion ->
                if (conversion.exactOrNull() == null) {
                    UiText.of(R.string.error_not_whole_chips_short)
                } else {
                    null
                }
            }
        }
        return OverrideEditor(
            playerId = playerId,
            playerName = playerName,
            mode = mode,
            bigBlinds = bigBlinds,
            cash = cash,
            preview = if (chipError == null) AmountPreview.of(amount, rate) else AmountPreview(),
            error = error ?: chipError,
        )
    }

    /** The amount as a whole multiple of the big blind, or null when it is not one. */
    private fun Money?.inBigBlinds(bigBlind: Money?): Long? {
        if (this == null || bigBlind == null || !bigBlind.isPositive) return null
        return if (micros % bigBlind.micros == 0L) micros / bigBlind.micros else null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
