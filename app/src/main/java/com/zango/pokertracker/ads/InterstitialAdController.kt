package com.zango.pokertracker.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.zango.pokertracker.BuildConfig
import com.zango.pokertracker.di.AdsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's full-screen ads. There are two places one may appear, and between them they share a
 * single cap: at most one full-screen ad per [GAMES_PER_FULL_SCREEN_AD] finished games.
 *
 * - [Slot.BEFORE_SETTLEMENT]: the moment a game is ended, on the way into its settlement. Meant
 *   for a video; whether the unit serves video is set on the ad unit in the AdMob console.
 * - [Slot.AFTER_PAID_UP]: when the host leaves a paid-up game's settlement for the games list.
 *
 * Both are breaks between tasks rather than interruptions of one: the chip counts are saved and
 * nothing has been typed on the next screen yet. Ads are loaded ahead of time so neither slot ever
 * makes the host wait. When a game's turn comes and the first slot has nothing loaded, the turn is
 * not lost: the paid-up slot of the same game, or the next game, takes it.
 */
@Singleton
class InterstitialAdController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consentManager: ConsentManager,
    @AdsPreferences private val preferences: SharedPreferences,
) {
    enum class Slot(val adUnitId: String) {
        BEFORE_SETTLEMENT(BuildConfig.ADMOB_INTERSTITIAL_BEFORE_SETTLEMENT_UNIT_ID),
        AFTER_PAID_UP(BuildConfig.ADMOB_INTERSTITIAL_PAID_UP_UNIT_ID),
    }

    // InterstitialAd.load and show must be called on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val loaded = mutableMapOf<Slot, InterstitialAd>()
    private val loading = mutableSetOf<Slot>()

    init {
        // Nothing is requested before consent: the first load waits for the SDK to be initialized.
        scope.launch {
            consentManager.adsReady.first { it }
            preload()
        }
    }

    /** The host has just ended [gameId] and its settlement is about to open. */
    fun onGameEnded(activity: Activity, gameId: Long) = onBreak(activity, gameId, Slot.BEFORE_SETTLEMENT)

    /** The host left [gameId]'s settlement for the games list, with every payment ticked off. */
    fun onGamePaidUp(activity: Activity, gameId: Long) = onBreak(activity, gameId, Slot.AFTER_PAID_UP)

    /**
     * Loads every slot that has no ad and none on its way. Does nothing until consent has cleared
     * the SDK. Public only because the ad callbacks call it; a private function reached from those
     * objects would need a synthetic accessor.
     */
    fun preload() {
        if (!consentManager.adsReady.value) return
        Slot.entries.forEach { slot ->
            if (slot in loaded || slot in loading) return@forEach
            loading += slot
            InterstitialAd.load(
                context,
                slot.adUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        loaded[slot] = interstitialAd
                        loading -= slot
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        // No retry loop: the next break tries again.
                        Log.w(TAG, "$slot failed to load: ${adError.message}")
                        loading -= slot
                    }
                },
            )
        }
    }

    private fun onBreak(activity: Activity, gameId: Long, slot: Slot) {
        countGameOnce(gameId)

        val ready = loaded[slot]
        val gamesSinceAd = preferences.getInt(KEY_GAMES_SINCE_AD, 0)
        if (gamesSinceAd < GAMES_PER_FULL_SCREEN_AD || ready == null || !consentManager.canRequestAds) {
            preload()
            return
        }
        loaded -= slot
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                // Reset only once it really showed; a failed show keeps the turn for the next break.
                preferences.edit { putInt(KEY_GAMES_SINCE_AD, 0) }
            }

            override fun onAdDismissedFullScreenContent() = preload()

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "$slot failed to show: ${adError.message}")
                preload()
            }
        }
        ready.show(activity)
    }

    /**
     * Counts a finished game towards the cap, once however many breaks it passes through: ending
     * it, leaving it paid up, and reopening it from history later.
     */
    private fun countGameOnce(gameId: Long) {
        val counted = preferences.getStringSet(KEY_COUNTED_GAME_IDS, null).orEmpty()
        val key = gameId.toString()
        if (key in counted) return
        // The id set grows by one per finished game, a few dozen bytes a year for a weekly table.
        preferences.edit {
            putStringSet(KEY_COUNTED_GAME_IDS, counted + key)
            putInt(KEY_GAMES_SINCE_AD, preferences.getInt(KEY_GAMES_SINCE_AD, 0) + 1)
        }
    }

    companion object {
        /**
         * One full-screen ad per this many finished games, whichever slot it lands in.
         *
         * The audience is one host running a home game, typically once a week, so 3 means roughly
         * one full-screen ad every three weeks of real use. It also keeps the first two games -
         * when the host is still deciding whether to trust the app with the table's money - free
         * of full-screen ads entirely. Sharing the cap between the two slots means a single evening
         * never gets both a video before the settlement and another ad after it.
         */
        const val GAMES_PER_FULL_SCREEN_AD = 3

        private const val TAG = "InterstitialAds"
        private const val KEY_GAMES_SINCE_AD = "finished_games_since_full_screen_ad"
        private const val KEY_COUNTED_GAME_IDS = "finished_game_ids_counted"
    }
}
