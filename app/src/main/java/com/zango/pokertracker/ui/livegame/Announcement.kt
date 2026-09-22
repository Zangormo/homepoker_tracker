package com.zango.pokertracker.ui.livegame

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zango.pokertracker.R
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Long enough to read and enjoy, short enough that nobody has to find the close button. */
private const val SHOW_MILLIS = 4_500L

/**
 * The whole screen goes dark and one word lands in the middle of it. A firetruck gets confetti
 * and fireworks on top; a bomb pot is a call to action rather than a celebration, so it gets the
 * word and the clock only. Tapping anywhere closes it, and it closes itself after a few seconds.
 */
@Composable
fun AnnouncementOverlay(announcement: LiveAnnouncement, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val dismiss by rememberUpdatedState(onDismiss)
    val scrimAlpha = remember { Animatable(0f) }
    val wordScale = remember { Animatable(0.2f) }

    LaunchedEffect(announcement) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { scrimAlpha.animateTo(1f, tween(durationMillis = 250)) }
        wordScale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        )
    }
    LaunchedEffect(announcement) {
        delay(SHOW_MILLIS)
        dismiss()
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 600), RepeatMode.Reverse),
        label = "pulse",
    )

    val celebrate = announcement is LiveAnnouncement.Firetruck
    val palette = PokerTheme.colors.celebration

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(scrimAlpha.value)
            .background(PokerTheme.colors.scrim)
            // Swallows every touch, so nothing underneath is tapped by accident while it shows.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (celebrate) Celebration(palette)

        Column(
            modifier = Modifier
                .padding(24.dp)
                .scale(wordScale.value * pulse),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (celebrate) Icons.Filled.LocalFireDepartment else Icons.Filled.Timer,
                contentDescription = null,
                tint = palette[1],
                modifier = Modifier.size(72.dp),
            )
            Text(
                stringResource(
                    if (celebrate) R.string.firetruck_announcement else R.string.bomb_pot_announcement,
                ).uppercase(),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    brush = Brush.verticalGradient(palette.take(3)),
                    fontSize = 52.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                ),
            )
            if (announcement is LiveAnnouncement.Firetruck && announcement.playerName.isNotBlank()) {
                Text(
                    announcement.playerName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            stringResource(R.string.announcement_tap_to_close),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Confetti and fireworks
// ---------------------------------------------------------------------------------------------

/** A scrap of confetti, falling from above the top edge. Positions are fractions of the screen. */
private class Confetto(random: Random, val color: Color) {
    val x = random.nextFloat()
    val startY = -0.1f - random.nextFloat() * 0.6f
    val fallPerSecond = 0.25f + random.nextFloat() * 0.3f
    val swayAmplitude = 0.01f + random.nextFloat() * 0.03f
    val swayPerSecond = 1f + random.nextFloat() * 2f
    val spinPerSecond = (random.nextFloat() - 0.5f) * 720f
    val width = 6f + random.nextFloat() * 6f
    val height = width * (1.5f + random.nextFloat())
}

/** One rocket's worth of sparks, bursting at a point after a delay. */
private class Firework(random: Random, palette: List<Color>) {
    val x = 0.15f + random.nextFloat() * 0.7f
    val y = 0.12f + random.nextFloat() * 0.35f
    val startSeconds = random.nextFloat() * 2.5f
    val sparks = List(SPARKS) { index ->
        val angle = 2 * PI * index / SPARKS
        val speed = 180f + random.nextFloat() * 120f
        Offset((cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat())
    }
    val color = palette[random.nextInt(palette.size)]

    companion object {
        const val SPARKS = 28
        const val LIFE_SECONDS = 1.4f
    }
}

private const val GRAVITY = 260f

@Composable
private fun Celebration(palette: List<Color>) {
    val random = remember { Random(System.nanoTime()) }
    val confetti = remember { List(140) { Confetto(random, palette[it % palette.size]) } }
    val fireworks = remember { List(5) { Firework(random, palette) } }

    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { seconds = (it - start) / 1000f }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        fireworks.forEach { drawFirework(it, seconds) }
        confetti.forEach { drawConfetto(it, seconds) }
    }
}

private fun DrawScope.drawConfetto(piece: Confetto, seconds: Float) {
    val y = (piece.startY + piece.fallPerSecond * seconds) * size.height
    if (y > size.height + piece.height) return
    val x = (piece.x + sin(seconds * piece.swayPerSecond) * piece.swayAmplitude) * size.width
    rotate(degrees = piece.spinPerSecond * seconds, pivot = Offset(x, y)) {
        drawRect(
            color = piece.color,
            topLeft = Offset(x - piece.width / 2, y - piece.height / 2),
            size = Size(piece.width, piece.height),
        )
    }
}

private fun DrawScope.drawFirework(firework: Firework, seconds: Float) {
    val t = seconds - firework.startSeconds
    if (t < 0f || t > Firework.LIFE_SECONDS) return
    val fade = 1f - t / Firework.LIFE_SECONDS
    val origin = Offset(firework.x * size.width, firework.y * size.height)
    firework.sparks.forEach { velocity ->
        val position = origin + velocity * t + Offset(0f, GRAVITY * t * t / 2)
        drawCircle(color = firework.color, radius = 5f * fade + 1f, center = position, alpha = fade)
        // A short tail behind each spark, so the burst reads as motion rather than dots.
        val tail = origin + velocity * (t * 0.8f) + Offset(0f, GRAVITY * t * t * 0.32f)
        drawLine(color = firework.color, start = tail, end = position, strokeWidth = 2f, alpha = fade * 0.6f)
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(name = "Announcement — firetruck", showBackground = true, heightDp = 700)
@Composable
private fun FiretruckAnnouncementPreview() {
    PokerTrackerTheme {
        AnnouncementOverlay(LiveAnnouncement.Firetruck("Anna"), onDismiss = {})
    }
}

@Preview(name = "Announcement — bomb pot", showBackground = true, heightDp = 700)
@Composable
private fun BombPotAnnouncementPreview() {
    PokerTrackerTheme {
        AnnouncementOverlay(LiveAnnouncement.BombPot, onDismiss = {})
    }
}
