package com.homepoker_tracker.ui.creategame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homepoker_tracker.ui.common.FormTextField
import com.homepoker_tracker.ui.common.AmountPreview
import com.homepoker_tracker.ui.common.MoneyField
import com.homepoker_tracker.ui.common.SegmentedChoice
import com.homepoker_tracker.ui.common.WholeNumberField

@Composable
fun CreateGameScreen(
    onBack: () -> Unit,
    onGameStarted: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateGameEvent.GameStarted -> onGameStarted(event.gameId)
                is CreateGameEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val actions = rememberCreateGameActions(viewModel)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("New game") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        CreateGameContent(
            state = state,
            actions = actions,
            modifier = Modifier.padding(padding),
        )
    }

    state.overrideEditor?.let { editor ->
        OverrideDialog(editor = editor, actions = actions)
    }
}

/** Every callback the screen needs, so the content is previewable without a ViewModel. */
data class CreateGameActions(
    val onNameChange: (String) -> Unit,
    val onSmallBlindChange: (String) -> Unit,
    val onBigBlindChange: (String) -> Unit,
    val onDeriveChipValueChange: (Boolean) -> Unit,
    val onChipValueChange: (String) -> Unit,
    val onChipsPerBigBlindChange: (String) -> Unit,
    val onBuyInModeChange: (BuyInMode) -> Unit,
    val onBuyInBigBlindsChange: (String) -> Unit,
    val onBuyInCashChange: (String) -> Unit,
    val onPayoutRoundingChange: (String) -> Unit,
    val onTogglePlayer: (Long) -> Unit,
    val onEditOverride: (Long) -> Unit,
    val onClearOverride: (Long) -> Unit,
    val onNewPlayerNameChange: (String) -> Unit,
    val onAddNewPlayer: () -> Unit,
    val onOverrideModeChange: (BuyInMode) -> Unit,
    val onOverrideBigBlindsChange: (String) -> Unit,
    val onOverrideCashChange: (String) -> Unit,
    val onApplyOverride: () -> Unit,
    val onDismissOverride: () -> Unit,
    val onStartGame: () -> Unit,
)

@Composable
private fun rememberCreateGameActions(viewModel: CreateGameViewModel): CreateGameActions =
    remember(viewModel) {
        CreateGameActions(
            onNameChange = viewModel::onNameChange,
            onSmallBlindChange = viewModel::onSmallBlindChange,
            onBigBlindChange = viewModel::onBigBlindChange,
            onDeriveChipValueChange = viewModel::onDeriveChipValueChange,
            onChipValueChange = viewModel::onChipValueChange,
            onChipsPerBigBlindChange = viewModel::onChipsPerBigBlindChange,
            onBuyInModeChange = viewModel::onBuyInModeChange,
            onBuyInBigBlindsChange = viewModel::onBuyInBigBlindsChange,
            onBuyInCashChange = viewModel::onBuyInCashChange,
            onPayoutRoundingChange = viewModel::onPayoutRoundingChange,
            onTogglePlayer = viewModel::onTogglePlayer,
            onEditOverride = viewModel::onEditOverride,
            onClearOverride = viewModel::onClearOverride,
            onNewPlayerNameChange = viewModel::onNewPlayerNameChange,
            onAddNewPlayer = viewModel::onAddNewPlayer,
            onOverrideModeChange = viewModel::onOverrideModeChange,
            onOverrideBigBlindsChange = viewModel::onOverrideBigBlindsChange,
            onOverrideCashChange = viewModel::onOverrideCashChange,
            onApplyOverride = viewModel::onApplyOverride,
            onDismissOverride = viewModel::onDismissOverride,
            onStartGame = viewModel::onStartGame,
        )
    }

