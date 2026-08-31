package com.zango.pokertracker.domain.model

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

    /** One sentence, identical wherever a name is typed. */
    fun tooLongMessage(label: String): String = "$label can be at most $MAX_LENGTH characters"
}
