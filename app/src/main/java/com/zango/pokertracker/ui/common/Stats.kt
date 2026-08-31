package com.zango.pokertracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zango.pokertracker.ui.theme.PokerTheme

/**
 * A row of small labelled figures: the few numbers that describe a night or a player at a glance.
 *
 * Each tile takes an equal share of the width so the figures line up as a row of columns rather
 * than drifting with the length of their labels.
 */
@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** One figure in a [StatRow], with the label above it in the quieter colour. */
@Composable
fun RowScope.StatTile(label: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionLabel(label)
            content()
        }
    }
}

/** A plain count, in the numeric face so it lines up with the cash figures beside it. */
@Composable
fun StatCount(value: Int, modifier: Modifier = Modifier) {
    Text(
        value.toString(),
        modifier = modifier,
        style = PokerTheme.type.numericMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** A short label such as a duration, sharing the numeric face for the same reason. */
@Composable
fun StatText(value: String, modifier: Modifier = Modifier) {
    Text(
        value,
        modifier = modifier,
        style = PokerTheme.type.numericMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
