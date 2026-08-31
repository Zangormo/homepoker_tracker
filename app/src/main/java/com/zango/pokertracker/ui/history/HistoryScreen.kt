package com.zango.pokertracker.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zango.pokertracker.R
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.ui.common.CashAmountText
import com.zango.pokertracker.ui.common.ChipAmountText
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.common.resolve
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme

@Composable
fun HistoryScreen(
    onOpenMenu: () -> Unit,
    onNewGame: () -> Unit,
    onResumeGame: (Long) -> Unit,
    onOpenSettlement: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(context))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewGame,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.history_new_game)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isEmpty -> Centered(Modifier.padding(padding)) { EmptyHistory() }
            else -> HistoryList(
                state = state,
                onResumeGame = onResumeGame,
                onOpenSettlement = onOpenSettlement,
                onDeleteRequested = viewModel::onDeleteRequested,
                modifier = Modifier.padding(padding),
            )
        }
    }

    state.pendingDeletion?.let { row ->
        DeleteGameDialog(
            row = row,
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDismissDelete,
        )
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
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.history_empty_body),
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
    onDeleteRequested: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.inProgress.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.history_section_running)) }
            items(state.inProgress, key = { it.gameId }) { row ->
                GameRow(
                    row = row,
                    onClick = { onResumeGame(row.gameId) },
                    onDelete = { onDeleteRequested(row.gameId) },
                )
            }
        }
        if (state.finished.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.history_section_finished), modifier = Modifier.padding(top = 12.dp)) }
            items(state.finished, key = { it.gameId }) { row ->
                GameRow(
                    row = row,
                    onClick = { onOpenSettlement(row.gameId) },
                    onDelete = { onDeleteRequested(row.gameId) },
                )
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
private fun GameRow(row: HistoryRow, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        // A running game is outlined so it can be found again; a finished one only earns an
        // outline once every payment it called for has actually been handed over. The two never
        // sit in the same section, and each says in words which it is.
        border = when {
            row.isInProgress -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            row.isFullyPaid -> BorderStroke(1.dp, PokerTheme.colors.positive)
            else -> null
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
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
                            stringResource(R.string.history_badge_running),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.dateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The outline says the same thing in colour; this says it in words, for
                    // anyone who cannot separate the two greens or is reading a screenshot.
                    if (row.isFullyPaid) {
                        Text(
                            stringResource(R.string.history_badge_paid_up),
                            style = MaterialTheme.typography.labelSmall,
                            color = PokerTheme.colors.positive,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.history_row_counts,
                            pluralStringResource(R.plurals.player_count, row.playerCount, row.playerCount),
                            pluralStringResource(R.plurals.buy_in_count, row.buyInCount, row.buyInCount),
                        ),
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

            RowMenu(gameName = row.name, onDelete = onDelete)
        }
    }
}

/**
 * Deleting is rare and cannot be undone, so it sits behind a menu rather than on the row where a
 * mis-tap while scrolling could reach it.
 */
@Composable
private fun RowMenu(gameName: String, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(MinTouchTarget),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.history_row_menu, gameName),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_delete_game), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DeleteGameDialog(row: HistoryRow, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.history_delete_title, row.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (row.isInProgress) {
                        stringResource(R.string.history_delete_running_body)
                    } else {
                        stringResource(R.string.history_delete_finished_body)
                    },
                )
                Text(
                    stringResource(
                        R.string.history_delete_detail,
                        pluralStringResource(R.plurals.player_count, row.playerCount, row.playerCount),
                        pluralStringResource(R.plurals.buy_in_count, row.buyInCount, row.buyInCount),
                        row.totalOnTable.format(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.history_delete_irreversible),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_delete_keep)) } },
    )
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
    paid: Boolean = false,
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
    isFullyPaid = paid,
)

private fun populated() = HistoryUiState(
    isLoading = false,
    inProgress = listOf(
        historyRow(1, "Thursday", "30 Aug 2026 · 20:15", 5, 7, 7_000_000, 1400, null, true),
    ),
    finished = listOf(
        historyRow(2, "Last Thursday", "23 Aug 2026 · 20:05", 6, 9, 9_000_000, 1800, "4h 12m", false, paid = true),
        historyRow(3, "Boris's birthday", "16 Aug 2026 · 19:30", 8, 14, 14_000_000, 2800, "6h 03m", false),
        historyRow(4, "Quiet one", "09 Aug 2026 · 21:00", 3, 3, 3_000_000, 600, "1h 48m", false),
    ),
)

@Preview(name = "History — populated", showBackground = true, heightDp = 700)
@Composable
private fun HistoryPopulatedPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HistoryList(populated(), {}, {}, {})
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

@Preview(name = "History — confirm delete", showBackground = true, heightDp = 520)
@Composable
private fun DeleteFinishedGamePreview() {
    PokerTrackerTheme {
        DeleteGameDialog(populated().finished.first(), onConfirm = {}, onDismiss = {})
    }
}

@Preview(name = "History — confirm delete, still running", showBackground = true, heightDp = 520)
@Composable
private fun DeleteRunningGamePreview() {
    PokerTrackerTheme {
        DeleteGameDialog(populated().inProgress.first(), onConfirm = {}, onDismiss = {})
    }
}

@Preview(name = "History — 200% font", showBackground = true, fontScale = 2.0f, heightDp = 1000)
@Composable
private fun HistoryLargeFontPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HistoryList(populated(), {}, {}, {})
        }
    }
}
