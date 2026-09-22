package com.zango.pokertracker.domain.model

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * When bomb pots come round: every [intervalMinutes] after the game started, forever.
 *
 * Everything is derived from the start time and the clock, never counted, so the timer restarts
 * the instant one is due, and a killed process or a phone that slept through one comes back on
 * the same beat instead of drifting.
 */
data class BombPotSchedule(val startedAt: Long, val intervalMinutes: Int) {

    init {
        require(intervalMinutes > 0) { "A bomb pot interval must be positive" }
    }

    private val intervalMillis: Long get() = intervalMinutes * MILLIS_PER_MINUTE

    /** How many have come round by [now]. A clock that moved backwards counts as none. */
    fun countAt(now: Long): Long = (now - startedAt).coerceAtLeast(0) / intervalMillis

    /** The next one strictly after [now]. */
    fun nextAfter(now: Long): Long = startedAt + (countAt(now) + 1) * intervalMillis

    /** Time left until the next one, never more than a full interval and never zero. */
    fun remainingAt(now: Long): Long = nextAfter(now) - now.coerceAtLeast(startedAt)
}

fun Game.bombPotSchedule(): BombPotSchedule? =
    bombPotIntervalMinutes?.let { BombPotSchedule(startedAt, it) }
