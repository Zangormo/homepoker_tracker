package com.zango.pokertracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.zango.pokertracker.R

/**
 * The chip mark that tells a chip figure from a cash one everywhere in the app.
 *
 * Material has no chip glyph, so this comes from a vector drawable. It is declared black and
 * tinted at every call site, so the colour in the resource is never the colour on screen.
 */
val PokerChip: ImageVector
    @Composable get() = ImageVector.vectorResource(R.drawable.ic_poker_chip)
