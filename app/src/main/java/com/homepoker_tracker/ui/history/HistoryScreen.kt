package com.homepoker_tracker.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.ui.common.CashAmountText
import com.homepoker_tracker.ui.common.ChipAmountText
import com.homepoker_tracker.ui.common.SectionLabel
import com.homepoker_tracker.ui.theme.PokerTheme
import com.homepoker_tracker.ui.theme.PokerTrackerTheme

@Composable
fun HistoryScreen(
    onNewGame: () -> Unit,
    onResumeGame: (Long) -> Unit,
    onOpenSettlement: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Poker Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewGame,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New game") },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isEmpty -> Centered(Modifier.padding(padding)) { EmptyHistory() }
            else -> HistoryList(
                state = state,
                onResumeGame = onResumeGame,
                onOpenSettlement = onOpenSettlement,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun EmptyHistory() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("No games yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap New game to set one up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    onResumeGame: (Long) -> Unit,
    onOpenSettlement: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.inProgress.isNotEmpty()) {
            item { SectionLabel("Still running") }
            items(state.inProgress, key = { it.gameId }) { row ->
                GameRow(row = row, onClick = { onResumeGame(row.gameId) })
            }
        }
        if (state.finished.isNotEmpty()) {
            item {
                SectionLabel("Finished", modifier = Modifier.padding(top = 12.dp))
            }
            items(state.finished, key = { it.gameId }) { row ->
                GameRow(row = row, onClick = { onOpenSettlement(row.gameId) })
            }
        }
    }
}

/**
 * Deliberately understated. This is a list of things that already happened; a running game is
 * marked and outlined so it can be found again, but nothing here should pull attention away from
 * the game actually being played.
 */
@Composable
private fun GameRow(row: HistoryRow, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (row.isInProgress) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (row.isInProgress) {
                    Text(
                        "RUNNING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        row.durationLabel.orEmpty(),
                        style = PokerTheme.type.numericSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                row.dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        append(row.playerCount)
                        append(if (row.playerCount == 1) " player" else " players")
                        append(" · ")
                        append(row.buyInCount)
                        append(if (row.buyInCount == 1) " buy-in" else " buy-ins")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CashAmountText(
                        row.totalOnTable,
                        style = PokerTheme.type.numericCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.chipsOnTable?.let {
                        ChipAmountText(
                            it,
                            style = PokerTheme.type.numericCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun historyRow(
    id: Long,
    name: String,
    date: String,
    players: Int,
    buyIns: Int,
    pot: Long,
    chips: Long,
    duration: String?,
    running: Boolean,
) = HistoryRow(
    gameId = id,
    name = name,
    dateLabel = date,
    stakes = "0.005 / 0.01",
    playerCount = players,
    buyInCount = buyIns,
    totalOnTable = Money(pot),
    chipsOnTable = Chips(chips),
    durationLabel = duration,
    isInProgress = running,
)

private fun populated() = HistoryUiState(
    isLoading = false,
    inProgress = listOf(
        historyRow(1, "Thursday", "30 Aug 2026 · 20:15", 5, 7, 7_000_000, 1400, null, true),
    ),
    finished = listOf(
        historyRow(2, "Last Thursday", "23 Aug 2026 · 20:05", 6, 9, 9_000_000, 1800, "4h 12m", false),
        historyRow(3, "Boris's birthday", "16 Aug 2026 · 19:30", 8, 14, 14_000_000, 2800, "6h 03m", false),
        historyRow(4, "Quiet one", "09 Aug 2026 · 21:00", 3, 3, 3_000_000, 600, "1h 48m", false),
    ),
)

@Preview(name = "History — populated", showBackground = true, heightDp = 700)
@Composable
private fun HistoryPopulatedPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HistoryList(populated(), {}, {})
        }
    }
}

@Preview(name = "History — empty", showBackground = true, heightDp = 400)
@Composable
private fun HistoryEmptyPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Centered { EmptyHistory() }
        }
    }
}

@Preview(name = "History — 200% font", showBackground = true, fontScale = 2.0f, heightDp = 1000)
@Composable
private fun HistoryLargeFontPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HistoryList(populated(), {}, {})
        }
    }
}
