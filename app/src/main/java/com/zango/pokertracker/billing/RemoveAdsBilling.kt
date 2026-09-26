package com.zango.pokertracker.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * What a screen needs of the "Remove ads" purchase. Implemented by [BillingManager]; kept apart so
 * a ViewModel can be tested without Google Play's BillingClient.
 */
interface RemoveAdsBilling {

    /** How [launchPurchaseFlow] went, for the screen to react to. */
    enum class LaunchResult {
        /** Play's purchase sheet is showing; the outcome arrives through [isAdsRemoved]. */
        LAUNCHED,

        /** Nothing to buy: this user already owns it. */
        ALREADY_OWNED,

        /** Play could not be reached, or does not offer the product to this user. */
        UNAVAILABLE,
    }

    /** True only once Play reports the product as PURCHASED. Never true while PENDING. */
    val isAdsRemoved: StateFlow<Boolean>

    /** A purchase was made but not paid for yet. Ads stay on until it clears. */
    val isPurchasePending: StateFlow<Boolean>

    /** The price as Play formats it for this user, e.g. "€2.99". Null until Play has said. */
    val removeAdsPrice: StateFlow<String?>

    /** How [debugResetPurchase] went. */
    enum class ResetResult {
        /** The purchase was consumed; ads are back and the product can be bought again. */
        RESET,

        /** Play holds no "Remove ads" purchase for this account. */
        NOTHING_TO_RESET,

        /** Play could not be reached, or refused the consume. */
        FAILED,
    }

    /** Opens Play's purchase sheet over [activity]. */
    suspend fun launchPurchaseFlow(activity: Activity): LaunchResult

    /**
     * Tester tools only (`BuildConfig.DEBUG_TOOLS`): consumes the "Remove ads" purchase so it can
     * be bought again, and turns ads back on. Does nothing in any other build.
     */
    suspend fun debugResetPurchase(): ResetResult
}
