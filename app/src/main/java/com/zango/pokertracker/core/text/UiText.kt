package com.zango.pokertracker.core.text

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * A sentence the app has decided to say, before it has been said in any particular language.
 *
 * `stringResource` is a Composable function: it reads the current Composition for a context and
 * the active locale. ViewModels and the domain have neither, so the layers that decide *which*
 * message to show cannot be the layers that look it up. Instead they name the string and hand
 * over its arguments, and the screen resolves it at the moment it draws it.
 *
 * A resource id is a plain `Int`, so nothing here drags the Android framework into a layer that
 * is otherwise pure Kotlin, and the tests that assert on these stay on the JVM.
 */
sealed interface UiText {

    /** A string resource, with whatever arguments its placeholders need. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /**
     * A quantity string. [count] chooses the grammatical form and is separate from [args],
     * because a language may need the number in a different place from where it counts.
     */
    data class Plural(
        @PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /**
     * Text that is already in its final form because it is the host's own: a player's name, a
     * game's name, an amount the app formatted. Never translated.
     */
    data class Raw(val text: String) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): Res = Res(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): Plural =
            Plural(id, count, args.toList())

        fun raw(text: String): Raw = Raw(text)
    }
}
