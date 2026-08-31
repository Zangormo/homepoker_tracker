package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stake level: the pair of blinds a game is played at.
 *
 * The pair travels together because that is how a table talks about itself — "we play 0.05/0.10"
 * — rather than as two numbers that happen to sit next to each other.
 */
class StakesTest {

    @Test
    fun `a level reads the way a host would write it out`() {
        assertEquals("0.05 / 0.10", Stakes(Money(50_000), Money(100_000)).label())
    }

    @Test
    fun `whole-unit stakes still read as currency rather than as counts`() {
        assertEquals("1.00 / 2.00", Stakes(Money(1_000_000), Money(2_000_000)).label())
    }

    @Test
    fun `micro stakes keep the precision they are played at`() {
        assertEquals("0.005 / 0.01", Stakes(Money(5_000), Money(10_000)).label())
    }

    @Test
    fun `two levels with the same blinds are the same level`() {
        assertEquals(
            Stakes(Money(50_000), Money(100_000)),
            Stakes(Money(50_000), Money(100_000)),
        )
    }

    // -----------------------------------------------------------------------------------------
    // The ladder the app ships with
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the standard ladder is the one hosts recognise`() {
        assertEquals(
            listOf("0.01 / 0.02", "0.05 / 0.10", "0.10 / 0.20", "0.50 / 1.00", "1.00 / 2.00"),
            Stakes.COMMON.map { it.label() },
        )
    }

    @Test
    fun `every standard level is a real one`() {
        Stakes.COMMON.forEach { stakes ->
            assertTrue("${stakes.label()} has a non-positive small blind", stakes.smallBlind.isPositive)
            assertTrue("${stakes.label()} is not a rising pair", stakes.smallBlind < stakes.bigBlind)
        }
    }

    @Test
    fun `the ladder climbs, so the picker never has to reorder it`() {
        val bigBlinds = Stakes.COMMON.map { it.bigBlind.micros }
        assertEquals(bigBlinds.sorted(), bigBlinds)
    }

    @Test
    fun `the ladder has no duplicates to waste a slot on`() {
        assertEquals(Stakes.COMMON.distinct().size, Stakes.COMMON.size)
    }

    /** The cap has to leave room for the host's own levels, or the list is not theirs to edit. */
    @Test
    fun `the standard ladder leaves room under the cap`() {
        assertEquals(10, Stakes.MAX_PRESETS)
        assertTrue(Stakes.COMMON.size < Stakes.MAX_PRESETS)
    }
}
