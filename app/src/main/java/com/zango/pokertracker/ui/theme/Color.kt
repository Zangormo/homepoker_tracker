package com.zango.pokertracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette, defined once. Nothing outside this file constructs a [Color].
 *
 * Dark grey and one bright green, with bright red held back for problems. The green is the only
 * saturated colour that appears routinely, which is what keeps it meaning "act here" rather than
 * decorating the screen.
 */
internal object Tokens {
    val SurfaceBase = Color(0xFF14171A)
    val SurfaceRaised = Color(0xFF1D2125)
    val SurfaceOverlay = Color(0xFF272C31)
    val Outline = Color(0xFF363C42)
    val Accent = Color(0xFF2EE68A)
    val AccentDim = Color(0xFF1A9D5E)
    val Error = Color(0xFFFF4155)
    val Negative = Color(0xFFE5707E)
    val TextPrimary = Color(0xFFEDF1F3)
    val TextSecondary = Color(0xFF8B959C)

    /** Sunk below [SurfaceBase] so a scrim or inset well still reads as recessed. */
    val SurfaceSunken = Color(0xFF101316)

    /** Top of the elevation ramp, for menus sitting above a dialog. */
    val SurfaceHighest = Color(0xFF2F353B)

    /**
     * Green- and red-tinted containers, derived from the two accents against [SurfaceBase].
     * Both are dark enough to carry their accent as text at better than 8:1.
     */
    val AccentContainer = Color(0xFF123024)
    val ErrorContainer = Color(0xFF3A1620)

    /** Hairline between rows, quieter than [Outline], which borders interactive things. */
    val Divider = Color(0xFF23282D)

    /**
     * Party colours, for the one moment the app is allowed to be loud: a side-game announcement.
     * Never used for anything the host has to read.
     */
    val CelebrationRed = Color(0xFFFF4B2B)
    val CelebrationOrange = Color(0xFFFF9F1C)
    val CelebrationYellow = Color(0xFFFFE14D)
    val CelebrationBlue = Color(0xFF3DB8FF)
    val CelebrationPink = Color(0xFFFF5FB7)

    /** The seating view: a felt table inside a dark rail, muted so the player names stay first. */
    val TableFelt = Color(0xFF155C3B)
    val TableFeltLine = Color(0xFF2E7A55)
    val TableRail = Color(0xFF2C221A)

    /** Darkens the whole screen behind an announcement. */
    val Scrim = Color(0xD9000000)
}

/**
 * Colours Material has no slot for.
 *
 * [negative] is deliberately not [Tokens.Error]: a player who lost money is a normal outcome, and
 * painting every losing row in the same red as a validation failure makes a working results
 * screen look broken.
 */
data class PokerColors(
    val positive: Color,
    val negative: Color,
    val chip: Color,
    val cash: Color,
    val divider: Color,
    /** The confetti, and the announcement lettering in [celebration] first. */
    val celebration: List<Color>,
    val scrim: Color,
    val tableFelt: Color,
    val tableFeltLine: Color,
    val tableRail: Color,
)

internal val DarkPokerColors = PokerColors(
    positive = Tokens.Accent,
    negative = Tokens.Negative,
    // Chips carry the accent and cash stays grey, so that when a chip figure and a cash figure
    // sit side by side the eye separates them before reading either number.
    chip = Tokens.Accent,
    cash = Tokens.TextSecondary,
    divider = Tokens.Divider,
    celebration = listOf(
        Tokens.CelebrationRed,
        Tokens.CelebrationOrange,
        Tokens.CelebrationYellow,
        Tokens.Accent,
        Tokens.CelebrationBlue,
        Tokens.CelebrationPink,
    ),
    scrim = Tokens.Scrim,
    tableFelt = Tokens.TableFelt,
    tableFeltLine = Tokens.TableFeltLine,
    tableRail = Tokens.TableRail,
)
