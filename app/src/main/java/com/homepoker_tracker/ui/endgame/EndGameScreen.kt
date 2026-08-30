package com.homepoker_tracker.ui.endgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import com.homepoker_tracker.ui.common.NetAmount
import com.homepoker_tracker.ui.common.ResultsTable
import com.homepoker_tracker.ui.common.WholeNumberField

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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EndGameEvent.Finished -> onFinished(event.gameId)
                is EndGameEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("End game")
                        if (state.gameName.isNotEmpty()) {
                            Text(state.gameName, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isMissing -> Centered(Modifier.padding(padding)) {
                Text("This game is no longer available.")
            }

            else -> EndGameContent(
                state = state,
                onCountChange = viewModel::onCountChange,
                onFinish = viewModel::onFinish,
                onViewSettlement = { onViewSettlement(state.gameId) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.isConfirmingMismatch) {
        MismatchDialog(
            headline = state.reconciliation?.headline.orEmpty(),
            onConfirm = viewModel::onConfirmMismatch,
            onDismiss = viewModel::onDismissMismatch,
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
    onFinish: () -> Unit,
    onViewSettlement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.reconciliation?.let { item { ReconciliationCard(it) } }

        item {
            Text("Chip counts", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Text(
                "Count the chips in front of each player. ${state.chipValueLabel}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.counts, key = { it.seatId }) { row ->
            CountCard(
                row = row,
                enabled = !state.alreadyFinished,
                onCountChange = { text -> onCountChange(row.seatId, text) },
            )
        }

        item { Text("Results", style = MaterialTheme.typography.titleMedium) }
        item { ResultsTable(state.results) }

        item {
            if (state.alreadyFinished) {
                Button(onClick = onViewSettlement, modifier = Modifier.fillMaxWidth()) {
                    Text("View settlement")
                }
            } else {
                Button(
                    onClick = onFinish,
                    enabled = state.canFinish,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isFinishing) "Finishing…" else "Finish and settle up")
                }
            }
        }
    }
}

@Composable
private fun ReconciliationCard(summary: ReconciliationSummary) {
    val clean = summary.isClean
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (clean) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                summary.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Bought in: ${summary.expectedChips} chips  ·  " +
                    "Counted: ${summary.countedChips} chips",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!clean && !summary.hasUncounted) {
                Text(
                    "Go back and re-count, or finish anyway if you are sure.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CountCard(row: CountRow, enabled: Boolean, onCountChange: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append("In for ${row.totalBuyIn.format()}")
                            if (row.wasCashedOut) append(" · cashed out during play")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NetAmount(row.net)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WholeNumberField(
                    value = row.text,
                    onValueChange = onCountChange,
                    label = "Final chips",
                    error = row.error,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.width(180.dp),
                )
                Text(
                    row.cashOutValue?.let { "= ${it.format()}" } ?: "not counted",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (row.cashOutValue == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color.Unspecified
                    },
                )
            }
            if (!enabled) {
                Text(
                    "This game has finished.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MismatchDialog(headline: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("The chips do not add up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(headline, fontWeight = FontWeight.SemiBold)
                Text(
                    "Settling up now will divide the table as counted, which means somebody " +
                        "ends up short. Re-count first if you can.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Finish anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Go back") } },
    )
}
