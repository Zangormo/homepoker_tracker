package com.zango.pokertracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameRulesTest {

    @Test
    fun `a name of exactly the limit is allowed`() {
        assertEquals(12, NameRules.MAX_LENGTH)
        assertFalse(NameRules.isTooLong("Christopher!"))
    }

    @Test
    fun `one character over is not`() {
        assertTrue(NameRules.isTooLong("Christopher!!"))
    }

    /** Trailing spaces are trimmed before storing, so they must not count against the limit. */
    @Test
    fun `surrounding whitespace does not count`() {
        assertFalse(NameRules.isTooLong("  Christopher!  "))
    }

    @Test
    fun `the message names the field it came from`() {
        assertEquals("A game name can be at most 12 characters", NameRules.tooLongMessage("A game name"))
    }
}
