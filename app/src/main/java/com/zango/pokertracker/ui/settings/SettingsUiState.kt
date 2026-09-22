package com.zango.pokertracker.ui.settings

import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.domain.model.Stakes

/** One stake level in the editor. */
data class StakeRow(val stakes: Stakes, val label: String)

/** The "add a level" dialog, held open while the host types. */
data class StakesEditor(
    val smallBlind: String = "",
    val bigBlind: String = "",
    val error: UiText? = null,
) {
    val canAdd: Boolean get() = smallBlind.isNotBlank() && bigBlind.isNotBlank()
}

/**
 * The "Remove ads" purchase. [price] is null until Google Play has been reached, and stays null in
 * builds Play does not know, such as debug builds with their own package name.
 */
data class RemoveAdsUiState(
    val isRemoved: Boolean = false,
    val isPending: Boolean = false,
    val price: String? = null,
)

data class SettingsUiState(
    val isLoading: Boolean = true,
    val stakes: List<StakeRow> = emptyList(),
    val editor: StakesEditor? = null,
    val removeAds: RemoveAdsUiState = RemoveAdsUiState(),
    /** Whether the ad privacy options entry is shown: only where the law requires one. */
    val showAdPrivacyOptions: Boolean = false,
) {
    val count: Int get() = stakes.size

    val isFull: Boolean get() = count >= Stakes.MAX_PRESETS

    val isEmpty: Boolean get() = !isLoading && stakes.isEmpty()
}

sealed interface SettingsEvent {
    data class Message(val text: UiText) : SettingsEvent

    /** A level came off the list. Offered back, because taking one off is a one-tap mistake. */
    data class Removed(val stakes: Stakes, val text: UiText) : SettingsEvent
}
