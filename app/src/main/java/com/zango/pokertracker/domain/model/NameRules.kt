package com.zango.pokertracker.domain.model

import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText

/**
 * What a player or a game may be called.
 *
 * Names are short because of where they are read: a settlement passed across a table, a results
 * table with four numeric columns beside the name, a row of roster chips in a dialog. A name that
 * wraps or is cut off there costs more than the letters it saves, so the limit is enforced rather
 * than suggested.
 *
 * Checked against the trimmed name, since trailing spaces are not something to charge anyone for,
 * and applied in the repository as well as in every field, so no path into the database can get
 * around it.
 */
object NameRules {

    const val MAX_LENGTH: Int = 12

    fun isTooLong(name: String): Boolean = name.trim().length > MAX_LENGTH

    /**
     * One sentence, identical wherever a name is typed. [label] names which name is too long —
     * a player's or a game's — and is itself a translated string.
     */
    fun tooLongMessage(label: UiText): UiText =
        UiText.plural(R.plurals.error_name_too_long, MAX_LENGTH, label, MAX_LENGTH)
}
