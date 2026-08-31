package com.zango.pokertracker.ui.endgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.zango.pokertracker.ui.common.CashAmountText
import com.zango.pokertracker.ui.common.ChipAmountField
import com.zango.pokertracker.ui.common.ChipAmountText
import com.zango.pokertracker.ui.common.ChipsToCashRow
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.NetCashText
import com.zango.pokertracker.ui.common.ResultRow
import com.zango.pokertracker.ui.common.ResultsTable
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.common.resolve
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme

@Composable
fun EndGameScreen(
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
    onViewSettlement: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EndGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EndGameEvent.Finished -> onFinished(event.gameId)
                is EndGameEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(context))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.end_title))
                        if (state.gameName.isNotEmpty()) {
                            Text(
                                state.gameName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (!state.isLoading && !state.isMissing) {
                FinishBar(
                    state = state,
                    onFinish = viewModel::onFinish,
                    onViewSettlement = { onViewSettlement(state.gameId) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isMissing -> Centered(Modifier.padding(padding)) {
                Text(stringResource(R.string.end_game_missing))
            }

            else -> EndGameContent(
                state = state,
                onCountChange = viewModel::onCountChange,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.isConfirmingMismatch) {
        MismatchDialog(
            summary = state.reconciliation,
            onFinishAnyway = viewModel::onConfirmMismatch,
            onGoBack = viewModel::onDismissMismatch,
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
private fun EndGameContent(
    state: EndGameUiState,
    onCountChange: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.reconciliation?.let { item { ReconciliationPanel(it) } }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel(stringResource(R.string.end_section_chip_counts))
                Text(
                    stringResource(
                        R.string.end_chip_counts_body,
                        state.chipValueLabel?.resolve().orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.counts, key = { it.seatId }) { row ->
            CountCard(
                row = row,
                enabled = !state.alreadyFinished,
                onCountChange = { text -> onCountChange(row.seatId, text) },
            )
        }

        item { SectionLabel(stringResource(R.string.end_section_results)) }
        item { ResultsTable(state.results) }
    }
}

/**
 * The one place in the app where a problem the host can proceed past still gets the full error
 * treatment. A discrepancy is stated in chips and in cash, because "12 chips" means nothing to a
 * player until it is also "0.06 of somebody's money".
 */
@Composable
private fun ReconciliationPanel(summary: ReconciliationSummary) {
    val container = when {
        summary.hasDiscrepancy -> MaterialTheme.colorScheme.errorContainer
        summary.addsUp -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val headlineColor = when {
        summary.hasDiscrepancy -> MaterialTheme.colorScheme.error
        summary.addsUp -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                summary.headline.resolve(),
                style = MaterialTheme.typography.titleMedium,
                color = headlineColor,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel(stringResource(R.string.end_bought_in))
                    ChipAmountText(summary.expectedChips, style = PokerTheme.type.numericMedium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel(stringResource(R.string.end_counted))
                    ChipAmountText(summary.countedChips, style = PokerTheme.type.numericMedium)
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SectionLabel(stringResource(R.string.end_difference))
                    if (summary.hasDiscrepancy) {
                        ChipsToCashRow(
                            chips = summary.differenceChips,
                            cash = summary.differenceCash,
                            style = PokerTheme.type.numericMedium,
                        )
                    } else {
                        ChipAmountText(Chips.ZERO, style = PokerTheme.type.numericMedium)
                    }
                }
            }

            if (summary.hasDiscrepancy) {
                Text(
                    stringResource(R.string.end_recount_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun CountCard(row: CountRow, enabled: Boolean, onCountChange: (String) -> Unit) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            if (row.wasCashedOut) R.string.end_seat_cashed_out
                            else R.string.end_seat_still_playing,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NetCashText(row.net, style = PokerTheme.type.numericLarge)
            }

            ChipAmountField(
                value = row.text,
                onValueChange = onCountChange,
                label = stringResource(R.string.end_final_chips),
                required = true,
                enabled = enabled,
                error = row.error,
                forceShowError = true,
                imeAction = ImeAction.Next,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel(stringResource(R.string.end_seat_in_for))
                    CashAmountText(row.totalBuyIn, style = PokerTheme.type.numericSmall)
                }
                if (!row.returnedChips.isZero) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SectionLabel(stringResource(R.string.end_seat_sold_back))
                        ChipsToCashRow(
                            chips = row.returnedChips,
                            cash = row.returnedCash,
                            style = PokerTheme.type.numericSmall,
                        )
                    }
                }
                if (row.chips != null) {
                    ChipsToCashRow(
                        chips = row.chips,
                        cash = row.cashOutValue,
                        style = PokerTheme.type.numericSmall,
                    )
                } else if (row.countedAsZero) {
                    Text(
                        stringResource(R.string.end_seat_empty_recorded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        stringResource(R.string.end_seat_not_counted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FinishBar(
    state: EndGameUiState,
    onFinish: () -> Unit,
    onViewSettlement: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.alreadyFinished) {
                Button(
                    onClick = onViewSettlement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MinTouchTarget),
                ) { Text(stringResource(R.string.end_view_settlement)) }
            } else {
                if (state.reconciliation?.isComplete == false) {
                    Text(
                        stringResource(R.string.end_blocked_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onFinish,
                    enabled = state.canFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MinTouchTarget),
                ) {
                    if (state.isFinishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.end_finish))
                    }
                }
            }
        }
    }
}

/**
 * Correcting the counts is the recommended way out, so it gets the filled button in the primary
 * position. Overriding stays available, as a plain text action.
 */
@Composable
private fun MismatchDialog(
    summary: ReconciliationSummary?,
    onFinishAnyway: () -> Unit,
    onGoBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onGoBack,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.end_mismatch_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    summary?.headline?.resolve().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (summary != null) {
                    HorizontalDivider(color = PokerTheme.colors.divider)
                    ChipsToCashRow(
                        chips = summary.differenceChips,
                        cash = summary.differenceCash,
                        style = PokerTheme.type.numericLarge,
                    )
                }
                Text(
                    stringResource(R.string.end_mismatch_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onGoBack) { Text(stringResource(R.string.end_mismatch_recount)) }
        },
        dismissButton = {
            TextButton(onClick = onFinishAnyway) {
                Text(stringResource(R.string.end_mismatch_finish_anyway))
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val PreviewRate = ChipRate(5_000)

private fun countRow(
    id: Long,
    name: String,
    buyIn: Long,
    chips: Long?,
    cashedOut: Boolean = false,
    error: UiText? = null,
    text: String = chips?.toString().orEmpty(),
) = CountRow(
    seatId = id,
    name = name,
    text = text,
    wasCashedOut = cashedOut,
    chips = chips?.let { Chips(it) },
    cashOutValue = chips?.let { PreviewRate.cashFor(Chips(it)) },
    totalBuyIn = Money(buyIn),
    net = chips?.let { PreviewRate.cashFor(Chips(it)) - Money(buyIn) },
    error = error,
)

private fun resultRow(id: Long, name: String, buyIn: Long, chips: Long?) = ResultRow(
    seatId = id,
    name = name,
    totalBuyIn = Money(buyIn),
    chipsOut = chips?.let { Chips(it) },
    cashOut = chips?.let { PreviewRate.cashFor(Chips(it)) },
    net = chips?.let { PreviewRate.cashFor(Chips(it)) - Money(buyIn) },
)

private fun summary(
    expected: Long,
    counted: Long,
    uncounted: Int,
    headline: UiText,
    impliedZero: Boolean = false,
) = ReconciliationSummary(
    expectedChips = Chips(expected),
    countedChips = Chips(counted),
    differenceChips = Chips(counted - expected),
    differenceCash = PreviewRate.cashFor(Chips(counted - expected)),
    chipRemainder = Money.ZERO,
    uncountedCount = uncounted,
    uncountedAreImpliedZero = impliedZero,
    headline = headline,
)

private fun balancedState() = EndGameUiState(
    isLoading = false,
    gameId = 1,
    gameName = "Thursday",
    chipValueLabel = UiText.Raw("1 chip = 0.005"),
    counts = listOf(
        countRow(1, "Anna", 2_000_000, 500),
        countRow(2, "Boris", 1_000_000, 100, cashedOut = true),
        countRow(3, "Chris", 1_000_000, 200),
    ),
    results = listOf(
        resultRow(1, "Anna", 2_000_000, 500),
        resultRow(2, "Boris", 1_000_000, 100),
        resultRow(3, "Chris", 1_000_000, 200),
    ),
    reconciliation = summary(800, 800, 0, UiText.Raw("Every chip is accounted for")),
)

private fun mismatchState() = EndGameUiState(
    isLoading = false,
    gameId = 1,
    gameName = "Thursday",
    chipValueLabel = UiText.Raw("1 chip = 0.005"),
    counts = listOf(
        countRow(1, "Anna", 2_000_000, 500),
        countRow(2, "Boris", 1_000_000, 100, cashedOut = true),
        countRow(3, "Chris", 1_000_000, null, text = "18o", error = UiText.Raw("Enter Chip count as a whole number")),
    ),
    results = listOf(
        resultRow(1, "Anna", 2_000_000, 500),
        resultRow(2, "Boris", 1_000_000, 100),
        resultRow(3, "Chris", 1_000_000, null),
    ),
    reconciliation = summary(800, 788, 0, UiText.Raw("12 chips unaccounted for — worth 0.06")),
)

private fun scoopedState() = EndGameUiState(
    isLoading = false,
    gameId = 1,
    gameName = "Thursday",
    chipValueLabel = UiText.Raw("1 chip = 0.005"),
    counts = listOf(
        countRow(1, "Anna", 2_000_000, 800),
        countRow(2, "Boris", 1_000_000, null, text = "").copy(
            countedAsZero = true,
            net = Money(-1_000_000),
        ),
        countRow(3, "Chris", 1_000_000, null, text = "").copy(
            countedAsZero = true,
            net = Money(-1_000_000),
        ),
    ),
    results = listOf(
        resultRow(1, "Anna", 2_000_000, 800),
        resultRow(2, "Boris", 1_000_000, 0),
        resultRow(3, "Chris", 1_000_000, 0),
    ),
    reconciliation = summary(
        expected = 800,
        counted = 800,
        uncounted = 2,
        headline = UiText.Raw("Every chip is accounted for — 2 empty stacks recorded as 0"),
        impliedZero = true,
    ),
)

@Preview(name = "End game — one player scooped it", showBackground = true, heightDp = 1200)
@Composable
private fun EndGameScoopedPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            EndGameContent(scoopedState(), { _, _ -> })
        }
    }
}

@Preview(name = "End game — balanced", showBackground = true, heightDp = 1200)
@Composable
private fun EndGameBalancedPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            EndGameContent(balancedState(), { _, _ -> })
        }
    }
}

@Preview(name = "End game — chips missing and a bad count", showBackground = true, heightDp = 1200)
@Composable
private fun EndGameMismatchPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            EndGameContent(mismatchState(), { _, _ -> })
        }
    }
}

@Preview(name = "End game — override dialog", showBackground = true, heightDp = 560)
@Composable
private fun MismatchDialogPreview() {
    PokerTrackerTheme {
        MismatchDialog(
            summary = summary(800, 788, 0, UiText.Raw("12 chips unaccounted for — worth 0.06")),
            onFinishAnyway = {},
            onGoBack = {},
        )
    }
}

@Preview(name = "End game — 200% font", showBackground = true, fontScale = 2.0f, heightDp = 1800)
@Composable
private fun EndGameLargeFontPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            EndGameContent(mismatchState(), { _, _ -> })
        }
    }
}
