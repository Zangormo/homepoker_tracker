package com.zango.pokertracker.ui.creategame

import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.Stakes
import com.zango.pokertracker.ui.common.AmountPreview

/** One roster player as the picker sees them. */
data class RosterRow(
    val player: Player,
    val isSelected: Boolean,
    /** What this player will be in for: their override, or the game default. */
    val buyIn: Money? = null,
    val chips: Chips? = null,
    val isOverridden: Boolean = false,
    val error: UiText? = null,
)

/** The state of the "give this player a different buy-in" dialog. */
data class OverrideEditor(
    val playerId: Long,
    val playerName: String,
    val mode: BuyInMode,
    val bigBlinds: String,
    val cash: String,
    val preview: AmountPreview = AmountPreview(),
    val error: UiText? = null,
) {
    val canApply: Boolean get() = error == null && !preview.isEmpty
}

/** One stake level in the picker under the blind fields. */
data class StakeOption(val label: String, val stakes: Stakes)

data class CreateGameUiState(
    val form: CreateGameForm = CreateGameForm(),
    val validation: CreateGameValidation = CreateGameValidation(),
    val roster: List<RosterRow> = emptyList(),
    val newPlayerName: String = "",
    val newPlayerError: UiText? = null,
    val derivedChipValue: Money? = null,
    val defaultBuyInPreview: AmountPreview = AmountPreview(),
    /** The default buy-in as a whole multiple of the big blind, when it is one. */
    val defaultBuyInBigBlinds: Long? = null,
    val totalOnTable: AmountPreview = AmountPreview(),
    val stakeOptions: List<StakeOption> = emptyList(),
    /** The option matching what is typed, or null when the host has entered their own. */
    val selectedStake: StakeOption? = null,
    val overrideEditor: OverrideEditor? = null,
    val isStarting: Boolean = false,
) {
    val selectedCount: Int get() = form.selection.size

    val canStart: Boolean get() = validation.isValid && !isStarting

    val hasRoster: Boolean get() = roster.isNotEmpty()
}

/** One-shot outcomes the screen reacts to but should not re-run on recomposition. */
sealed interface CreateGameEvent {
    data class GameStarted(val gameId: Long) : CreateGameEvent
    data class Message(val text: UiText) : CreateGameEvent
}
