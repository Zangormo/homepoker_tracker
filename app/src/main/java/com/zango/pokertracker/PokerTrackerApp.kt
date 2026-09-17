package com.zango.pokertracker

import android.app.Application
import com.zango.pokertracker.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PokerTrackerApp : Application() {

    @Inject
    lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()
        // Once per process start. The setup callback reads existing purchases, which is how a
        // purchase is restored after a reinstall: there is no account or backend to ask instead.
        billingManager.startConnection()
    }
}
