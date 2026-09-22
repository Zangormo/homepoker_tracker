package com.zango.pokertracker.bombpot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BombPotEntryPoint {
    fun bombPotAlarms(): BombPotAlarms
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Runs [block] off the main thread, keeping the process alive until it is done. */
internal fun BroadcastReceiver.work(context: Context, block: suspend (BombPotAlarms) -> Unit) {
    val alarms = EntryPointAccessors
        .fromApplication(context.applicationContext, BombPotEntryPoint::class.java)
        .bombPotAlarms()
    val pending = goAsync()
    receiverScope.launch {
        try {
            block(alarms)
        } finally {
            pending.finish()
        }
    }
}

/** A bomb pot is due. */
class BombPotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOMB_POT) return
        val gameId = intent.getLongExtra(EXTRA_GAME_ID, -1L).takeIf { it > 0 } ?: return
        work(context) { it.onAlarm(gameId) }
    }

    companion object {
        const val ACTION_BOMB_POT = "com.zango.pokertracker.action.BOMB_POT"
        const val EXTRA_GAME_ID = "gameId"
    }
}

/**
 * Puts the alarms back after something wiped them: a reboot, an update of the app, or the host
 * granting or taking away exact alarms, which cancels everything already set.
 */
class BombPotRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_EXACT_ALARMS_CHANGED,
            -> work(context) { it.resync() }
        }
    }
}

/**
 * AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, spelled out because the
 * constant only exists from Android 12. Older versions never send it, so listening costs nothing.
 */
private const val ACTION_EXACT_ALARMS_CHANGED =
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
