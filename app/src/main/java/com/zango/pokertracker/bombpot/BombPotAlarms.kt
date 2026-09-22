package com.zango.pokertracker.bombpot

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.zango.pokertracker.MainActivity
import com.zango.pokertracker.R
import com.zango.pokertracker.core.time.Clock
import com.zango.pokertracker.data.local.dao.GameDao
import com.zango.pokertracker.data.local.entity.GameEntity
import com.zango.pokertracker.domain.model.BombPotSchedule
import com.zango.pokertracker.domain.model.GameStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one alarm per running bomb pot game, so the host hears about a bomb pot with the app
 * closed, the process killed or the screen off.
 *
 * The alarms follow the database rather than the screens: whatever starts, ends or deletes a game,
 * the list of running bomb pot games changes and the alarms are brought in line with it. An alarm
 * that outlives its game anyway (the process was dead when the game ended) finds the game over
 * when it fires and quietly stops.
 */
@Singleton
class BombPotAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val clock: Clock,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /** Game ids with an alarm set by this process, so ended games can have theirs taken down. */
    private val scheduled = mutableSetOf<Long>()

    /** Follows the running games for as long as [scope] lives. Called once per process. */
    fun start(scope: CoroutineScope) {
        createChannel()
        scope.launch {
            gameDao.observeRunningBombPots().collect { games -> sync(games) }
        }
    }

    /** One pass over the running games, for a process woken by a reboot or an app update. */
    suspend fun resync() {
        createChannel()
        sync(gameDao.observeRunningBombPots().first())
    }

    /**
     * An alarm went off. Tells the host unless they are already looking at the game's screen,
     * which announces it itself, and sets the next one straight away.
     */
    suspend fun onAlarm(gameId: Long) {
        val game = gameDao.load(gameId)
        val schedule = game?.schedule()
        if (game == null || schedule == null || game.status != GameStatus.IN_PROGRESS) {
            cancel(gameId)
            return
        }
        if (BombPotPresence.visibleGameId != gameId) notify(game)
        setAlarm(gameId, schedule.nextAfter(clock.nowMillis()))
    }

    /** True when alarms land on the minute; otherwise Android may hold them back while idle. */
    fun canBeExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    @Synchronized
    private fun sync(games: List<GameEntity>) {
        val now = clock.nowMillis()
        val running = games.mapNotNull { game -> game.schedule()?.let { game.id to it } }.toMap()
        (scheduled - running.keys).forEach(::cancel)
        running.forEach { (gameId, schedule) -> setAlarm(gameId, schedule.nextAfter(now)) }
    }

    /**
     * Set as an alarm clock: the kind alarm apps use, which the system treats as the most
     * important alarm there is. It fires on time through Doze, and it is the kind Xiaomi and other
     * aggressive battery savers are least inclined to hold back; an ordinary exact alarm was
     * reported not to arrive on a Xiaomi phone once the app had been closed. It shows the alarm
     * icon in the status bar while bomb pots are due.
     *
     * Without permission for exact alarms, which Android 14 no longer grants by default, it falls
     * back to an ordinary alarm that Android may deliver late; the game tab offers the switch.
     */
    @Synchronized
    private fun setAlarm(gameId: Long, at: Long) {
        val intent = alarmIntent(gameId)
        if (canBeExact()) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAppIntent()), intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
        scheduled += gameId
    }

    @Synchronized
    private fun cancel(gameId: Long) {
        alarmManager.cancel(alarmIntent(gameId))
        scheduled -= gameId
    }

    private fun alarmIntent(gameId: Long): PendingIntent {
        val intent = Intent(context, BombPotReceiver::class.java)
            .setAction(BombPotReceiver.ACTION_BOMB_POT)
            // The data makes each game's intent distinct, so one game's alarm never replaces
            // another's even though they share an action and a receiver.
            .setData("pokertracker://bomb-pot/$gameId".toUri())
            .putExtra(BombPotReceiver.EXTRA_GAME_ID, gameId)
        return PendingIntent.getBroadcast(
            context,
            gameId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.bomb_pot_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.bomb_pot_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notify(game: GameEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bomb_pot)
            .setContentTitle(context.getString(R.string.bomb_pot_announcement))
            .setContentText(context.getString(R.string.bomb_pot_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(game.id.toInt(), notification)
    }

    /** Opens the app: from the notification, and from the alarm icon in the status bar. */
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun GameEntity.schedule(): BombPotSchedule? =
        bombPotIntervalMinutes?.takeIf { it > 0 }?.let { BombPotSchedule(startedAt, it) }

    private companion object {
        const val CHANNEL_ID = "bomb_pot"
    }
}

/**
 * Which game's screen is on show right now, if any. That screen announces a bomb pot itself, so
 * a notification on top of it would only be noise.
 *
 * In memory on purpose: when the process has been killed nothing is on screen, which is exactly
 * what a fresh process reads here.
 */
object BombPotPresence {
    @Volatile
    var visibleGameId: Long? = null
}
