package com.homepoker_tracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
        topBar = { TopAppBar(title = { Text("Poker Tracker") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewGame,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New game") },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isEmpty -> Centered(Modifier.padding(padding)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No games yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap New game to set one up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
private fun HistoryList(
    state: HistoryUiState,
    onResumeGame: (Long) -> Unit,
    onOpenSettlement: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.inProgress.isNotEmpty()) {
            item { SectionHeader("Still running") }
            items(state.inProgress, key = { it.gameId }) { row ->
                GameCard(row = row, onClick = { onResumeGame(row.gameId) })
            }
        }
        if (state.finished.isNotEmpty()) {
            item { SectionHeader("Finished") }
            items(state.finished, key = { it.gameId }) { row ->
                GameCard(row = row, onClick = { onOpenSettlement(row.gameId) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun GameCard(row: HistoryRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isInProgress) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (row.isInProgress) {
                    AssistChip(onClick = onClick, label = { Text("Resume") })
                }
            }
            Text(
                row.dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                buildString {
                    append("${row.totalOnTable.format()} on the table")
                    row.chipsOnTable?.let { append(" · $it chips") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildString {
                    append("${row.playerCount} player${if (row.playerCount == 1) "" else "s"}")
                    append(" · ${row.buyInCount} buy-in${if (row.buyInCount == 1) "" else "s"}")
                    append(" · ${row.stakes}")
                    row.durationLabel?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
