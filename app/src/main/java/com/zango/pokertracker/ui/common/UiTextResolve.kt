package com.zango.pokertracker.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zango.pokertracker.core.text.UiText

/**
 * Turns a [UiText] into the sentence for the language the phone is set to.
 *
 * The only place in the app that knows what language anything is in. Anything above this line
 * decides *which* message to show; this decides how to say it.
 */
@Composable
@ReadOnlyComposable
fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.resolveArgs())
    }

    is UiText.Plural -> if (args.isEmpty()) {
        pluralStringResource(id, count)
    } else {
        pluralStringResource(id, count, *args.resolveArgs())
    }
}

/** Resolves a message that may not be there, so callers can pass a nullable straight through. */
@Composable
@ReadOnlyComposable
fun UiText?.resolveOrNull(): String? = this?.resolve()

/**
 * An argument can itself be a [UiText] — "5 players" inside "5 players and 7 buy-ins go with it"
 * — so nested messages are resolved before the outer one is formatted.
 */
@Composable
@ReadOnlyComposable
private fun List<Any>.resolveArgs(): Array<Any> =
    Array(size) { index -> this[index].let { if (it is UiText) it.resolve() else it } }

/**
 * The same lookup, for code that is not in a Composition.
 *
 * Snackbars are shown from a `LaunchedEffect`, which is a coroutine rather than a composable, so
 * it cannot call [resolve]. The screen captures the context first and resolves there.
 */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> if (args.isEmpty()) {
        context.getString(id)
    } else {
        context.getString(id, *args.resolveArgs(context))
    }

    is UiText.Plural -> if (args.isEmpty()) {
        context.resources.getQuantityString(id, count)
    } else {
        context.resources.getQuantityString(id, count, *args.resolveArgs(context))
    }
}

private fun List<Any>.resolveArgs(context: Context): Array<Any> =
    Array(size) { index -> this[index].let { if (it is UiText) it.resolve(context) else it } }
