package com.zango.pokertracker.domain.model

import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameRulesTest {

    @Test
    fun `a player name of exactly the limit is allowed`() {
        assertEquals(12, NameRules.MAX_PLAYER_LENGTH)
        assertFalse(NameRules.isPlayerNameTooLong("Christopher!"))
    }

    @Test
    fun `one character over is not`() {
        assertTrue(NameRules.isPlayerNameTooLong("Christopher!!"))
    }

    /** Trailing spaces are trimmed before storing, so they must not count against the limit. */
    @Test
    fun `surrounding whitespace does not count`() {
        assertFalse(NameRules.isPlayerNameTooLong("  Christopher!  "))
    }

    /**
     * A game name is read as a heading with a line to itself rather than squeezed beside numbers,
     * so it gets a good deal more room than a player's.
     */
    @Test
    fun `a game name has its own longer limit`() {
        assertEquals(25, NameRules.MAX_GAME_LENGTH)
        assertFalse(NameRules.isGameNameTooLong("Thursday night home game!"))
        assertTrue(NameRules.isGameNameTooLong("Thursday night home games!"))
    }

    /** A name too long for a player can still be a perfectly good game name. */
    @Test
    fun `the two limits are independent`() {
        val name = "Thursday regulars"

        assertTrue(NameRules.isPlayerNameTooLong(name))
        assertFalse(NameRules.isGameNameTooLong(name))
    }

    /**
     * Which name is too long travels as an argument, and so does the limit, so a translation
     * cannot drift from the rule the code actually enforces.
     */
    @Test
    fun `each message names the field it came from and carries its own limit`() {
        assertEquals(
            UiText.plural(
                R.plurals.error_name_too_long,
                12,
                UiText.of(R.string.error_name_label_player),
                12,
            ),
            NameRules.playerNameTooLongMessage(),
        )
        assertEquals(
            UiText.plural(
                R.plurals.error_name_too_long,
                25,
                UiText.of(R.string.error_name_label_game),
                25,
            ),
            NameRules.gameNameTooLongMessage(),
        )
    }
}
