package com.zango.pokertracker.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.zango.pokertracker.core.money.CashFormat

/**
 * How cash is written everywhere below it: the host's currency symbol, on the side their language
 * puts it. Provided once at the top of the app and changed only from settings, which is why it is
 * static: a change redraws everything, and that is exactly what a new currency should do.
 *
 * Previews get a dollar sign, so they look like the app rather than like bare numbers.
 */
val LocalCashFormat = staticCompositionLocalOf { CashFormat(symbol = "$", symbolFirst = true) }
