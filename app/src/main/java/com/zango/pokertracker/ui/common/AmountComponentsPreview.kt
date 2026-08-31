package com.zango.pokertracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme

/**
 * The chip and cash components on their own, in every state that matters, so the pair can be
 * judged before either goes near a screen.
 */
@Preview(name = "Chip and cash components", showBackground = true, heightDp = 1500)
@Composable
private fun AmountComponentSpecimen() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Heading("Chip glyph at working sizes")
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    listOf(16, 20, 24, 40, 64).forEach { size ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                PokerChip,
                                contentDescription = null,
                                tint = PokerTheme.colors.chip,
                                modifier = Modifier.size(size.dp),
                            )
                            Text(
                                "${size}dp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Heading("Chip and cash side by side, separable at a glance")
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Anna", style = MaterialTheme.typography.titleMedium)
                            ChipAmountText(Chips(1_200), style = PokerTheme.type.numericMedium)
                            CashAmountText(Money(6_000_000), style = PokerTheme.type.numericMedium)
                        }
                        HorizontalDivider(color = PokerTheme.colors.divider)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Boris", style = MaterialTheme.typography.titleMedium)
                            ChipAmountText(Chips(80), style = PokerTheme.type.numericMedium)
                            CashAmountText(Money(400_000), style = PokerTheme.type.numericMedium)
                        }
                    }
                }

                Heading("Conversion reads as one relationship")
                CashToChipsRow(cash = Money(1_000_000), chips = Chips(200))
                CashToChipsRow(
                    cash = Money(5_000),
                    chips = Chips(1),
                    style = PokerTheme.type.numericSmall,
                )

                Heading("Fields — resting, focused-then-emptied, invalid, disabled")
                ChipAmountField(
                    value = "",
                    onValueChange = {},
                    label = "Final chips",
                    required = true,
                )
                ChipAmountField(
                    value = "",
                    onValueChange = {},
                    label = "Final chips",
                    required = true,
                    error = UiText.Raw("Enter a whole number of chips"),
                    forceShowError = true,
                )
                ChipAmountField(
                    value = "250.5",
                    onValueChange = {},
                    label = "Final chips",
                    error = UiText.Raw("Enter a whole number of chips"),
                    forceShowError = true,
                )
                ChipAmountField(value = "1200", onValueChange = {}, label = "Final chips")
                ChipAmountField(
                    value = "1200",
                    onValueChange = {},
                    label = "Final chips",
                    enabled = false,
                )

                CashAmountField(
                    value = "",
                    onValueChange = {},
                    label = "Small blind",
                    required = true,
                    supporting = UiText.Raw("As little as 0.001"),
                )
                CashAmountField(
                    value = "0.02",
                    onValueChange = {},
                    label = "Small blind",
                    error = UiText.Raw("Small blind must be smaller than big blind"),
                    forceShowError = true,
                )
                CashAmountField(value = "1.00", onValueChange = {}, label = "Buy-in")
                PokerTextField(
                    value = "",
                    onValueChange = {},
                    label = "Game name",
                    required = true,
                    error = UiText.Raw("Give the game a name"),
                    forceShowError = true,
                )

                Heading("Profit and loss carry a sign as well as a colour")
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    NetCashText(Money(4_500_000), style = PokerTheme.type.numericLarge)
                    NetCashText(Money(-1_200_000), style = PokerTheme.type.numericLarge)
                    NetCashText(Money.ZERO, style = PokerTheme.type.numericLarge)
                    NetCashText(null, style = PokerTheme.type.numericLarge)
                }

                Heading("Right-aligned column, monospaced so digits line up")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf(
                        Money(1_041_500_000),
                        Money(6_000_000),
                        Money(400_000),
                        Money(1_000),
                    ).forEach { CashAmountText(it, style = PokerTheme.type.numericMedium) }
                }

                Heading("Absent values read as absent, not as zero")
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ChipAmountText(null, style = PokerTheme.type.numericMedium)
                    CashAmountText(null, style = PokerTheme.type.numericMedium)
                }
            }
        }
    }
}

@Preview(name = "Components at 200% font", showBackground = true, fontScale = 2.0f, heightDp = 900)
@Composable
private fun AmountComponentsLargeFont() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CashToChipsRow(cash = Money(1_000_000), chips = Chips(200))
                ChipAmountField(
                    value = "",
                    onValueChange = {},
                    label = "Final chips",
                    required = true,
                    error = UiText.Raw("Enter a whole number of chips"),
                    forceShowError = true,
                )
                CashAmountField(value = "1.00", onValueChange = {}, label = "Buy-in")
                NetCashText(Money(-1_200_000), style = PokerTheme.type.numericLarge)
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}
