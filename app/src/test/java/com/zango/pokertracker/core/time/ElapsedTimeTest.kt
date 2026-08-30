package com.zango.pokertracker.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

private const val SECOND = 1_000L
private const val MINUTE = 60 * SECOND
private const val HOUR = 60 * MINUTE

class ElapsedTimeTest {

    @Test
    fun `a game that just started reads as zero minutes`() {
        assertEquals("0m", formatElapsed(0))
        assertEquals("0m", formatElapsed(59 * SECOND))
    }

    @Test
    fun `under an hour only minutes are shown`() {
        assertEquals("1m", formatElapsed(MINUTE))
        assertEquals("47m", formatElapsed(47 * MINUTE))
        assertEquals("59m", formatElapsed(59 * MINUTE + 59 * SECOND))
    }

    @Test
    fun `past an hour both parts are shown`() {
        assertEquals("1h 0m", formatElapsed(HOUR))
        assertEquals("2h 47m", formatElapsed(2 * HOUR + 47 * MINUTE))
        assertEquals("2h 47m", formatElapsed(2 * HOUR + 47 * MINUTE + 59 * SECOND))
    }

    @Test
    fun `a long session keeps counting hours rather than rolling over to days`() {
        assertEquals("13h 30m", formatElapsed(13 * HOUR + 30 * MINUTE))
        assertEquals("36h 0m", formatElapsed(36 * HOUR))
    }

    @Test
    fun `minutes are truncated, never rounded up`() {
        // A game 1 second short of the next minute has not reached it yet.
        assertEquals("2h 46m", formatElapsed(2 * HOUR + 47 * MINUTE - SECOND))
    }

    @Test
    fun `a clock that moved backwards reads as zero rather than as nonsense`() {
        assertEquals("0m", formatElapsed(-1))
        assertEquals("0m", formatElapsed(-5 * HOUR))
    }

    @Test
    fun `the reading is derived from timestamps, so a gap in observation loses nothing`() {
        // Standing in for process death: the app sees nothing between these two calls, but the
        // elapsed time is computed from the stored start either way.
        val startedAt = 1_700_000_000_000L
        val muchLater = startedAt + 3 * HOUR + 12 * MINUTE
        assertEquals("3h 12m", formatElapsed(muchLater - startedAt))
    }
}
