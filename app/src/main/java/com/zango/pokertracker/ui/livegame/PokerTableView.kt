package com.zango.pokertracker.ui.livegame

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zango.pokertracker.R
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One player's place round the table, in seating order. */
data class TableSeat(val seatId: Long, val name: String)

private val SeatWidth = 68.dp
private val SeatHeight = 64.dp
private val AvatarSize = 40.dp

/**
 * An oval table with everyone round it, in seat order clockwise from the bottom.
 *
 * Tap one player to pick them up, then another to swap the two; tapping the felt or anything else
 * that is not a player puts them back down. Each seat animates to its new place, so a swap reads
 * as two people changing chairs rather than two names changing.
 */
@Composable
fun PokerTableView(
    seats: List<TableSeat>,
    selectedSeatId: Long?,
    enabled: Boolean,
    onSeatTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedName = seats.firstOrNull { it.seatId == selectedSeatId }?.name
    val colors = PokerTheme.colors

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(TABLE_ASPECT_RATIO),
    ) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val seatWidthPx = with(density) { SeatWidth.toPx() }
        val seatHeightPx = with(density) { SeatHeight.toPx() }
        // Seats sit on the rail, so the track they follow is the table inset by half a seat.
        val track = Stadium(
            center = Offset(width / 2, height / 2),
            width = width - seatWidthPx,
            height = height - seatHeightPx,
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SeatWidth / 3, vertical = SeatHeight / 3),
        ) {
            val railWidth = 14.dp.toPx()
            drawStadium(colors.tableRail, inset = 0f)
            drawStadium(colors.tableFelt, inset = railWidth)
            drawStadium(
                colors.tableFeltLine,
                inset = railWidth * 2.2f,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        Text(
            when {
                !enabled -> ""
                selectedName != null -> stringResource(R.string.live_table_hint_selected, selectedName)
                else -> stringResource(R.string.live_table_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = SeatWidth + 8.dp),
        )

        seats.forEachIndexed { index, seat ->
            key(seat.seatId) {
                // Evenly spaced round the rail, clockwise from the bottom middle, where the host
                // usually holds the phone.
                val target = track.pointAt(index.toFloat() / seats.size) -
                    Offset(seatWidthPx / 2, seatHeightPx / 2)
                val position by animateOffsetAsState(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "seat",
                )
                TableSeatChip(
                    seat = seat,
                    selected = seat.seatId == selectedSeatId,
                    enabled = enabled,
                    onTap = { onSeatTap(seat.seatId) },
                    modifier = Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) },
                )
            }
        }
    }
}

@Composable
private fun TableSeatChip(
    seat: TableSeat,
    selected: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "selected",
    )
    // The whole seat takes the tap, so it is easy to hit, but the ripple is drawn inside the
    // circle only: a square flash round a round chair looks like a glitch.
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .size(SeatWidth, SeatHeight)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap,
            )
            .semantics { contentDescription = seat.name },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            modifier = Modifier
                .size(AvatarSize)
                .scale(scale),
            shape = CircleShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            border = BorderStroke(
                width = 2.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .indication(interactionSource, ripple()),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials(seat.name), style = MaterialTheme.typography.labelLarge)
            }
        }
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier.width(SeatWidth),
        ) {
            Text(
                seat.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** "Anna" → "AN", "Anna Karenina" → "AK". Two letters fit the circle at any font scale. */
private fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> ""
    }.uppercase()
}

/**
 * Calls [onTap] for a tap that nothing inside handled: the felt, the gaps between rows, a label.
 * Read in the final pass, after every button and player has had its chance, so tapping a player
 * or a button never counts; and a scroll or drag never counts either.
 */
fun Modifier.onUnhandledTap(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitPointerEventScope {
        while (true) {
            var handled = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.any { it.isConsumed }) handled = true
            } while (event.changes.any { it.pressed })
            if (!handled) onTap()
        }
    }
}

// Long and low like a real table, which also gives the straight sides room to exist.
private const val TABLE_ASPECT_RATIO = 1.6f

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val PreviewSeats = listOf("Anna", "Boris", "Chris", "Dina", "Erik Larsson", "Farida", "Gus")
    .mapIndexed { index, name -> TableSeat(index.toLong(), name) }

@Preview(name = "Table", showBackground = true, widthDp = 360)
@Composable
private fun PokerTablePreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PokerTableView(PreviewSeats, selectedSeatId = 2, enabled = true, onSeatTap = {})
        }
    }
}

@Preview(name = "Table — ten players", showBackground = true, widthDp = 360)
@Composable
private fun PokerTableFullPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PokerTableView(
                PreviewSeats + listOf("Hana", "Igor", "Jon").mapIndexed { i, name -> TableSeat(10L + i, name) },
                selectedSeatId = null,
                enabled = true,
                onSeatTap = {},
            )
        }
    }
}

/**
 * A racetrack: two straight sides joined by half circles, the shape of a real poker table.
 * [pointAt] walks round it by distance rather than by angle, so seats are spread evenly along the
 * straights and the ends alike.
 */
private class Stadium(val center: Offset, width: Float, height: Float) {
    private val radius = height / 2
    private val halfStraight = ((width - height) / 2).coerceAtLeast(0f)
    private val straight = halfStraight * 2
    private val arc = (PI * radius).toFloat()
    private val perimeter = 2 * straight + 2 * arc

    /** The point [fraction] of the way round, clockwise from the bottom middle. */
    fun pointAt(fraction: Float): Offset {
        var s = (fraction.mod(1f)) * perimeter
        // Bottom edge, from the middle to the left end.
        if (s < halfStraight) return Offset(center.x - s, center.y + radius)
        s -= halfStraight
        // Left end, bottom round to top.
        if (s < arc) return onArc(center.x - halfStraight, PI / 2 + s / radius)
        s -= arc
        // Top edge, left to right.
        if (s < straight) return Offset(center.x - halfStraight + s, center.y - radius)
        s -= straight
        // Right end, top round to bottom.
        if (s < arc) return onArc(center.x + halfStraight, -PI / 2 + s / radius)
        s -= arc
        // Bottom edge, from the right end back to the middle.
        return Offset(center.x + halfStraight - s, center.y + radius)
    }

    private fun onArc(x: Float, angle: Double) = Offset(
        x + (radius * cos(angle)).toFloat(),
        center.y + (radius * sin(angle)).toFloat(),
    )
}

/** The table's outline inset by [inset], filled or stroked. */
private fun DrawScope.drawStadium(color: Color, inset: Float, style: DrawStyle = Fill) {
    val height = size.height - inset * 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, height),
        cornerRadius = CornerRadius(height / 2),
        style = style,
    )
}
