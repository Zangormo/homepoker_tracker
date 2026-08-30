package com.homepoker_tracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.domain.model.GameSnapshot

/** One player's line in the results table, shared by the end-game and settlement screens. */
data class ResultRow(
    val seatId: Long,
    val name: String,
    val totalBuyIn: Money,
    val finalChips: Chips?,
    val cashOut: Money?,
    val net: Money?,
)

fun GameSnapshot.toResultRows(): List<ResultRow> = seats.map { seat ->
    ResultRow(
        seatId = seat.id,
        name = seat.player.name,
        totalBuyIn = seat.totalBuyIn,
        finalChips = seat.finalChips,
        cashOut = cashOutValueOf(seat),
        net = netOf(seat),
    )
}

/** Profit and loss in the app's one colour convention: up is primary, down is error. */
@Composable
fun NetAmount(net: Money?, modifier: Modifier = Modifier) {
    Text(
        text = net?.formatSigned() ?: "—",
        modifier = modifier,
        textAlign = TextAlign.End,
        fontWeight = FontWeight.SemiBold,
        color = when {
            net == null -> MaterialTheme.colorScheme.onSurfaceVariant
            net.isPositive -> MaterialTheme.colorScheme.primary
            net.isNegative -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
fun ResultsTable(rows: List<ResultRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            HeaderCell("Player", 2.4f, TextAlign.Start)
            HeaderCell("In", 1.6f)
            HeaderCell("Chips", 1.4f)
            HeaderCell("Out", 1.6f)
            HeaderCell("Net", 1.7f)
        }
        HorizontalDivider()
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    row.name,
                    modifier = Modifier.weight(2.4f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                BodyCell(row.totalBuyIn.format(), 1.6f)
                BodyCell(row.finalChips?.toString() ?: "—", 1.4f)
                BodyCell(row.cashOut?.format() ?: "—", 1.6f)
                NetAmount(row.net, Modifier.weight(1.7f))
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.End,
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        textAlign = align,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BodyCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.End,
    )
}
