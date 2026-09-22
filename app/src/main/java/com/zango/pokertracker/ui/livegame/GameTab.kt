package com.zango.pokertracker.ui.livegame

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zango.pokertracker.R
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme

/** The side games: the bomb pot countdown and the firetruck count. */
@Composable
fun GameTabContent(
    state: GameTabUiState,
    onFiretruckTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        // Room at the end for the hand-over button, which floats over this tab too.
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.isEmpty) {
            item {
                Text(
                    stringResource(R.string.live_game_tab_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.bombPot?.let { bombPot ->
            item { BombPotPanel(bombPot) }
        }

        state.firetruckRows?.let { rows ->
            item {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SectionLabel(stringResource(R.string.side_game_firetruck))
                    Text(
                        stringResource(R.string.live_firetruck_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.live_nobody_seated),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(rows, key = { it.seatId }) { row ->
                FiretruckPlayerRow(
                    row = row,
                    enabled = state.canTapFiretruck,
                    onTap = { onFiretruckTap(row.seatId) },
                )
            }
        }
    }
}

/** The countdown is the biggest thing on the tab, so it can be read from across the table. */
@Composable
private fun BombPotPanel(bombPot: BombPotUi) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                SectionLabel(stringResource(R.string.live_bomb_pot_next))
            }
            Text(
                bombPot.remaining,
                style = PokerTheme.type.numericHero,
                color = if (bombPot.isRunning) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            LinearProgressIndicator(
                progress = { bombPot.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawStopIndicator = {},
            )
            Text(
                if (bombPot.isRunning) {
                    pluralStringResource(
                        R.plurals.live_bomb_pot_every,
                        bombPot.intervalMinutes,
                        bombPot.intervalMinutes,
                    )
                } else {
                    stringResource(R.string.live_bomb_pot_stopped)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (bombPot.isRunning) ExactAlarmHint()
        }
    }
}

/**
 * Android 12 and later can hold exact alarms back until the host allows them, and an idle phone
 * then delivers the bomb pot alert late. Offered only when that is the case, and checked again
 * whenever the screen comes back, which is how the host returns from the settings page.
 */
@Composable
private fun ExactAlarmHint() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(context) {
        allowed = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        onPauseOrDispose { }
    }
    if (allowed) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.live_bomb_pot_inexact),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        "package:${context.packageName}".toUri(),
                    ),
                )
            },
            modifier = Modifier.height(MinTouchTarget),
        ) { Text(stringResource(R.string.live_bomb_pot_allow_exact)) }
    }
}

/** A name and three dots. The whole row is the button, so it is hard to miss in a hurry. */
@Composable
private fun FiretruckPlayerRow(row: FiretruckRow, enabled: Boolean, onTap: () -> Unit) {
    val description = stringResource(
        R.string.live_firetruck_row_description,
        row.name,
        row.wins,
        FiretruckStreak.DOTS,
    )
    Surface(
        onClick = onTap,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clearAndSetSemantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = if (row.wins > 0) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                row.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(FiretruckStreak.DOTS) { index -> FiretruckDot(filled = index < row.wins) }
            }
        }
    }
}

@Composable
private fun FiretruckDot(filled: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(color)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                },
            ),
    )
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun previewTab() = GameTabUiState(
    bombPot = BombPotUi(intervalMinutes = 30, remaining = "12:34", progress = 0.58f, isRunning = true),
    firetruckRows = listOf(
        FiretruckRow(1, "Anna", 2),
        FiretruckRow(2, "Boris", 0),
        FiretruckRow(3, "Chris", 0),
    ),
    canTapFiretruck = true,
)

@Preview(name = "Game tab", showBackground = true, heightDp = 700)
@Composable
private fun GameTabPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GameTabContent(previewTab(), onFiretruckTap = {})
        }
    }
}

@Preview(name = "Game tab — no side games", showBackground = true, heightDp = 300)
@Composable
private fun GameTabEmptyPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GameTabContent(GameTabUiState(), onFiretruckTap = {})
        }
    }
}
