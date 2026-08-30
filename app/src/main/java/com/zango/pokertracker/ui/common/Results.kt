package com.zango.pokertracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.ui.theme.PokerTheme

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

private const val NAME_WEIGHT = 2.1f
private const val CASH_WEIGHT = 1.5f
private const val CHIP_WEIGHT = 1.3f
private const val NET_WEIGHT = 1.6f

/**
 * A dense table: every numeric column right-aligned and monospaced, so the figures line up
 * vertically and can be scanned as a column rather than read one at a time.
 *
 * The chip and cash marks sit in the headers rather than in every cell. In a table the column
 * already names the unit, and repeating the icon on each row is noise that works against the
 * density the screen needs.
 */
@Composable
fun ResultsTable(rows: List<ResultRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            HeaderCell("Player", NAME_WEIGHT, align = TextAlign.Start)
            HeaderCell("In", CASH_WEIGHT, icon = Icons.Filled.AttachMoney, iconTint = PokerTheme.colors.cash)
            HeaderCell("Chips", CHIP_WEIGHT, icon = PokerChip, iconTint = PokerTheme.colors.chip)
            HeaderCell("Out", CASH_WEIGHT, icon = Icons.Filled.AttachMoney, iconTint = PokerTheme.colors.cash)
            HeaderCell("Net", NET_WEIGHT)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name,
                    modifier = Modifier.weight(NAME_WEIGHT),
                    style = MaterialTheme.typography.titleSmall,
                )
                CashAmountText(
                    row.totalBuyIn,
                    modifier = Modifier.weight(CASH_WEIGHT),
                    style = PokerTheme.type.numericSmall,
                    showIcon = false,
                )
                ChipAmountText(
                    row.finalChips,
                    modifier = Modifier.weight(CHIP_WEIGHT),
                    style = PokerTheme.type.numericSmall,
                    showIcon = false,
                )
                CashAmountText(
                    row.cashOut,
                    modifier = Modifier.weight(CASH_WEIGHT),
                    style = PokerTheme.type.numericSmall,
                    showIcon = false,
                )
                NetCashText(
                    row.net,
                    modifier = Modifier.weight(NET_WEIGHT),
                    style = PokerTheme.type.numericSmall,
                )
            }
            HorizontalDivider(color = PokerTheme.colors.divider)
        }
    }
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.End,
    icon: ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.weight(weight),
        horizontalArrangement = if (align == TextAlign.Start) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
        )
    }
}
