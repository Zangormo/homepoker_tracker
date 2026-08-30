package com.zango.pokertracker.ui.livegame

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.ui.common.AmountPreview
import com.zango.pokertracker.ui.common.CashAmountField
import com.zango.pokertracker.ui.common.CashAmountText
import com.zango.pokertracker.ui.common.CashToChipsRow
import com.zango.pokertracker.ui.common.ChipAmountField
import com.zango.pokertracker.ui.common.ChipAmountText
import com.zango.pokertracker.ui.common.ChipsToCashRow
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.NetCashText
import com.zango.pokertracker.ui.common.PokerTextField
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (!state.isFinished && !state.isMissing) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::onAddPlayer,
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text("Add player") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isMissing -> Centered(Modifier.padding(padding)) {
                Text("This game is no longer available.")
            }

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
private fun LiveGameContent(
    state: LiveGameUiState,
    onAddBuyIn: (Long) -> Unit,
    onCashOut: (Long) -> Unit,
    onUndoCashOut: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HeadlinePanel(state) }

        if (state.activeSeats.isNotEmpty()) {
            item { SectionLabel("At the table · ${state.activeSeats.size}") }
            items(state.activeSeats, key = { it.seatId }) { seat ->
                ActiveSeatRow(
                    seat = seat,
                    enabled = !state.isFinished,
                    onAddBuyIn = { onAddBuyIn(seat.seatId) },
                    onCashOut = { onCashOut(seat.seatId) },
                )
            }
        }

        if (state.cashedOutSeats.isNotEmpty()) {
            item { SectionLabel("Cashed out · ${state.cashedOutSeats.size}") }
            items(state.cashedOutSeats, key = { it.seatId }) { seat ->
                CashedOutSeatRow(
                    seat = seat,
                    enabled = !state.isFinished,
                    onUndo = { onUndoCashOut(seat.seatId) },
                )
            }
        }

        if (state.playerCount == 0) {
            item {
                Text(
                    "Nobody is seated yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The three figures the host is asked for all night: what is on the table, how long they have
 * been playing, and how many buy-ins have gone in. The money is the largest thing on screen.
 */
@Composable
private fun HeadlinePanel(state: LiveGameUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel("On the table")
            CashAmountText(
                money = state.totalOnTable.cash ?: Money.ZERO,
                style = PokerTheme.type.numericHero,
            )
            ChipAmountText(
                chips = state.totalOnTable.chips,
                style = PokerTheme.type.numericMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = PokerTheme.colors.divider,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Statistic("Elapsed", state.elapsed)
                Statistic("Buy-ins", state.buyInCount.toString())
                Statistic("Blinds", state.stakes)
            }

            Text(
                state.chipValueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )

            if (state.isFinished) {
                Text(
                    "This game has finished.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Statistic(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(label)
        // Monospaced and tabular, so a ticking timer does not shuffle the row beside it.
        Text(value, style = PokerTheme.type.numericLarge)
    }
}

@Composable
private fun ActiveSeatRow(
    seat: SeatRow,
    enabled: Boolean,
    onAddBuyIn: () -> Unit,
    onCashOut: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(seat.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${seat.buyInCount} buy-in${if (seat.buyInCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CashToChipsRow(
                cash = seat.totalBuyIn,
                chips = seat.buyInChips,
                style = PokerTheme.type.numericMedium,
            )
            if (enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAddBuyIn,
                        modifier = Modifier
                            .weight(1f)
                            .height(MinTouchTarget),
                    ) { Text("Rebuy") }
                    OutlinedButton(
                        onClick = onCashOut,
                        modifier = Modifier
                            .weight(1f)
                            .height(MinTouchTarget),
                    ) { Text("Cash out") }
                }
            }
        }
    }
}

/**
 * A player who has left the table. The row sits flush with the page instead of on a raised
 * surface, so it recedes without any of its text losing contrast.
 */
@Composable
private fun CashedOutSeatRow(seat: SeatRow, enabled: Boolean, onUndo: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, PokerTheme.colors.divider),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(seat.name, style = MaterialTheme.typography.titleMedium)
                NetCashText(seat.net, style = PokerTheme.type.numericLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    SectionLabel("In for")
                    CashAmountText(seat.totalBuyIn, style = PokerTheme.type.numericSmall)
                }
                Column {
                    SectionLabel("Out with")
                    ChipsToCashRow(
                        chips = seat.finalChips,
                        cash = seat.cashOutValue,
                        style = PokerTheme.type.numericSmall,
                    )
                }
            }
            if (enabled) {
                TextButton(
                    onClick = onUndo,
                    modifier = Modifier.height(MinTouchTarget),
                ) { Text("Undo cash-out") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

@Composable
private fun PokerDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    extraDismiss: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                extraDismiss?.invoke()
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun AddBuyInDialog(
    dialog: LiveGameDialog.AddBuyIn,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PokerDialog(
        title = "Rebuy for ${dialog.playerName}",
        onDismiss = onDismiss,
        confirmLabel = "Add",
        confirmEnabled = dialog.canConfirm,
        onConfirm = onConfirm,
    ) {
        CashAmountField(
            value = dialog.amount,
            onValueChange = onAmountChange,
            label = "Amount",
            required = true,
            error = dialog.error,
            forceShowError = true,
            imeAction = ImeAction.Done,
        )
        CashToChipsRow(cash = dialog.preview.cash, chips = dialog.preview.chips)
    }
}

@Composable
private fun CashOutDialog(
    dialog: LiveGameDialog.CashOut,
    onChipsChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PokerDialog(
        title = "Cash out ${dialog.playerName}",
        onDismiss = onDismiss,
        confirmLabel = "Cash out",
        confirmEnabled = dialog.canConfirm,
        onConfirm = onConfirm,
    ) {
        Text(
            "Count the chips in front of them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipAmountField(
            value = dialog.chips,
            onValueChange = onChipsChange,
            label = "Final chip count",
            required = true,
            error = dialog.error,
            forceShowError = true,
            imeAction = ImeAction.Done,
        )
        HorizontalDivider(color = PokerTheme.colors.divider)
        ChipsToCashRow(
            chips = dialog.chipCount,
            cash = dialog.cashValue,
            style = PokerTheme.type.numericLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionLabel("In for")
                CashAmountText(dialog.totalBuyIn, style = PokerTheme.type.numericSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                SectionLabel("Net")
                NetCashText(dialog.net, style = PokerTheme.type.numericMedium)
            }
        }
    }
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
    PokerDialog(
        title = "Add a player",
        onDismiss = onDismiss,
        confirmLabel = "Seat them",
        confirmEnabled = dialog.canConfirm,
        onConfirm = onConfirm,
    ) {
        if (dialog.candidates.isNotEmpty()) {
            SectionLabel("From the roster")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                dialog.candidates.forEach { candidate ->
                    FilterChip(
                        selected = candidate.id == dialog.selectedPlayerId,
                        onClick = { onSelectCandidate(candidate.id) },
                        label = { Text(candidate.name) },
                        modifier = Modifier.height(MinTouchTarget),
                    )
                }
            }
        }
        PokerTextField(
            value = dialog.newPlayerName,
            onValueChange = onNewNameChange,
            label = "Or add someone new",
        )
        CashAmountField(
            value = dialog.buyIn,
            onValueChange = onBuyInChange,
            label = "Buy-in",
            required = true,
            error = dialog.error,
            forceShowError = true,
            imeAction = ImeAction.Done,
        )
        CashToChipsRow(cash = dialog.preview.cash, chips = dialog.preview.chips)
        if (!dialog.hasSubject) {
            Text(
                "Pick someone from the roster or type a name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val PreviewRate = ChipRate(5_000)

private fun seat(
    id: Long,
    name: String,
    buyIn: Long,
    buyIns: Int = 1,
    finalChips: Long? = null,
) = SeatRow(
    seatId = id,
    playerId = id,
    name = name,
    totalBuyIn = Money(buyIn),
    buyInChips = PreviewRate.chipsFor(Money(buyIn)).exactOrNull(),
    buyInCount = buyIns,
    isCashedOut = finalChips != null,
    finalChips = finalChips?.let { Chips(it) },
    cashOutValue = finalChips?.let { PreviewRate.cashFor(Chips(it)) },
    net = finalChips?.let { PreviewRate.cashFor(Chips(it)) - Money(buyIn) },
)

private fun liveState() = LiveGameUiState(
    isLoading = false,
    gameId = 1,
    gameName = "Thursday",
    stakes = "0.005 / 0.01",
    chipValueLabel = "1 chip = 0.005",
    elapsed = "2h 47m",
    totalOnTable = AmountPreview.of(Money(4_000_000), PreviewRate),
    buyInCount = 5,
    defaultBuyIn = Money(1_000_000),
    activeSeats = listOf(
        seat(1, "Anna", 2_000_000, buyIns = 2),
        seat(2, "Boris", 1_000_000),
    ),
    cashedOutSeats = listOf(
        seat(3, "Chris", 1_000_000, finalChips = 340),
        seat(4, "Dina", 1_000_000, finalChips = 60),
    ),
)

@Preview(name = "Live game — running", showBackground = true, heightDp = 1100)
@Composable
private fun LiveGameRunningPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LiveGameContent(liveState(), {}, {}, {})
        }
    }
}

@Preview(name = "Live game — cash-out with a bad count", showBackground = true, heightDp = 620)
@Composable
private fun CashOutErrorPreview() {
    PokerTrackerTheme {
        CashOutDialog(
            dialog = LiveGameDialog.CashOut(
                seatId = 1,
                playerName = "Anna",
                totalBuyIn = Money(2_000_000),
                chips = "250.5",
                cashValue = null,
                net = null,
                error = "Chips come in whole numbers only",
            ),
            onChipsChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Live game — cash-out confirmed", showBackground = true, heightDp = 620)
@Composable
private fun CashOutFilledPreview() {
    PokerTrackerTheme {
        CashOutDialog(
            dialog = LiveGameDialog.CashOut(
                seatId = 1,
                playerName = "Anna",
                totalBuyIn = Money(2_000_000),
                chips = "500",
                cashValue = Money(2_500_000),
                net = Money(500_000),
            ),
            onChipsChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Live game — add player", showBackground = true, heightDp = 700)
@Composable
private fun AddPlayerPreview() {
    PokerTrackerTheme {
        AddPlayerDialog(
            dialog = LiveGameDialog.AddPlayer(
                candidates = listOf(
                    Player(5, "Erik", 0),
                    Player(6, "Farida", 0),
                    Player(7, "Gus", 0),
                ),
                selectedPlayerId = 6,
                newPlayerName = "",
                buyIn = "1.00",
                preview = AmountPreview.of(Money(1_000_000), PreviewRate),
            ),
            onSelectCandidate = {},
            onNewNameChange = {},
            onBuyInChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Live game — 200% font", showBackground = true, fontScale = 2.0f, heightDp = 1600)
@Composable
private fun LiveGameLargeFontPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LiveGameContent(liveState(), {}, {}, {})
        }
    }
}
