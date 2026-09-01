package com.zango.pokertracker.ui.players

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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.NameRules
import com.zango.pokertracker.ui.common.CashAmountText
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.NetCashText
import com.zango.pokertracker.ui.common.PokerTextField
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.common.resolve
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme

/**
 * The roster. Every regular the host has ever added, what they have paid in over the years and
 * how far up or down they are, with the individual games one tap away.
 */
@Composable
fun PlayersScreen(
    onOpenMenu: () -> Unit,
    onOpenPlayer: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayersEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(context))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.players_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                },
                actions = {
                    // Hidden behind an action rather than sitting above the list: the order is set
                    // once in a while, and the rows themselves are what the screen is for.
                    ListingMenu(
                        sort = state.sort,
                        onlyWithGames = state.onlyWithGames,
                        onSortSelected = viewModel::onSortSelected,
                        onOnlyWithGamesChange = viewModel::onOnlyWithGamesChange,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::onAddRequested,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.players_add)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isEmpty -> Centered(Modifier.padding(padding)) { EmptyRoster() }
            state.isFilteredEmpty -> Centered(Modifier.padding(padding)) {
                FilteredOutRoster(onClearFilter = { viewModel.onOnlyWithGamesChange(false) })
            }

            else -> PlayerList(
                state = state,
                onOpenPlayer = onOpenPlayer,
                onRename = viewModel::onRenameRequested,
                onDelete = viewModel::onDeleteRequested,
                onRestore = viewModel::onRestore,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.isAdding) {
        AddPlayerDialog(
            name = state.newPlayerName,
            error = state.newPlayerError,
            onNameChange = viewModel::onNewPlayerNameChange,
            onConfirm = viewModel::onAddPlayer,
            onDismiss = viewModel::onDismissAdd,
        )
    }

    state.renaming?.let { editor ->
        RenamePlayerDialog(
            editor = editor,
            onNameChange = viewModel::onRenameTextChange,
            onConfirm = viewModel::onConfirmRename,
            onDismiss = viewModel::onDismissRename,
        )
    }

    state.deleting?.let { editor ->
        DeletePlayerDialog(
            editor = editor,
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

/**
 * The order the roster is listed in, and the one thing worth filtering it by.
 *
 * Choosing an order closes the menu, because that is the whole errand. The filter is a switch and
 * leaves it open, so it can be flicked on and the order changed in the same visit.
 */
@Composable
private fun ListingMenu(
    sort: PlayerSort,
    onlyWithGames: Boolean,
    onSortSelected: (PlayerSort) -> Unit,
    onOnlyWithGamesChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.players_sort_and_filter),
                // A filter that is on changes what the list contains, so it says so from the bar
                // rather than only from inside the menu that set it.
                tint = if (onlyWithGames) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                },
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuHeader(stringResource(R.string.players_sort_header))
            PlayerSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        onSortSelected(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == sort) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            MenuHeader(stringResource(R.string.players_filter_header))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.players_filter_played)) },
                onClick = { onOnlyWithGamesChange(!onlyWithGames) },
                leadingIcon = {
                    // The row carries the click, so the box itself must not take a second one.
                    Checkbox(checked = onlyWithGames, onCheckedChange = null)
                },
            )
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyRoster() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.players_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.players_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * There are players, but the filter matches none of them. Said plainly, with the way out beside
 * it, so it cannot be mistaken for an empty roster.
 */
@Composable
private fun FilteredOutRoster(onClearFilter: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.players_filtered_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.players_filtered_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClearFilter) {
            Text(stringResource(R.string.players_filter_clear))
        }
    }
}

@Composable
private fun PlayerList(
    state: PlayersUiState,
    onOpenPlayer: (Long) -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.active, key = { it.playerId }) { row ->
            PlayerCard(
                row = row,
                onClick = { onOpenPlayer(row.playerId) },
                onRename = { onRename(row.playerId) },
                onDelete = { onDelete(row.playerId) },
                onRestore = { onRestore(row.playerId) },
            )
        }
        if (state.archived.isNotEmpty()) {
            item {
                SectionLabel(stringResource(R.string.players_section_hidden), modifier = Modifier.padding(top = 12.dp))
            }
            items(state.archived, key = { it.playerId }) { row ->
                PlayerCard(
                    row = row,
                    onClick = { onOpenPlayer(row.playerId) },
                    onRename = { onRename(row.playerId) },
                    onDelete = { onDelete(row.playerId) },
                    onRestore = { onRestore(row.playerId) },
                )
            }
        }
    }
}

/**
 * The lifetime figure sits on the same line as the name, because it is the one thing a host
 * actually looks a player up for. What it was built from — games, buy-ins, money in — reads
 * underneath in the quieter colour.
 */
