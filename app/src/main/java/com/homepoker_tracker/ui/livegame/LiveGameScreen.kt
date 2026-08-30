package com.homepoker_tracker.ui.livegame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.ui.common.AmountPreview
import com.homepoker_tracker.ui.common.FormTextField
import com.homepoker_tracker.ui.common.MoneyField
import com.homepoker_tracker.ui.common.WholeNumberField

@Composable
fun LiveGameScreen(
    onBack: () -> Unit,
    onEndGame: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LiveGameEvent.EndGame -> onEndGame(event.gameId)
                is LiveGameEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.gameName.ifEmpty { "Game" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.canEndGame) {
                        TextButton(onClick = viewModel::onEndGame) { Text("End game") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isFinished && !state.isMissing) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::onAddPlayer,
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    text = { Text("Add player") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.isMissing -> MissingState(Modifier.padding(padding))
            else -> LiveGameContent(
                state = state,
                onAddBuyIn = viewModel::onAddBuyIn,
                onCashOut = viewModel::onCashOut,
                onUndoCashOut = viewModel::onUndoCashOut,
                modifier = Modifier.padding(padding),
            )
        }
    }

    when (val dialog = state.dialog) {
        is LiveGameDialog.AddBuyIn -> AddBuyInDialog(
            dialog = dialog,
            onAmountChange = viewModel::onBuyInAmountChange,
            onConfirm = viewModel::onConfirmBuyIn,
            onDismiss = viewModel::onDismissDialog,
        )

        is LiveGameDialog.CashOut -> CashOutDialog(
            dialog = dialog,
            onChipsChange = viewModel::onChipCountChange,
            onConfirm = viewModel::onConfirmCashOut,
            onDismiss = viewModel::onDismissDialog,
        )

        is LiveGameDialog.AddPlayer -> AddPlayerDialog(
            dialog = dialog,
            onSelectCandidate = viewModel::onSelectCandidate,
            onNewNameChange = viewModel::onNewPlayerNameChange,
            onBuyInChange = viewModel::onAddPlayerBuyInChange,
            onConfirm = viewModel::onConfirmAddPlayer,
            onDismiss = viewModel::onDismissDialog,
        )

        null -> Unit
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

@Composable
private fun MissingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text("This game is no longer available.") }
}

@Composable
private fun LiveGameContent(
    state: LiveGameUiState,
    onAddBuyIn: (Long) -> Unit,
    onCashOut: (Long) -> Unit,
    onUndoCashOut: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeadlineCard(state) }

        if (state.activeSeats.isNotEmpty()) {
            item { SectionHeader("At the table (${state.activeSeats.size})") }
            items(state.activeSeats, key = { it.seatId }) { seat ->
                ActiveSeatCard(
                    seat = seat,
                    enabled = !state.isFinished,
                    onAddBuyIn = { onAddBuyIn(seat.seatId) },
                    onCashOut = { onCashOut(seat.seatId) },
                )
            }
        }

        if (state.cashedOutSeats.isNotEmpty()) {
            item { SectionHeader("Cashed out (${state.cashedOutSeats.size})") }
            items(state.cashedOutSeats, key = { it.seatId }) { seat ->
                CashedOutSeatCard(
                    seat = seat,
                    enabled = !state.isFinished,
                    onUndo = { onUndoCashOut(seat.seatId) },
                )
            }
        }

        if (state.playerCount == 0) {
            item { Text("Nobody is seated yet.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun HeadlineCard(state: LiveGameUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("On the table", style = MaterialTheme.typography.labelLarge)
            Text(
                state.totalOnTable.cash?.format() ?: "0.00",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            state.totalOnTable.chips?.let {
                Text("$it chips", style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Statistic("Elapsed", state.elapsed)
                Statistic("Buy-ins", state.buyInCount.toString())
                Statistic("Stakes", state.stakes)
            }
            Text(
                state.chipValueLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (state.isFinished) {
                Text(
                    "This game has finished.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Statistic(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ActiveSeatCard(
    seat: SeatRow,
    enabled: Boolean,
    onAddBuyIn: () -> Unit,
    onCashOut: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(seat.name, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append("In for ${seat.totalBuyIn.format()}")
                    seat.buyInChips?.let { append(" · $it chips") }
                    append(" · ${seat.buyInCount} buy-in${if (seat.buyInCount == 1) "" else "s"}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddBuyIn) { Text("Rebuy") }
                    OutlinedButton(onClick = onCashOut) { Text("Cash out") }
                }
            }
        }
    }
}

@Composable
private fun CashedOutSeatCard(seat: SeatRow, enabled: Boolean, onUndo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                Text(seat.name, style = MaterialTheme.typography.titleMedium)
                seat.net?.let { NetText(it) }
            }
            Text(
                buildString {
                    append("In for ${seat.totalBuyIn.format()}")
                    seat.finalChips?.let { append(" · out with $it chips") }
                    seat.cashOutValue?.let { append(" (${it.format()})") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (enabled) {
                TextButton(onClick = onUndo) { Text("Undo cash-out") }
            }
        }
    }
}

@Composable
private fun NetText(net: Money) {
    Text(
        net.formatSigned(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = when {
            net.isPositive -> MaterialTheme.colorScheme.primary
            net.isNegative -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun AmountPreviewText(preview: AmountPreview) {
    val cash = preview.cash ?: return
    Text(
        buildString {
            append(cash.format())
            preview.chips?.let { append("  ·  $it chips") }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AddBuyInDialog(
    dialog: LiveGameDialog.AddBuyIn,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rebuy for ${dialog.playerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyField(
                    value = dialog.amount,
                    onValueChange = onAmountChange,
                    label = "Amount",
                    error = dialog.error,
                    imeAction = ImeAction.Done,
                )
                AmountPreviewText(dialog.preview)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = dialog.canConfirm) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CashOutDialog(
    dialog: LiveGameDialog.CashOut,
    onChipsChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cash out ${dialog.playerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Count the chips in front of them.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                WholeNumberField(
                    value = dialog.chips,
                    onValueChange = onChipsChange,
                    label = "Final chip count",
                    error = dialog.error,
                    imeAction = ImeAction.Done,
                )
                dialog.cashValue?.let { cash ->
                    Text(
                        "Worth ${cash.format()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("In for ${dialog.totalBuyIn.format()}", style = MaterialTheme.typography.bodySmall)
                    dialog.net?.let { NetText(it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = dialog.canConfirm) { Text("Cash out") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddPlayerDialog(
    dialog: LiveGameDialog.AddPlayer,
    onSelectCandidate: (Long) -> Unit,
    onNewNameChange: (String) -> Unit,
    onBuyInChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dialog.candidates.isNotEmpty()) {
                    Text("From the roster", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dialog.candidates.forEach { candidate ->
                            FilterChip(
                                selected = candidate.id == dialog.selectedPlayerId,
                                onClick = { onSelectCandidate(candidate.id) },
                                label = { Text(candidate.name) },
                            )
                        }
                    }
                }
                FormTextField(
                    value = dialog.newPlayerName,
                    onValueChange = onNewNameChange,
                    label = "Or add someone new",
                )
                MoneyField(
                    value = dialog.buyIn,
                    onValueChange = onBuyInChange,
                    label = "Buy-in",
                    error = dialog.error,
                    imeAction = ImeAction.Done,
                )
                AmountPreviewText(dialog.preview)
                if (!dialog.hasSubject) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Pick someone or type a name") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = dialog.canConfirm) { Text("Seat them") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
