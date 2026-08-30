package com.zango.pokertracker.core.time

/**
 * Wall-clock time source.
 *
 * Injected rather than called statically so timestamps are controllable in tests, and so the
 * live-game elapsed timer always derives from a stored `startedAt` rather than counting ticks.
 */
fun interface Clock {
    fun nowMillis(): Long
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
