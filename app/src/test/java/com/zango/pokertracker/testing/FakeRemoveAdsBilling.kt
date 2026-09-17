package com.zango.pokertracker.testing

import android.app.Activity
import com.zango.pokertracker.billing.RemoveAdsBilling
import kotlinx.coroutines.flow.MutableStateFlow

/** Google Play stood in for: state is set directly, and each launch answers with [nextLaunch]. */
class FakeRemoveAdsBilling : RemoveAdsBilling {
    override val isAdsRemoved = MutableStateFlow(false)
    override val isPurchasePending = MutableStateFlow(false)
    override val removeAdsPrice = MutableStateFlow<String?>(null)

    var nextLaunch = RemoveAdsBilling.LaunchResult.LAUNCHED
    var launches = 0
        private set

    override suspend fun launchPurchaseFlow(activity: Activity): RemoveAdsBilling.LaunchResult {
        launches++
        return nextLaunch
    }
}