@Composable
private fun CreateGameContent(
    state: CreateGameUiState,
    actions: CreateGameActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { GameDetailsCard(state, actions) }
        item { ChipValueCard(state, actions) }
        item { BuyInCard(state, actions) }
        item { PlayersHeader(state, actions) }
        items(state.roster, key = { it.player.id }) { row ->
            RosterItem(row = row, actions = actions)
        }
        if (!state.hasRoster) {
            item {
                Text(
                    "No players on the roster yet. Add the first one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { StartGameSection(state, actions) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun GameDetailsCard(state: CreateGameUiState, actions: CreateGameActions) {
    SectionCard("Game") {
        FormTextField(
            value = state.form.name,
            onValueChange = actions.onNameChange,
            label = "Game name",
            error = state.validation.nameError,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoneyField(
                value = state.form.smallBlind,
                onValueChange = actions.onSmallBlindChange,
                label = "Small blind",
                error = state.validation.smallBlindError,
                modifier = Modifier.weight(1f),
            )
            MoneyField(
                value = state.form.bigBlind,
                onValueChange = actions.onBigBlindChange,
                label = "Big blind",
                error = state.validation.bigBlindError,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChipValueCard(state: CreateGameUiState, actions: CreateGameActions) {
    SectionCard("Chips") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Work it out from the chip markings", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Chips marked 1/2 on a 0.005/0.01 table make a chip worth 0.005.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.form.deriveChipValue,
                onCheckedChange = actions.onDeriveChipValueChange,
            )
        }

        if (state.form.deriveChipValue) {
            WholeNumberField(
                value = state.form.chipsPerBigBlind,
                onValueChange = actions.onChipsPerBigBlindChange,
                label = "Big blind, in chips",
                error = state.validation.chipValueError,
                supporting = state.derivedChipValue?.let { "One chip is worth ${it.format()}" },
            )
        } else {
            MoneyField(
                value = state.form.chipValue,
                onValueChange = actions.onChipValueChange,
                label = "Cash value of one chip",
                error = state.validation.chipValueError,
            )
        }
    }
}

@Composable
private fun BuyInCard(state: CreateGameUiState, actions: CreateGameActions) {
    SectionCard("Default buy-in") {
        SegmentedChoice(
            options = listOf(BuyInMode.BIG_BLINDS, BuyInMode.CASH),
            selected = state.form.buyInMode,
            onSelect = actions.onBuyInModeChange,
            label = { if (it == BuyInMode.BIG_BLINDS) "Big blinds" else "Cash" },
            modifier = Modifier.fillMaxWidth(),
        )

        when (state.form.buyInMode) {
            BuyInMode.BIG_BLINDS -> WholeNumberField(
                value = state.form.buyInBigBlinds,
                onValueChange = actions.onBuyInBigBlindsChange,
                label = "Buy-in, in big blinds",
                error = state.validation.buyInError,
            )

            BuyInMode.CASH -> MoneyField(
                value = state.form.buyInCash,
                onValueChange = actions.onBuyInCashChange,
                label = "Buy-in",
                error = state.validation.buyInError,
            )
        }

        AmountPreviewText(state.defaultBuyInPreview)

        MoneyField(
            value = state.form.payoutRounding,
            onValueChange = actions.onPayoutRoundingChange,
            label = "Round payouts to",
            error = state.validation.payoutRoundingError,
            supporting = "The smallest note or coin players will actually hand each other.",
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun AmountPreviewText(preview: AmountPreview, modifier: Modifier = Modifier) {
    val cash = preview.cash ?: return
    val chips = preview.chips
    Text(
        buildString {
            append(cash.format())
            if (chips != null) append("  ·  $chips chips")
            preview.leftOver?.let { append("  ·  ${it.format()} will not fit into whole chips") }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (preview.leftOver != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

@Composable
private fun PlayersHeader(state: CreateGameUiState, actions: CreateGameActions) {
    SectionCard("Who is playing") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormTextField(
                value = state.newPlayerName,
                onValueChange = actions.onNewPlayerNameChange,
                label = "Add a new player",
                error = state.newPlayerError,
                imeAction = ImeAction.Done,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = actions.onAddNewPlayer) {
                Icon(Icons.Default.Add, contentDescription = "Add player")
            }
        }
        state.validation.playersError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "${state.selectedCount} selected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RosterItem(row: RosterRow, actions: CreateGameActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(row.player.name, fontWeight = if (row.isSelected) FontWeight.SemiBold else null)
            },
            supportingContent = {
                when {
                    row.error != null -> Text(row.error, color = MaterialTheme.colorScheme.error)
                    row.isSelected && row.buyIn != null -> Text(
                        buildString {
                            append("In for ${row.buyIn.format()}")
                            row.chips?.let { append(" · $it chips") }
                        },
                    )

                    row.isSelected -> Text("Finish the game settings to see the buy-in")
                    else -> Unit
                }
            },
            leadingContent = {
                Checkbox(
                    checked = row.isSelected,
                    onCheckedChange = { actions.onTogglePlayer(row.player.id) },
                )
            },
            trailingContent = {
                if (row.isSelected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (row.isOverridden) {
                            AssistChip(
                                onClick = { actions.onClearOverride(row.player.id) },
                                label = { Text("Override") },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = { actions.onEditOverride(row.player.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Change buy-in")
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StartGameSection(state: CreateGameUiState, actions: CreateGameActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.totalOnTable.isEmpty) {
            Text("On the table at kick-off", style = MaterialTheme.typography.titleSmall)
            AmountPreviewText(state.totalOnTable)
        }
        Button(
            onClick = actions.onStartGame,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isStarting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Start game")
            }
        }
    }
}

@Composable
private fun OverrideDialog(editor: OverrideEditor, actions: CreateGameActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissOverride,
        title = { Text("Buy-in for ${editor.playerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedChoice(
                    options = listOf(BuyInMode.BIG_BLINDS, BuyInMode.CASH),
                    selected = editor.mode,
                    onSelect = actions.onOverrideModeChange,
                    label = { if (it == BuyInMode.BIG_BLINDS) "Big blinds" else "Cash" },
                    modifier = Modifier.fillMaxWidth(),
                )
                when (editor.mode) {
                    BuyInMode.BIG_BLINDS -> WholeNumberField(
                        value = editor.bigBlinds,
                        onValueChange = actions.onOverrideBigBlindsChange,
                        label = "Big blinds",
                        error = editor.error,
                    )

                    BuyInMode.CASH -> MoneyField(
                        value = editor.cash,
                        onValueChange = actions.onOverrideCashChange,
                        label = "Cash",
                        error = editor.error,
                        imeAction = ImeAction.Done,
                    )
                }
                AmountPreviewText(editor.preview)
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onApplyOverride, enabled = editor.canApply) {
                Text("Set")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { actions.onClearOverride(editor.playerId) }) {
                    Text("Use default")
                }
                TextButton(onClick = actions.onDismissOverride) { Text("Cancel") }
            }
        },
    )
}
