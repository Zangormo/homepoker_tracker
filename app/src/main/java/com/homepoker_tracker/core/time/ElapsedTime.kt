package com.homepoker_tracker.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/**
 * How long a game has been running, as "2h 47m".
 *
 * Always derived from two timestamps rather than accumulated from ticks, so backgrounding the
 * app, killing the process or turning the screen off for an hour cannot lose time. A negative
 * span (a clock that moved backwards) reads as zero rather than as nonsense.
 */
fun formatElapsed(millis: Long): String {
    val totalMinutes = millis.coerceAtLeast(0) / MILLIS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Emits the current time once a second so an elapsed readout can keep up.
 *
 * The value is the wall clock, not a count, so whatever consumes it recomputes the whole span
 * from the stored start. Collapsing repeated identical labels is the consumer's job.
 */
fun Clock.tick(periodMillis: Long = 1_000L): Flow<Long> = flow {
    while (true) {
        emit(nowMillis())
        delay(periodMillis)
    }
}
