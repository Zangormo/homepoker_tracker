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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.NameRules
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
import com.zango.pokertracker.ui.common.resolve
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

    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LiveGameEvent.EndGame -> onEndGame(event.gameId)
                is LiveGameEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(context))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.gameName.ifEmpty { stringResource(R.string.live_title_fallback) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.canEndGame) {
                        TextButton(onClick = viewModel::onEndGame) {
                            Text(stringResource(R.string.live_end_game))
                        }
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
                    text = { Text(stringResource(R.string.live_add_player)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isMissing -> Centered(Modifier.padding(padding)) {
                Text(stringResource(R.string.live_game_missing))
            }

            else -> LiveGameContent(
                state = state,
                onAddBuyIn = viewModel::onAddBuyIn,
                onReturnChips = viewModel::onReturnChips,
                onUndoLastReturn = viewModel::onUndoLastReturn,
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

        is LiveGameDialog.ReturnChips -> ReturnChipsDialog(
            dialog = dialog,
            onChipsChange = viewModel::onReturnChipsChange,
            onConfirm = viewModel::onConfirmReturnChips,
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
    onReturnChips: (Long) -> Unit,
    onUndoLastReturn: (Long) -> Unit,
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
            item {
                SectionLabel(
                    stringResource(R.string.live_section_at_table, state.activeSeats.size),
                )
            }
            items(state.activeSeats, key = { it.seatId }) { seat ->
                ActiveSeatRow(
                    seat = seat,
                    enabled = !state.isFinished,
                    onAddBuyIn = { onAddBuyIn(seat.seatId) },
                    onReturnChips = { onReturnChips(seat.seatId) },
                    onUndoLastReturn = { onUndoLastReturn(seat.seatId) },
                    onCashOut = { onCashOut(seat.seatId) },
                )
            }
        }

        if (state.cashedOutSeats.isNotEmpty()) {
            item {
                SectionLabel(
                    stringResource(R.string.live_section_cashed_out, state.cashedOutSeats.size),
                )
            }
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
                    stringResource(R.string.live_nobody_seated),
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
            SectionLabel(stringResource(R.string.live_section_on_the_table))
            CashAmountText(
                money = state.totalOnTable.cash ?: Money.ZERO,
                style = PokerTheme.type.numericHero,
            )
            ChipAmountText(
                chips = state.totalOnTable.chips,
                style = PokerTheme.type.numericMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.hasReturns) {
                Text(
                    pluralStringResource(
                        R.plurals.live_returned_summary,
                        state.returnedChips.count.toInt(),
                        state.returnedChips.count,
                        state.returnedCash.format(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = PokerTheme.colors.divider,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Statistic(stringResource(R.string.live_stat_elapsed), state.elapsed)
                Statistic(stringResource(R.string.live_stat_buy_ins), state.buyInCount.toString())
                Statistic(stringResource(R.string.live_stat_blinds), state.stakes)
            }

            Text(
                state.chipValueLabel?.resolve().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )

            if (state.isFinished) {
                Text(
                    stringResource(R.string.live_game_finished),
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
    onReturnChips: () -> Unit,
    onUndoLastReturn: () -> Unit,
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
                    pluralStringResource(R.plurals.buy_in_count, seat.buyInCount, seat.buyInCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CashToChipsRow(
                cash = seat.totalBuyIn,
                chips = seat.buyInChips,
                style = PokerTheme.type.numericMedium,
            )
            if (seat.hasReturns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel(stringResource(R.string.live_seat_sold_back))
                        ChipsToCashRow(
                            chips = seat.returnedChips,
                            cash = seat.returnedCash,
                            style = PokerTheme.type.numericSmall,
                        )
                    }
                    if (enabled) {
                        TextButton(onClick = onUndoLastReturn) {
                            Text(stringResource(R.string.live_action_undo_last))
                        }
                    }
                }
            }
            if (enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAddBuyIn,
                        modifier = Modifier
                            .weight(1f)
                            .height(MinTouchTarget),
                    ) { Text(stringResource(R.string.live_action_rebuy)) }
                    OutlinedButton(
                        onClick = onCashOut,
                        modifier = Modifier
                            .weight(1f)
                            .height(MinTouchTarget),
                    ) { Text(stringResource(R.string.live_action_cash_out)) }
                }
                TextButton(
                    onClick = onReturnChips,
                    modifier = Modifier.height(MinTouchTarget),
                ) { Text(stringResource(R.string.live_action_sell_back)) }
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
                    SectionLabel(stringResource(R.string.live_seat_in_for))
                    CashAmountText(seat.totalBuyIn, style = PokerTheme.type.numericSmall)
                }
                Column {
                    SectionLabel(stringResource(R.string.live_seat_out_with))
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
                ) { Text(stringResource(R.string.live_action_undo_cash_out)) }
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
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
        title = stringResource(R.string.live_rebuy_title, dialog.playerName),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.live_rebuy_confirm),
        confirmEnabled = dialog.canConfirm,
        onConfirm = onConfirm,
    ) {
        CashAmountField(
            value = dialog.amount,
            onValueChange = onAmountChange,
            label = stringResource(R.string.live_rebuy_amount),
            required = true,
            error = dialog.error,
            forceShowError = true,
            imeAction = ImeAction.Done,
        )
        CashToChipsRow(cash = dialog.preview.cash, chips = dialog.preview.chips)
    }
}

/**
 * For when the physical chips run out: a player hands some back and takes the cash, so the next
 * buy-in can be paid out in them. They keep their seat, and the cash counts towards their result.
 */
@Composable
private fun ReturnChipsDialog(
    dialog: LiveGameDialog.ReturnChips,
    onChipsChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PokerDialog(
        title = stringResource(R.string.live_return_title, dialog.playerName),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.live_return_confirm),
        confirmEnabled = dialog.canConfirm,
        onConfirm = onConfirm,
    ) {
        Text(
            stringResource(R.string.live_return_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipAmountField(
            value = dialog.chips,
            onValueChange = onChipsChange,
            label = stringResource(R.string.live_return_field),
            required = true,
            error = dialog.error,
            forceShowError = true,
            supporting = dialog.chipsOnTable?.let {
                UiText.plural(R.plurals.live_return_on_table, it.count.toInt(), it.count)
            },
            imeAction = ImeAction.Done,
        )
        HorizontalDivider(color = PokerTheme.colors.divider)
        ChipsToCashRow(
            chips = dialog.chipCount,
            cash = dialog.cashValue,
            style = PokerTheme.type.numericLarge,
        )
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
        title = stringResource(R.string.live_cash_out_title, dialog.playerName),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.live_cash_out_confirm),
        confirmEnabled = dialog.canConfirm,
        onConfirm = onConfirm,
    ) {
        Text(
            stringResource(R.string.live_cash_out_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipAmountField(
            value = dialog.chips,
            onValueChange = onChipsChange,
            label = stringResource(R.string.live_cash_out_field),
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
                SectionLabel(stringResource(R.string.live_seat_in_for))
                CashAmountText(dialog.totalBuyIn, style = PokerTheme.type.numericSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                SectionLabel(stringResource(R.string.live_seat_net))
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
        title = stringResource(R.string.live_add_player_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.live_add_player_confirm),
        confirmEnabled = dialog.canConfirm,
        onConfirm = onConfirm,
    ) {
        if (dialog.candidates.isNotEmpty()) {
            SectionLabel(stringResource(R.string.live_add_player_from_roster))
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
            label = stringResource(R.string.live_add_player_new_name),
            maxLength = NameRules.MAX_PLAYER_LENGTH,
            error = dialog.nameError,
            forceShowError = true,
        )
        CashAmountField(
            value = dialog.buyIn,
            onValueChange = onBuyInChange,
            label = stringResource(R.string.live_add_player_buy_in),
            required = true,
            error = dialog.error,
            forceShowError = true,
            imeAction = ImeAction.Done,
        )
        CashToChipsRow(cash = dialog.preview.cash, chips = dialog.preview.chips)
        if (!dialog.hasSubject) {
            Text(
                stringResource(R.string.live_add_player_hint),
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
    chipValueLabel = UiText.Raw("1 chip = 0.005"),
    elapsed = "2h 47m",
    totalOnTable = AmountPreview.of(Money(4_000_000), PreviewRate),
    buyInCount = 5,
    defaultBuyIn = Money(1_000_000),
    activeSeats = listOf(
        seat(1, "Anna", 2_000_000, buyIns = 2).copy(
            returnedChips = Chips(200),
            returnedCash = PreviewRate.cashFor(Chips(200)),
            lastReturnId = 9,
        ),
        seat(2, "Boris", 1_000_000),
    ),
    returnedChips = Chips(200),
    returnedCash = PreviewRate.cashFor(Chips(200)),
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
            LiveGameContent(liveState(), {}, {}, {}, {}, {})
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
                error = UiText.Raw("Chips come in whole numbers only"),
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

@Preview(name = "Live game — selling chips back", showBackground = true, heightDp = 620)
@Composable
private fun ReturnChipsPreview() {
    PokerTrackerTheme {
        ReturnChipsDialog(
            dialog = LiveGameDialog.ReturnChips(
                seatId = 1,
                playerName = "Anna",
                chips = "200",
                chipsOnTable = Chips(800),
                chipCount = Chips(200),
                cashValue = PreviewRate.cashFor(Chips(200)),
            ),
            onChipsChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Live game — selling back more than exists", showBackground = true, heightDp = 620)
@Composable
private fun ReturnChipsErrorPreview() {
    PokerTrackerTheme {
        ReturnChipsDialog(
            dialog = LiveGameDialog.ReturnChips(
                seatId = 1,
                playerName = "Anna",
                chips = "900",
                chipsOnTable = Chips(800),
                error = UiText.Raw("Only 800 chips are on the table"),
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
            LiveGameContent(liveState(), {}, {}, {}, {}, {})
        }
    }
}