@Composable
private fun PlayerCard(
    row: PlayerRow,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (row.isArchived) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            // The menu button is a 48dp touch target and taller than the two lines beside it,
            // so top-aligning the text would leave the slack below it and read as a heavier
            // bottom margin than the top one.
            verticalAlignment = Alignment.CenterVertically,
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
                        color = if (row.isArchived) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (row.netProfit != null) {
                        NetCashText(row.netProfit, style = PokerTheme.type.numericSmall)
                    } else {
                        Text(
                            stringResource(
                                if (row.hasPlayed) R.string.players_no_results_yet
                                else R.string.players_never_played,
                            ),
                            style = MaterialTheme.typography.labelSmall,
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
                        stringResource(
                            R.string.players_row_counts,
                            pluralStringResource(R.plurals.game_count, row.gamesPlayed, row.gamesPlayed),
                            pluralStringResource(R.plurals.buy_in_count, row.buyInCount, row.buyInCount),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CashAmountText(
                        row.totalPaidIn,
                        style = PokerTheme.type.numericCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (row.isArchived) {
                    Text(
                        stringResource(R.string.players_hidden_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (row.openGames > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.games_to_settle,
                            row.openGames,
                            row.openGames,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            RowMenu(
                row = row,
                onRename = onRename,
                onDelete = onDelete,
                onRestore = onRestore,
            )
        }
    }
}

/**
 * Renaming is safe and reversible; removing a player is neither, so it keeps the error colour
 * and its own confirmation behind this menu rather than sitting on the row.
 */
@Composable
private fun RowMenu(
    row: PlayerRow,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(MinTouchTarget),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.players_row_menu, row.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.players_rename)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            if (row.isArchived) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.players_restore)) },
                    leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onRestore()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (row.hasPlayed) R.string.players_remove_from_roster
                                else R.string.players_delete,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (row.hasPlayed) Icons.Filled.VisibilityOff else Icons.Filled.Delete,
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
}

@Composable
private fun AddPlayerDialog(
    name: String,
    error: UiText?,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.players_add_title)) },
        text = {
            PokerTextField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.players_name_field),
                maxLength = NameRules.MAX_PLAYER_LENGTH,
                error = error,
                forceShowError = error != null,
                imeAction = ImeAction.Done,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RenamePlayerDialog(
    editor: RenameEditor,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.players_rename_title, editor.originalName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PokerTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.players_name_field),
                    maxLength = NameRules.MAX_PLAYER_LENGTH,
                    error = editor.error,
                    forceShowError = editor.error != null,
                    imeAction = ImeAction.Done,
                )
                Text(
                    stringResource(R.string.players_rename_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = editor.canSave) {
                Text(stringResource(R.string.players_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Two different endings behind one menu entry. A player nobody has ever dealt to is simply gone;
 * one with games behind them is hidden, because their seats are what those settlements were
 * worked out from and deleting the row would rewrite results that have already been paid.
 */
@Composable
private fun DeletePlayerDialog(
    editor: DeleteEditor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                if (editor.hasHistory) {
                    stringResource(R.string.players_remove_title, editor.name)
                } else {
                    stringResource(R.string.players_delete_title, editor.name)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editor.hasHistory) {
                    Text(
                        stringResource(
                            R.string.players_remove_body,
                            editor.name,
                            pluralStringResource(
                                R.plurals.game_count,
                                editor.gamesPlayed,
                                editor.gamesPlayed,
                            ),
                        ),
                    )
                    Text(
                        stringResource(R.string.players_remove_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.players_delete_body))
                    Text(
                        stringResource(R.string.players_delete_irreversible),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (editor.hasHistory) R.string.players_remove_confirm
                        else R.string.players_delete_confirm,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.players_keep)) }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun playerRow(
    id: Long,
    name: String,
    games: Int,
    buyIns: Int,
    paidIn: Long,
    net: Long?,
    open: Int = 0,
    archived: Boolean = false,
) = PlayerRow(
    playerId = id,
    name = name,
    gamesPlayed = games,
    buyInCount = buyIns,
    totalPaidIn = Money(paidIn),
    netProfit = net?.let { Money(it) },
    openGames = open,
    isArchived = archived,
)

private fun populated() = PlayersUiState(
    isLoading = false,
    active = listOf(
        playerRow(1, "Boris", 14, 22, 44_000_000, 12_500_000),
        playerRow(2, "Ilja", 14, 19, 38_000_000, -6_250_000, open = 1),
        playerRow(3, "Marta", 9, 11, 22_000_000, 0),
        playerRow(4, "New guy", 0, 0, 0, null),
    ),
    archived = listOf(playerRow(5, "Old regular", 3, 4, 8_000_000, -1_500_000, archived = true)),
)

@Preview(name = "Players — populated", showBackground = true, heightDp = 700)
@Composable
private fun PlayersPopulatedPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlayerList(populated(), {}, {}, {}, {})
        }
    }
}

@Preview(name = "Players — empty", showBackground = true, heightDp = 400)
@Composable
private fun PlayersEmptyPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Centered { EmptyRoster() }
        }
    }
}

@Preview(name = "Players — rename", showBackground = true, heightDp = 480)
@Composable
private fun RenamePreview() {
    PokerTrackerTheme {
        RenamePlayerDialog(RenameEditor(1, "Boris", "Boris"), {}, {}, {})
    }
}

@Preview(name = "Players — remove with history", showBackground = true, heightDp = 480)
@Composable
private fun DeleteWithHistoryPreview() {
    PokerTrackerTheme {
        DeletePlayerDialog(DeleteEditor(1, "Boris", 14), {}, {})
    }
}

@Preview(name = "Players — delete unplayed", showBackground = true, heightDp = 480)
@Composable
private fun DeleteUnplayedPreview() {
    PokerTrackerTheme {
        DeletePlayerDialog(DeleteEditor(4, "New guy", 0), {}, {})
    }
}

@Preview(name = "Players — 200% font", showBackground = true, fontScale = 2.0f, heightDp = 1000)
@Composable
private fun PlayersLargeFontPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlayerList(populated(), {}, {}, {}, {})
        }
    }
}
