package com.zango.pokertracker.ui.players

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.ui.common.CashAmountText
import com.zango.pokertracker.ui.common.NetCashText
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.common.StatCount
import com.zango.pokertracker.ui.common.StatRow
import com.zango.pokertracker.ui.common.StatTile
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme

/**
 * Everything one player has ever done, in one column: the lifetime figure first, what it was
 * built from underneath it, and then the games themselves so any number here can be traced back
 * to a night that actually happened.
 */
@Composable
fun PlayerDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name.ifEmpty { "Player" })
                        if (state.hasPlayed) {
                            Text(
                                buildString {
                                    append(state.gamesPlayed)
                                    append(if (state.gamesPlayed == 1) " game" else " games")
                                    if (state.gamesUp > 0) {
                                        append(" · ")
                                        append(state.gamesUp)
                                        append(" up")
                                    }
                                },
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
    ) { padding ->
        when {
            state.isLoading -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.isMissing -> Centered(Modifier.padding(padding)) {
                Text("This player is no longer on the roster.")
            }

            else -> PlayerDetailContent(state, Modifier.padding(padding))
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
private fun PlayerDetailContent(state: PlayerDetailUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { LifetimeHeadline(state) }
        item { StatTiles(state) }

        if (state.games.isNotEmpty()) {
            item { SectionLabel("Every game") }
            items(state.games, key = { it.gameId }) { game -> GameLine(game) }
        }
    }
}

/**
 * The one number the screen exists for, given the room to be read across a table. Everything
 * else on the screen is what produced it.
 */
@Composable
private fun LifetimeHeadline(state: PlayerDetailUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionLabel("All-time profit")
            if (state.netProfit != null) {
                NetCashText(state.netProfit, style = PokerTheme.type.numericHero)
            } else {
                Text(
                    if (state.hasPlayed) "Not settled yet" else "No games yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.openGames > 0) {
                Text(
                    if (state.openGames == 1) {
                        "One game is still to be settled and is not counted here."
                    } else {
                        "${state.openGames} games are still to be settled and are not counted here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatTiles(state: PlayerDetailUiState) {
    StatRow {
        StatTile(label = "Paid in") {
            CashAmountText(state.totalPaidIn, style = PokerTheme.type.numericMedium)
        }
        StatTile(label = "Buy-ins") {
            StatCount(state.buyInCount)
        }
        StatTile(label = "Cashed out") {
            CashAmountText(state.cashedOut, style = PokerTheme.type.numericMedium)
        }
    }
}

/**
 * A game reads as one line: what it was, what it cost, and how it ended. A night still being
 * played says so instead of showing a profit that has not been decided yet.
 */
@Composable
private fun GameLine(game: PlayerGameRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                game.gameName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            if (game.net != null) {
                NetCashText(game.net, style = PokerTheme.type.numericSmall)
            } else {
                Text(
                    if (game.isInProgress) "Still playing" else "No result",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (game.isInProgress) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildString {
                    append(game.dateLabel)
                    append(" · ")
                    append(game.buyInCount)
                    append(if (game.buyInCount == 1) " buy-in" else " buy-ins")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CashAmountText(
                    game.totalBuyIn,
                    style = PokerTheme.type.numericCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CashAmountText(
                    game.cashOut,
                    style = PokerTheme.type.numericCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = PokerTheme.colors.divider,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun gameRow(
    id: Long,
    name: String,
    date: String,
    buyIns: Int,
    paidIn: Long,
    out: Long?,
    running: Boolean = false,
) = PlayerGameRow(
    gameId = id,
    gameName = name,
    dateLabel = date,
    isInProgress = running,
    buyInCount = buyIns,
    totalBuyIn = Money(paidIn),
    cashOut = out?.let { Money(it) },
    net = out?.let { Money(it - paidIn) },
)

private fun populatedDetail() = PlayerDetailUiState(
    isLoading = false,
    name = "Boris",
    gamesPlayed = 4,
    gamesUp = 2,
    buyInCount = 7,
    totalPaidIn = Money(14_000_000),
    cashedOut = Money(17_500_000),
    netProfit = Money(4_500_000),
    openGames = 1,
    games = listOf(
        gameRow(1, "Thursday", "30 Aug 2026 · 20:15", 1, 2_000_000, null, running = true),
        gameRow(2, "Last Thursday", "23 Aug 2026 · 20:05", 2, 4_000_000, 9_500_000),
        gameRow(3, "Boris's birthday", "16 Aug 2026 · 19:30", 3, 6_000_000, 3_000_000),
        gameRow(4, "Quiet one", "09 Aug 2026 · 21:00", 1, 2_000_000, 5_000_000),
    ),
)

@Preview(name = "Player — populated", showBackground = true, heightDp = 760)
@Composable
private fun PlayerDetailPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlayerDetailContent(populatedDetail())
        }
    }
}

@Preview(name = "Player — never played", showBackground = true, heightDp = 420)
@Composable
private fun PlayerDetailEmptyPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlayerDetailContent(PlayerDetailUiState(isLoading = false, name = "New guy"))
        }
    }
}

@Preview(name = "Player — 200% font", showBackground = true, fontScale = 2.0f, heightDp = 1100)
@Composable
private fun PlayerDetailLargeFontPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlayerDetailContent(populatedDetail())
        }
    }
}
