package com.zango.pokertracker.domain.model

import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
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

    /**
     * Which name is too long travels as an argument, and so does the limit, so a translation
     * cannot drift from the rule the code actually enforces.
     */
    @Test
    fun `the message names the field it came from and carries the limit`() {
        val label = UiText.of(R.string.error_name_label_game)

        assertEquals(
            UiText.plural(R.plurals.error_name_too_long, 12, label, 12),
            NameRules.tooLongMessage(label),
        )
    }
}
