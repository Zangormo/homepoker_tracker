package com.zango.pokertracker.ui.settlement

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.ResultRow
import com.zango.pokertracker.ui.common.ResultsTable
import com.zango.pokertracker.ui.common.SectionLabel
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settlement")
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            putExtra(Intent.EXTRA_SUBJECT, "${state.gameName} — settlement")
                            putExtra(Intent.EXTRA_TEXT, state.shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share settlement"))
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString(state.shareText))
                        scope.launch { snackbarHostState.showSnackbar("Copied") }
                    },
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

            else -> SettlementContent(
                state = state,
                onDone = onDone,
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
private fun SettlementContent(
    state: SettlementUiState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp),
    ) {
        if (state.hasPayments) {
            items(state.payments) { line ->
                PaymentLineRow(line)
                HorizontalDivider(color = PokerTheme.colors.divider)
            }
            item {
                Text(
                    "${state.payments.size} payment${if (state.payments.size == 1) "" else "s"} · " +
                        "${state.totalMoved.format()} changes hands",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            item {
                Text(
                    "Everyone broke even. No payments needed.",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }

        if (state.notes.isNotEmpty()) {
            item { NotesBlock(notes = state.notes, isProblem = state.hasProblem) }
        }

        item {
            SectionLabel("Results", modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
        }
        item { ResultsTable(state.results) }

        item {
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(MinTouchTarget),
            ) { Text("Back to history") }
        }
    }
}

/**
 * The line a player acts on. Names carry the sentence and the amount is set in the numeric face,
 * which is why the payment travels as three fields rather than as a finished string.
 *
 * No card, no icon: on a screen that gets passed across a table, anything beside the words is
 * something else for the reader to look past.
 */
@Composable
private fun PaymentLineRow(line: PaymentLine) {
    val sentence = buildAnnotatedString {
        append(line.from)
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            append(" pays ")
        }
        append(line.to)
        append(' ')
        withStyle(
            SpanStyle(
                fontFamily = NumericFamily,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
                color = MaterialTheme.colorScheme.primary,
            ),
        ) {
            append('$')
            append(line.amount.format())
        }
    }
    Text(
        text = sentence,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .clearAndSetSemantics {
                contentDescription = "${line.from} pays ${line.to} ${line.amount.format()}"
            },
    )
}

@Composable
private fun NotesBlock(notes: List<String>, isProblem: Boolean) {
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
                        "Not fully settled",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    notes.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        } else {
            notes.forEach {
                Text(
                    it,
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
                Text("  Share")
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
                Text("  Copy")
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
        PaymentLine("Anna", "Boris", Money(4_500_000)),
        PaymentLine("Chris", "Boris", Money(1_200_000)),
    ),
    totalMoved = Money(5_700_000),
    results = listOf(
        result("Boris", 1_000_000, 1340, 6_700_000, 5_700_000),
        result("Anna", 5_000_000, 100, 500_000, -4_500_000),
        result("Chris", 2_000_000, 160, 800_000, -1_200_000),
    ),
    shareText = "Thursday — settlement",
)

private fun unsettledState() = settledState().copy(
    payments = listOf(PaymentLine("Boris", "Anna", Money(250_000))),
    totalMoved = Money(250_000),
    hasProblem = true,
    notes = listOf(
        "Chip counts came out 0.06 short of the buy-ins, so these payments do not fully " +
            "square everyone up.",
        "Boris still owes 0.06.",
    ),
    results = listOf(
        result("Anna", 1_000_000, 250, 1_250_000, 250_000),
        result("Boris", 1_000_000, 138, 690_000, -310_000),
    ),
)

private fun evenState() = settledState().copy(
    payments = emptyList(),
    totalMoved = Money.ZERO,
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
