package com.zango.pokertracker.domain.model

import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText

/**
 * What a player or a game may be called.
 *
 * Player names are the shorter of the two because of where they are read: a settlement passed
 * across a table, a results table with four numeric columns beside the name, a row of roster chips
 * in a dialog. A name that wraps or is cut off there costs more than the letters it saves. A game
 * name is only ever read as a heading with a whole line to itself, so it can afford to be longer.
 *
 * Checked against the trimmed name, since trailing spaces are not something to charge anyone for,
 * and applied in the repository as well as in every field, so no path into the database can get
 * around it. The fields themselves stop at the limit rather than letting a name be typed and then
 * refused - see the `maxLength` argument on the name fields.
 *
 * Players and games get their own function each rather than sharing one with a limit argument:
 * the two limits differ, and a call site that picks the wrong one should not compile into
 * something plausible.
 */
object NameRules {

    const val MAX_PLAYER_LENGTH: Int = 12

    const val MAX_GAME_LENGTH: Int = 25

    fun isPlayerNameTooLong(name: String): Boolean = name.trim().length > MAX_PLAYER_LENGTH

    fun isGameNameTooLong(name: String): Boolean = name.trim().length > MAX_GAME_LENGTH

    /**
     * One sentence, identical wherever a player's name is typed, carrying the limit it enforces so
     * a translation cannot drift from the rule.
     */
    fun playerNameTooLongMessage(): UiText =
        tooLongMessage(UiText.of(R.string.error_name_label_player), MAX_PLAYER_LENGTH)

    /** The same sentence for a game's own name, which has a longer limit of its own. */
    fun gameNameTooLongMessage(): UiText =
        tooLongMessage(UiText.of(R.string.error_name_label_game), MAX_GAME_LENGTH)

    private fun tooLongMessage(label: UiText, maxLength: Int): UiText =
        UiText.plural(R.plurals.error_name_too_long, maxLength, label, maxLength)
}
