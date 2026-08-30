package com.homepoker_tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One scheme, always dark. There is no light variant and no dynamic colour: the palette is the
 * product's identity, and a host reading a settlement across a table at 1am is not served by the
 * screen turning white.
 */
private val PokerColorScheme = darkColorScheme(
    primary = Tokens.Accent,
    // Bright green is a light colour. Anything filled with it takes the darkest surface as its
    // label, never white, which would sit at roughly 1.4:1.
    onPrimary = Tokens.SurfaceBase,
    primaryContainer = Tokens.AccentContainer,
    onPrimaryContainer = Tokens.Accent,
    inversePrimary = Tokens.AccentDim,

    secondary = Tokens.TextSecondary,
    onSecondary = Tokens.SurfaceBase,
    secondaryContainer = Tokens.SurfaceOverlay,
    onSecondaryContainer = Tokens.TextPrimary,

    tertiary = Tokens.AccentDim,
    onTertiary = Tokens.SurfaceBase,
    tertiaryContainer = Tokens.AccentContainer,
    onTertiaryContainer = Tokens.Accent,

    background = Tokens.SurfaceBase,
    onBackground = Tokens.TextPrimary,
    surface = Tokens.SurfaceBase,
    onSurface = Tokens.TextPrimary,
    surfaceVariant = Tokens.SurfaceRaised,
    onSurfaceVariant = Tokens.TextSecondary,
    surfaceTint = Tokens.Accent,

    surfaceContainerLowest = Tokens.SurfaceSunken,
    surfaceContainerLow = Tokens.SurfaceBase,
    surfaceContainer = Tokens.SurfaceRaised,
    surfaceContainerHigh = Tokens.SurfaceOverlay,
    surfaceContainerHighest = Tokens.SurfaceHighest,

    error = Tokens.Error,
    onError = Tokens.SurfaceBase,
    errorContainer = Tokens.ErrorContainer,
    onErrorContainer = Tokens.Error,

    outline = Tokens.Outline,
    outlineVariant = Tokens.Divider,
    scrim = Tokens.SurfaceSunken,

    inverseSurface = Tokens.TextPrimary,
    inverseOnSurface = Tokens.SurfaceBase,
)

internal val LocalPokerColors = staticCompositionLocalOf { DarkPokerColors }
internal val LocalPokerTypography = staticCompositionLocalOf { PokerNumerics }

/** Access to the tokens Material has no slot for: `PokerTheme.colors`, `PokerTheme.type`. */
object PokerTheme {
    val colors: PokerColors
        @Composable @ReadOnlyComposable get() = LocalPokerColors.current

    val type: PokerTypography
        @Composable @ReadOnlyComposable get() = LocalPokerTypography.current
}

@Composable
fun PokerTrackerTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPokerColors provides DarkPokerColors,
        LocalPokerTypography provides PokerNumerics,
    ) {
        MaterialTheme(
            colorScheme = PokerColorScheme,
            typography = PokerTypographyScale,
            shapes = PokerShapes,
            content = content,
        )
    }
}
