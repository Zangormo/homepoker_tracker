package com.zango.pokertracker.ui.settlement

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.ui.common.CashAmountText
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.ResultRow
import com.zango.pokertracker.ui.common.ResultsTable
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.common.resolve
import com.zango.pokertracker.ui.common.StatCount
import com.zango.pokertracker.ui.common.StatRow
import com.zango.pokertracker.ui.common.StatText
import com.zango.pokertracker.ui.common.StatTile
import com.zango.pokertracker.ui.theme.NumericFamily
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme
import kotlinx.coroutines.launch

@Composable
fun SettlementScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettlementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalClipboard is the newer API but its suspend ClipEntry plumbing is Android-specific and
    // buys nothing here; plain text on the clipboard is all this screen needs.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareSubject = stringResource(R.string.settlement_share_subject, state.gameName)
    val shareChooser = stringResource(R.string.settlement_share_chooser)
    val copiedMessage = stringResource(R.string.settlement_copied)
    // Built here rather than in the ViewModel: the lines are resources, and this is the only
    // layer that knows which language to render them in.
    val shareText = state.shareLines.joinToString(separator = "\n") { it.resolve(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.settlement_title))
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
                ShareBar(
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, shareChooser))
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString(shareText))
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isMissing -> Centered(Modifier.padding(padding)) {
                Text(stringResource(R.string.settlement_game_missing))
            }

            else -> SettlementContent(
                state = state,
                onDone = onDone,
                modifier = Modifier.padding(padding),
                onPaymentToggled = viewModel::onPaymentToggled,
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
private fun SettlementContent(
    state: SettlementUiState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onPaymentToggled: (PaymentLine) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp),
    ) {
        if (state.isFinished) {
            item { GameStats(state) }
        }

        if (state.hasPayments) {
            items(state.payments, key = { it.fromPlayerId to it.toPlayerId }) { line ->
                PaymentLineRow(
                    line = line,
                    canTick = state.isFinished,
                    onToggle = { onPaymentToggled(line) },
                )
                HorizontalDivider(color = PokerTheme.colors.divider)
            }
            item {
                Text(
                    if (state.isFinished) {
                        pluralStringResource(
                            R.plurals.settlement_paid_count,
                            state.payments.size,
                            state.paidCount,
                            state.payments.size,
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.payment_count,
                            state.payments.size,
                            state.payments.size,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isFullyPaid) {
                        PokerTheme.colors.positive
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            item {
                Text(
                    stringResource(R.string.settlement_everyone_even),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }

        if (state.notes.isNotEmpty()) {
            item { NotesBlock(notes = state.notes, isProblem = state.hasProblem) }
        }

        item {
            SectionLabel(stringResource(R.string.settlement_section_results), modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
        }
        item { ResultsTable(state.results) }

        item {
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(MinTouchTarget),
            ) { Text(stringResource(R.string.settlement_back_to_history)) }
        }
    }
}

/**
 * What the night came to, above the payments.
 *
 * These three answer the questions asked once the cards are away and before anyone starts paying
 * each other: how many times people went back to the bank, how long it took, and how much money
 * the bank was holding by the end.
 */
@Composable
private fun GameStats(state: SettlementUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(stringResource(R.string.settlement_section_the_night))
        StatRow {
            StatTile(label = stringResource(R.string.settlement_stat_buy_ins)) { StatCount(state.buyInCount) }
            StatTile(label = stringResource(R.string.settlement_stat_lasted)) {
                StatText(state.durationLabel ?: stringResource(R.string.value_none))
            }
            StatTile(label = stringResource(R.string.settlement_stat_in_the_bank)) {
                CashAmountText(state.totalOnTable, style = PokerTheme.type.numericMedium)
            }
        }
    }
}

/**
 * The line a player acts on, with a box to tick once the money has actually changed hands.
 *
 * Names carry the sentence and the amount is set in the numeric face, which is why the payment
 * travels as separate fields rather than as a finished string. A paid line drops to the quiet
 * colour throughout, so a glance down the list finds what is still owed without reading it.
 *
 * No card, no icon: on a screen that gets passed across a table, anything beside the words and
 * the box is something else for the reader to look past.
 */
@Composable
private fun PaymentLineRow(line: PaymentLine, canTick: Boolean, onToggle: () -> Unit) {
    val paid = line.isPaid
    val bodyColor = if (paid) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val paysVerb = stringResource(R.string.settlement_pays_verb)
    val sentence = buildAnnotatedString {
        append(line.from)
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            append(paysVerb)
        }
        append(line.to)
        append(' ')
        withStyle(
            SpanStyle(
                fontFamily = NumericFamily,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
                color = if (paid) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            ),
        ) {
            append('$')
            append(line.amount.format())
        }
    }

    val plain = stringResource(R.string.settlement_pays, line.from, line.to, line.amount.format())
    val spoken = when {
        !canTick -> plain
        paid -> stringResource(R.string.settlement_payment_paid, plain)
        else -> stringResource(R.string.settlement_payment_unpaid, plain)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canTick) {
                    Modifier.toggleable(value = paid, role = Role.Checkbox, onValueChange = {
                        onToggle()
                    })
                } else {
                    Modifier
                },
            )
            .padding(vertical = if (canTick) 10.dp else 16.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canTick) {
            Checkbox(
                checked = paid,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = sentence,
            style = MaterialTheme.typography.headlineMedium,
            color = bodyColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NotesBlock(notes: List<UiText>, isProblem: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isProblem) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.settlement_not_settled),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    notes.forEach {
                        Text(
                            it.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        } else {
            notes.forEach {
                Text(
                    it.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShareBar(onShare: () -> Unit, onCopy: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(MinTouchTarget),
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settlement_share))
            }
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier
                    .weight(1f)
                    .height(MinTouchTarget),
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settlement_copy))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun result(name: String, buyIn: Long, chips: Long, out: Long, net: Long) = ResultRow(
    seatId = name.hashCode().toLong(),
    name = name,
    totalBuyIn = Money(buyIn),
    chipsOut = Chips(chips),
    cashOut = Money(out),
    net = Money(net),
)

private fun settledState() = SettlementUiState(
    isLoading = false,
    gameName = "Thursday",
    isFinished = true,
    payments = listOf(
        PaymentLine(1, "Anna", 2, "Boris", Money(4_500_000), isPaid = true),
        PaymentLine(3, "Chris", 2, "Boris", Money(1_200_000)),
    ),
    buyInCount = 9,
    durationLabel = "4h 12m",
    totalOnTable = Money(8_000_000),
    results = listOf(
        result("Boris", 1_000_000, 1340, 6_700_000, 5_700_000),
        result("Anna", 5_000_000, 100, 500_000, -4_500_000),
        result("Chris", 2_000_000, 160, 800_000, -1_200_000),
    ),
    shareLines = listOf(UiText.Raw("Thursday — settlement")),
)

private fun unsettledState() = settledState().copy(
    payments = listOf(PaymentLine(2, "Boris", 1, "Anna", Money(250_000))),
    buyInCount = 2,
    totalOnTable = Money(2_000_000),
    hasProblem = true,
    notes = listOf(
        UiText.Raw(
            "Chip counts came out 0.06 short of the buy-ins, so these payments do not fully " +
                "square everyone up.",
        ),
        UiText.Raw("Boris still owes 0.06."),
    ),
    results = listOf(
        result("Anna", 1_000_000, 250, 1_250_000, 250_000),
        result("Boris", 1_000_000, 138, 690_000, -310_000),
    ),
)

private fun evenState() = settledState().copy(
    payments = emptyList(),
    buyInCount = 2,
    totalOnTable = Money(2_000_000),
    notes = emptyList(),
    results = listOf(
        result("Anna", 1_000_000, 200, 1_000_000, 0),
        result("Boris", 1_000_000, 200, 1_000_000, 0),
    ),
)

@Preview(name = "Settlement — payments", showBackground = true, heightDp = 900)
@Composable
private fun SettlementPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettlementContent(settledState(), {})
        }
    }
}

@Preview(name = "Settlement — did not balance", showBackground = true, heightDp = 900)
@Composable
private fun SettlementProblemPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettlementContent(unsettledState(), {})
        }
    }
}

@Preview(name = "Settlement — everyone even", showBackground = true, heightDp = 700)
@Composable
private fun SettlementEvenPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettlementContent(evenState(), {})
        }
    }
}

@Preview(name = "Settlement — 200% font", showBackground = true, fontScale = 2.0f, heightDp = 1400)
@Composable
private fun SettlementLargeFontPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettlementContent(settledState(), {})
        }
    }
}
