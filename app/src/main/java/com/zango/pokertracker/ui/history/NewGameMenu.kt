package com.zango.pokertracker.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zango.pokertracker.R
import com.zango.pokertracker.ui.theme.PokerTheme

private const val OPEN_MILLIS = 220

/** How far behind the one below it each option starts, so they rise in a quick cascade. */
private const val STAGGER_MILLIS = 50

/**
 * "New game" opens into two ways of getting a game: set one up, or load one another host handed
 * over. The button stays where it was and turns into a close button, and the options rise out of
 * it, so it reads as one control opening rather than a new screen.
 */
@Composable
fun NewGameMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
    onLoad: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(OPEN_MILLIS),
        label = "plus",
    )
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top to bottom as drawn; the bottom one, nearest the button, arrives first.
        MenuOption(
            visible = expanded,
            delayMillis = STAGGER_MILLIS,
            label = stringResource(R.string.history_load_game),
            icon = Icons.Filled.QrCodeScanner,
            primary = false,
            onClick = onLoad,
        )
        MenuOption(
            visible = expanded,
            delayMillis = 0,
            label = stringResource(R.string.history_create_game),
            icon = Icons.Filled.AddCircle,
            primary = true,
            onClick = onCreate,
        )
        ExtendedFloatingActionButton(
            onClick = onToggle,
            expanded = !expanded,
            icon = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (expanded) stringResource(R.string.action_cancel) else null,
                    modifier = Modifier.rotate(rotation),
                )
            },
            text = { Text(stringResource(R.string.history_new_game)) },
        )
    }
}

@Composable
private fun MenuOption(
    visible: Boolean,
    delayMillis: Int,
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(OPEN_MILLIS, delayMillis)) +
            slideInVertically(tween(OPEN_MILLIS, delayMillis)) { it / 2 } +
            scaleIn(tween(OPEN_MILLIS, delayMillis), initialScale = 0.6f, transformOrigin = TransformOrigin(1f, 1f)),
        exit = fadeOut(tween(OPEN_MILLIS / 2)) +
            slideOutVertically(tween(OPEN_MILLIS / 2)) { it / 2 } +
            scaleOut(tween(OPEN_MILLIS / 2), targetScale = 0.6f, transformOrigin = TransformOrigin(1f, 1f)),
        label = label,
    ) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(icon, contentDescription = null) },
            text = { Text(label) },
            containerColor = if (primary) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (primary) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Dims the list while the menu is open, and closes the menu when tapped, so tapping anywhere
 * that is not an option backs out of it.
 */
@Composable
fun NewGameMenuScrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(OPEN_MILLIS)),
        exit = fadeOut(tween(OPEN_MILLIS)),
        label = "scrim",
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.7f)
                .background(PokerTheme.colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
}
