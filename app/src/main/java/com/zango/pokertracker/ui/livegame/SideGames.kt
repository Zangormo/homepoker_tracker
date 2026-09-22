package com.zango.pokertracker.ui.livegame

import java.util.Locale

/**
 * The firetruck count: whoever wins keeps adding a dot, and anyone else winning wipes the slate
 * and starts their own run. [DOTS] in a row is a firetruck.
 */
data class FiretruckStreak(val seatId: Long? = null, val wins: Int = 0) {

    val isFiretruck: Boolean get() = wins >= DOTS

    /** One more win for [seatId]. A finished streak starts over rather than growing past [DOTS]. */
    fun tap(seatId: Long): FiretruckStreak =
        if (seatId == this.seatId && wins < DOTS) copy(wins = wins + 1) else FiretruckStreak(seatId, 1)

    fun winsFor(seatId: Long): Int = if (seatId == this.seatId) wins else 0

    companion object {
        const val DOTS = 3
    }
}

/** The bomb pot timer as the game tab shows it. */
data class BombPotUi(
    val intervalMinutes: Int,
    /** "12:34", or "1:02:03" for intervals of an hour or more. */
    val remaining: String,
    /** How far through the current interval, 0 to 1, for the bar under the countdown. */
    val progress: Float,
    /** False once the game has finished: the timer stops rather than counting on for nobody. */
    val isRunning: Boolean,
)

/** One player on the firetruck list. */
data class FiretruckRow(val seatId: Long, val name: String, val wins: Int)

data class GameTabUiState(
    val bombPot: BombPotUi? = null,
    /** Null when the game is not a firetruck game, empty when nobody is left at the table. */
    val firetruckRows: List<FiretruckRow>? = null,
    val canTapFiretruck: Boolean = false,
) {
    val isEmpty: Boolean get() = bombPot == null && firetruckRows == null
}

/** Something big enough to take over the screen for a moment. */
sealed interface LiveAnnouncement {
    data object BombPot : LiveAnnouncement
    data class Firetruck(val playerName: String) : LiveAnnouncement
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/**
 * A countdown as "mm:ss", or "h:mm:ss" from an hour up. Rounded up to the second, so a fresh
 * 30 minute timer reads 30:00 rather than 29:59 and nothing reads 0:00 while time is still left.
 */
fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
