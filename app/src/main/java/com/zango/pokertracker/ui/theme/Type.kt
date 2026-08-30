package com.zango.pokertracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.zango.pokertracker.R

private val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun family(name: String, vararg weights: FontWeight) = FontFamily(
    weights.map { Font(googleFont = GoogleFont(name), fontProvider = GoogleFontProvider, weight = it) },
)

/** Screen titles and the few numbers that carry a screen. Used sparingly. */
val DisplayFamily: FontFamily = family("Space Grotesk", FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** Labels, helper text, buttons: everything that is read as prose. */
val BodyFamily: FontFamily = family("Inter", FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)

/**
 * Every money amount, chip count, blind level and the elapsed timer.
 *
 * Monospaced for a functional reason: the table total and the timer change while being watched,
 * and proportional digits make a live number twitch and a column of amounts fail to line up.
 */
val NumericFamily: FontFamily = family("IBM Plex Mono", FontWeight.Medium, FontWeight.SemiBold)

/**
 * Tabular figures. Redundant on IBM Plex Mono, which is already fixed-pitch, but it keeps digits
 * aligned if the download has not landed yet and the system fallback is proportional.
 */
private const val TABULAR = "tnum"

/**
 * Numeric styles, kept outside [Typography] because Material has no slot for a third family.
 * Reached through `PokerTheme.type`.
 */
data class PokerTypography(
    val numericHero: TextStyle,
    val numericLarge: TextStyle,
    val numericMedium: TextStyle,
    val numericSmall: TextStyle,
    val numericCaption: TextStyle,
)

private fun numeric(size: Int, height: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = NumericFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = TABULAR,
)

val PokerNumerics = PokerTypography(
    numericHero = numeric(40, 44, FontWeight.SemiBold, -0.5),
    numericLarge = numeric(22, 28, FontWeight.SemiBold),
    numericMedium = numeric(17, 24, FontWeight.Medium),
    numericSmall = numeric(14, 20, FontWeight.Medium),
    numericCaption = numeric(12, 16, FontWeight.Medium),
)

private fun display(size: Int, height: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = DisplayFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = tracking.sp,
)

private fun body(size: Int, height: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = BodyFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = tracking.sp,
)

val PokerTypographyScale = Typography(
    displayLarge = display(40, 44, FontWeight.SemiBold, -0.5),
    displayMedium = display(32, 38, FontWeight.SemiBold, -0.25),
    displaySmall = display(26, 32, FontWeight.SemiBold),

    headlineLarge = display(24, 30, FontWeight.SemiBold),
    headlineMedium = display(20, 26, FontWeight.SemiBold),
    headlineSmall = display(18, 24, FontWeight.SemiBold),

    titleLarge = display(20, 26, FontWeight.SemiBold),
    titleMedium = body(16, 22, FontWeight.SemiBold, 0.1),
    titleSmall = body(14, 20, FontWeight.SemiBold, 0.1),

    bodyLarge = body(16, 24, FontWeight.Normal, 0.15),
    bodyMedium = body(14, 20, FontWeight.Normal, 0.15),
    bodySmall = body(12, 18, FontWeight.Normal, 0.2),

    labelLarge = body(14, 20, FontWeight.SemiBold, 0.3),
    labelMedium = body(12, 16, FontWeight.Medium, 0.4),
    labelSmall = body(11, 16, FontWeight.SemiBold, 0.8),
)
