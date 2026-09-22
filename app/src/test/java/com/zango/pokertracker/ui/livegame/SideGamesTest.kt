package com.zango.pokertracker.ui.livegame

import com.zango.pokertracker.domain.model.BombPotSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SideGamesTest {

    // --- Firetruck -------------------------------------------------------------------------

    @Test
    fun `each win by the same player adds a dot`() {
        val streak = FiretruckStreak().tap(1).tap(1)

        assertEquals(2, streak.winsFor(1))
        assertFalse(streak.isFiretruck)
    }

    @Test
    fun `three in a row is a firetruck`() {
        assertTrue(FiretruckStreak().tap(1).tap(1).tap(1).isFiretruck)
    }

    @Test
    fun `someone else winning clears the previous player and starts their own run`() {
        val streak = FiretruckStreak().tap(1).tap(1).tap(2)

        assertEquals(0, streak.winsFor(1))
        assertEquals(1, streak.winsFor(2))
    }

    @Test
    fun `a finished firetruck starts over instead of growing a fourth dot`() {
        val streak = FiretruckStreak().tap(1).tap(1).tap(1).tap(1)

        assertEquals(1, streak.winsFor(1))
    }

    // --- Bomb pot schedule -----------------------------------------------------------------

    private val start = 1_000_000L
    private val thirty = BombPotSchedule(startedAt = start, intervalMinutes = 30)
    private val minute = 60_000L

    @Test
    fun `the first bomb pot is one interval after the start`() {
        assertEquals(start + 30 * minute, thirty.nextAfter(start))
        assertEquals(0L, thirty.countAt(start))
    }

    @Test
    fun `the timer restarts the moment one is due`() {
        val due = start + 30 * minute

        assertEquals(1L, thirty.countAt(due))
        assertEquals(due + 30 * minute, thirty.nextAfter(due))
        assertEquals(30 * minute, thirty.remainingAt(due))
    }

    @Test
    fun `a phone that slept through several comes back on the same beat`() {
        val later = start + 95 * minute

        assertEquals(3L, thirty.countAt(later))
        assertEquals(start + 120 * minute, thirty.nextAfter(later))
    }

    @Test
    fun `a clock behind the start counts none and shows a full interval`() {
        assertEquals(0L, thirty.countAt(start - minute))
        assertEquals(30 * minute, thirty.remainingAt(start - minute))
    }

    // --- Countdown -------------------------------------------------------------------------

    @Test
    fun `a fresh timer reads its full length`() {
        assertEquals("30:00", formatCountdown(30 * minute))
    }

    @Test
    fun `part of a second rounds up so the timer never shows zero early`() {
        assertEquals("00:01", formatCountdown(1))
        assertEquals("12:35", formatCountdown(12 * minute + 34_001))
    }

    @Test
    fun `an hour or more shows hours`() {
        assertEquals("1:05:00", formatCountdown(65 * minute))
    }
}
