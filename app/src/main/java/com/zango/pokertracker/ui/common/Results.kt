package com.zango.pokertracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zango.pokertracker.R
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.ui.theme.PokerTheme

/** One player's line in the results table, shared by the end-game and settlement screens. */
data class ResultRow(
    val seatId: Long,
    val name: String,
    val totalBuyIn: Money,
    /**
     * Every chip the player took out of the game: sold back to the bank mid-game plus held at
     * the end. Keeping the total here is what makes [cashOut] equal chips times the chip value
     * on every row, however many times the player went back to the bank.
     */
    val chipsOut: Chips?,
    val cashOut: Money?,
    val net: Money?,
)

fun GameSnapshot.toResultRows(): List<ResultRow> = seats.map { seat ->
    ResultRow(
        seatId = seat.id,
        name = seat.player.name,
        totalBuyIn = seat.totalBuyIn,
        chipsOut = seat.chipsOut,
        cashOut = cashOutValueOf(seat),
        net = netOf(seat),
    )
}

// The name column is the widest because a name cannot be abbreviated the way a figure can be
// shrunk; the four numeric columns share the rest, the result a little wider for its sign.
private const val NAME_WEIGHT = 2.0f
private const val CASH_WEIGHT = 1.4f
private const val CHIP_WEIGHT = 1.4f
private const val NET_WEIGHT = 1.6f
private val COLUMN_WEIGHTS = listOf(NAME_WEIGHT, CASH_WEIGHT, CHIP_WEIGHT, CASH_WEIGHT, NET_WEIGHT)

/** Room between a cell's content and the rules either side of it. */
private val CellPadding = 8.dp

/** Headings and figures shrink to stay on one line, but never below this. */
private val MinHeaderSize = 8.sp
private val MinNameSize = 11.sp

/** How far the column separators stop short of a row's top and bottom edges. */
private val RuleInset = 10.dp

/**
 * The results on a soft rounded card rather than in a grid: every numeric column right-aligned
 * and monospaced, with the same inset in every cell, so the figures still line up and can be
 * scanned as a column. Columns are parted by short, faint separators that float in each row
 * instead of running the full height, and each player's result sits in a tinted pill, so the one
 * figure people look for is found at a glance.
 *
 * Nothing in it wraps. A heading, a long name or a large figure shrinks to fit its cell instead,
 * since a second line in one cell pushes the whole row out of line with the others.
 *
 * The chip and cash marks sit above the headings rather than in every cell. In a table the
 * column already names the unit, and repeating the mark on each row is noise.
 */
@Composable
fun ResultsTable(rows: List<ResultRow>, modifier: Modifier = Modifier) {
    val rule = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        val cashSymbol = LocalCashFormat.current.symbol
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderCell(stringResource(R.string.results_column_player), NAME_WEIGHT, alignEnd = false)
            HeaderCell(stringResource(R.string.results_column_in), CASH_WEIGHT, symbol = cashSymbol, markTint = PokerTheme.colors.cash)
            HeaderCell(stringResource(R.string.results_column_chips), CHIP_WEIGHT, icon = PokerChip, markTint = PokerTheme.colors.chip)
            HeaderCell(stringResource(R.string.results_column_out), CASH_WEIGHT, symbol = cashSymbol, markTint = PokerTheme.colors.cash)
            HeaderCell(stringResource(R.string.results_column_net), NET_WEIGHT)
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = CellPadding), color = rule)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .columnRules(rule),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name,
                    modifier = Modifier
                        .weight(NAME_WEIGHT)
                        .padding(horizontal = CellPadding),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = MinNameSize,
                        maxFontSize = MaterialTheme.typography.titleSmall.fontSize,
                    ),
                )
                CashAmountText(
                    row.totalBuyIn,
                    modifier = numericCell(CASH_WEIGHT),
                    style = PokerTheme.type.numericSmall,
                    showIcon = false,
                    fitWidth = true,
                )
                ChipAmountText(
                    row.chipsOut,
                    modifier = numericCell(CHIP_WEIGHT),
                    style = PokerTheme.type.numericSmall,
                    showIcon = false,
                    fitWidth = true,
                )
                CashAmountText(
                    row.cashOut,
                    modifier = numericCell(CASH_WEIGHT),
                    style = PokerTheme.type.numericSmall,
                    showIcon = false,
                    fitWidth = true,
                )
                Box(modifier = numericCell(NET_WEIGHT), contentAlignment = Alignment.CenterEnd) {
                    NetCashText(
                        row.net,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(netTint(row.net))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = PokerTheme.type.numericSmall,
                        fitWidth = true,
                    )
                }
            }
        }
    }
}

/** A faint wash of the result's own colour, green for a win and red for a loss. */
@Composable
private fun netTint(net: Money?): Color = when {
    net == null -> Color.Transparent
    net.isPositive -> PokerTheme.colors.positive.copy(alpha = 0.14f)
    net.isNegative -> PokerTheme.colors.negative.copy(alpha = 0.14f)
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
}

private fun RowScope.numericCell(weight: Float): Modifier =
    Modifier.weight(weight).padding(horizontal = CellPadding)

/**
 * Draws the separators between columns, stopping short of the row's edges. Every cell in a row
 * is sized by weight alone, so the column edges fall at the same fractions of the width in every
 * row.
 */
private fun Modifier.columnRules(color: Color): Modifier = drawBehind {
    val total = COLUMN_WEIGHTS.sum()
    val stroke = 1.dp.toPx()
    val inset = RuleInset.toPx()
    var x = 0f
    COLUMN_WEIGHTS.dropLast(1).forEach { weight ->
        x += size.width * weight / total
        drawLine(color, Offset(x, inset), Offset(x, size.height - inset), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    weight: Float,
    alignEnd: Boolean = true,
    icon: ImageVector? = null,
    markTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** The currency symbol, marking a cash column the way [icon] marks the chips one. */
    symbol: String? = null,
) {
    val align = if (alignEnd) Alignment.End else Alignment.Start
    Column(
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = CellPadding, vertical = 8.dp),
        horizontalAlignment = align,
        verticalArrangement = Arrangement.Bottom,
    ) {
        // The mark sits on a line of its own, the same height in every column, so the headings
        // below all share one baseline whether or not their column has a unit.
        Box(modifier = Modifier.height(14.dp), contentAlignment = Alignment.Center) {
            when {
                icon != null -> Icon(icon, contentDescription = null, tint = markTint, modifier = Modifier.size(13.dp))
                !symbol.isNullOrEmpty() -> Text(symbol, style = MaterialTheme.typography.labelSmall, color = markTint, maxLines = 1)
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = MinHeaderSize,
                maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
            ),
        )
    }
}
