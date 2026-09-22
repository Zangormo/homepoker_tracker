package com.zango.pokertracker

import android.app.Application
import com.zango.pokertracker.billing.BillingManager
import com.zango.pokertracker.bombpot.BombPotAlarms
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class PokerTrackerApp : Application() {

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var bombPotAlarms: BombPotAlarms

    /** Lives as long as the process: work that belongs to the app rather than to any screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Once per process start. The setup callback reads existing purchases, which is how a
        // purchase is restored after a reinstall: there is no account or backend to ask instead.
        billingManager.startConnection()
        bombPotAlarms.start(appScope)
    }
}
