package com.zango.pokertracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zango.pokertracker.R
import com.zango.pokertracker.core.locale.CurrencyOption
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.PokerTextField
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.theme.PokerTheme
import java.util.Locale

/**
 * The currency shown beside cash amounts: one row saying what it is now, and a searchable list of
 * every currency behind it, the way money apps everywhere offer the choice.
 */
@Composable
fun CurrencySection(
    current: CurrencyOption,
    options: (Locale) -> List<CurrencyOption>,
    onSelect: (String) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }

    SectionLabel(
        stringResource(R.string.settings_section_currency),
        modifier = Modifier.padding(top = 20.dp),
    )
    Text(
        stringResource(R.string.settings_currency_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Surface(
        onClick = { picking = true },
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        CurrencyLine(option = current, mark = LineMark.OPENS_LIST)
    }

    if (picking) {
        CurrencySheet(
            currentCode = current.code,
            options = options,
            onSelect = {
                picking = false
                onSelect(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun CurrencySheet(
    currentCode: String,
    options: (Locale) -> List<CurrencyOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val all = remember(locale) { options(locale) }
    var query by rememberSaveable { mutableStateOf("") }
    // The current one first, then everything in name order, narrowed by name, code or symbol.
    val shown = remember(all, query, currentCode) {
        val needle = query.trim().lowercase(locale)
        all.filter {
            needle.isEmpty() ||
                it.name.lowercase(locale).contains(needle) ||
                it.code.lowercase(locale).contains(needle) ||
                it.symbol.lowercase(locale).contains(needle)
        }.sortedByDescending { it.code == currentCode }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.settings_currency_pick),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            PokerTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.settings_currency_search),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(shown, key = { it.code }) { option ->
                    val selected = option.code == currentCode
                    Surface(
                        onClick = { onSelect(option.code) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = MinTouchTarget),
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ) {
                        CurrencyLine(
                            option = option,
                            mark = if (selected) LineMark.SELECTED else LineMark.NONE,
                        )
                    }
                }
            }
        }
    }
}

/** What sits at the end of a currency line. */
private enum class LineMark { OPENS_LIST, SELECTED, NONE }

/** The symbol in a fixed-width column, so the names line up down the list. */
@Composable
private fun CurrencyLine(option: CurrencyOption, mark: LineMark) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.widthIn(min = 40.dp), contentAlignment = Alignment.Center) {
            Text(
                option.symbol,
                style = PokerTheme.type.numericMedium,
                color = PokerTheme.colors.cash,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                option.name.replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                option.code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (mark) {
                LineMark.OPENS_LIST -> Icon(
                    Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LineMark.SELECTED -> Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )

                LineMark.NONE -> Unit
            }
        }
    }
}
