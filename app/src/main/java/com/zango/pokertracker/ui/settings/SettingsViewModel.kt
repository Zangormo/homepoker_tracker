package com.zango.pokertracker.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zango.pokertracker.R
import com.zango.pokertracker.ads.AdPrivacy
import com.zango.pokertracker.billing.RemoveAdsBilling
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.data.repository.AddStakesResult
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.Stakes
import com.zango.pokertracker.ui.common.parseBlinds
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
 * Settings: the stake levels the game picker offers, the "Remove ads" purchase, and the way back
 * into the ad consent choice where the law requires one.
 *
 * The list fills itself as games are played, which is convenient right up until a one-off night
 * at odd blinds is stuck in the picker for good. This is where the host prunes it, and where a
 * level can be added for a game not played yet.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PokerRepository,
    private val billing: RemoveAdsBilling,
    private val adPrivacy: AdPrivacy,
) : ViewModel() {

    private val editing = MutableStateFlow<StakesEditor?>(null)

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(
            repository.observeStakeOptions(),
            editing,
            removeAdsState(),
            adPrivacy.isPrivacyOptionsRequired,
        ) { stakes, editor, removeAds, privacyOptionsRequired ->
            SettingsUiState(
                isLoading = false,
                stakes = stakes.map { StakeRow(it, it.label()) },
                editor = editor,
                removeAds = removeAds,
                showAdPrivacyOptions = privacyOptionsRequired,
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
                        UiText.plural(
                            R.plurals.error_stakes_list_full,
                            Stakes.MAX_PRESETS,
                            Stakes.MAX_PRESETS,
                        ),
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
        val blinds = parseBlinds(editor.smallBlind, editor.bigBlind)
        val small = blinds.smallBlind
        val big = blinds.bigBlind
        if (small == null || big == null) {
            editing.update { it?.copy(error = blinds.smallBlindError ?: blinds.bigBlindError) }
            return
        }

        val stakes = Stakes(small, big)
        viewModelScope.launch {
            when (repository.addStakes(stakes)) {
                AddStakesResult.Added -> {
                    editing.value = null
                    eventChannel.send(
                        SettingsEvent.Message(
                            UiText.of(R.string.message_stakes_added, stakes.label()),
                        ),
                    )
                }

                AddStakesResult.AlreadyListed ->
                    editing.update {
                        it?.copy(
                            error = UiText.of(
                                R.string.error_stakes_already_listed,
                                stakes.label(),
                            ),
                        )
                    }

                AddStakesResult.ListFull -> editing.update {
                    it?.copy(
                        error = UiText.plural(
                            R.plurals.error_stakes_list_full_short,
                            Stakes.MAX_PRESETS,
                            Stakes.MAX_PRESETS,
                        ),
                    )
                }
            }
        }
    }

    fun onRemove(stakes: Stakes) {
        viewModelScope.launch {
            repository.removeStakes(stakes)
            eventChannel.send(
                SettingsEvent.Removed(
                    stakes,
                    UiText.of(R.string.message_stakes_removed, stakes.label()),
                ),
            )
        }
    }

    /**
     * Opens Google Play's purchase sheet. The outcome of the payment arrives through
     * [RemoveAdsBilling.isAdsRemoved]; only a failure to open the sheet at all is reported here.
     */
    fun onRemoveAds(activity: Activity) {
        viewModelScope.launch {
            val message = when (billing.launchPurchaseFlow(activity)) {
                RemoveAdsBilling.LaunchResult.LAUNCHED -> return@launch
                RemoveAdsBilling.LaunchResult.ALREADY_OWNED -> R.string.message_ads_already_removed
                RemoveAdsBilling.LaunchResult.UNAVAILABLE -> R.string.error_purchase_unavailable
            }
            eventChannel.send(SettingsEvent.Message(UiText.of(message)))
        }
    }

    /**
     * Opens Google's form for changing the ad consent choice. Google saves the new choice itself
     * and the ads SDK reads it on the next request, so nothing comes back here to handle.
     */
    fun onAdPrivacyOptions(activity: Activity) = adPrivacy.showPrivacyOptionsForm(activity)

    /** Puts back a level taken off by mistake, straight from the snackbar. */
    fun onUndoRemove(stakes: Stakes) {
        viewModelScope.launch {
            if (repository.addStakes(stakes) == AddStakesResult.ListFull) {
                eventChannel.send(
                    SettingsEvent.Message(
                        UiText.of(R.string.message_stakes_stayed_off, stakes.label()),
                    ),
                )
            }
        }
    }

    private fun removeAdsState(): Flow<RemoveAdsUiState> = combine(
        billing.isAdsRemoved,
        billing.isPurchasePending,
        billing.removeAdsPrice,
    ) { removed, pending, price -> RemoveAdsUiState(isRemoved = removed, isPending = pending, price = price) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
