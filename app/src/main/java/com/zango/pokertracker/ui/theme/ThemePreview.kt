package com.zango.pokertracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The palette and type scale on one screen, so both can be judged before any of it reaches a
 * real screen. Every ratio quoted here was measured against the surface the swatch sits on.
 */
@Preview(name = "Palette and type", showBackground = true, heightDp = 1500)
@Composable
private fun ThemeSpecimen() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SectionTitle("Surfaces")
                Swatch("surfaceBase", Tokens.SurfaceBase, "#14171A", "app background")
                Swatch("surfaceRaised", Tokens.SurfaceRaised, "#1D2125", "cards, list rows")
                Swatch("surfaceOverlay", Tokens.SurfaceOverlay, "#272C31", "dialogs, fields, pressed")
                Swatch("outline", Tokens.Outline, "#363C42", "hairlines, resting borders")

                SectionTitle("Accent and state")
                Swatch("accent", Tokens.Accent, "#2EE68A", "primary action, focus, profit")
                Swatch("accentDim", Tokens.AccentDim, "#1A9D5E", "pressed / disabled green")
                Swatch("error", Tokens.Error, "#FF4155", "validation failures only")
                Swatch("negative", Tokens.Negative, "#E5707E", "a player is down — not an error")

                SectionTitle("Text")
                Swatch("textPrimary", Tokens.TextPrimary, "#EDF1F3", "15.8:1 on base")
                Swatch("textSecondary", Tokens.TextSecondary, "#8B959C", "5.3:1 on raised")

                SectionTitle("Green fill takes dark text")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}) { Text("Start game") }
                    OutlinedButton(onClick = {}) { Text("Rebuy") }
                    TextButton(onClick = {}) { Text("Cancel") }
                }

                SectionTitle("Profit and loss never rely on colour alone")
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        "+4.50",
                        style = PokerTheme.type.numericLarge,
                        color = PokerTheme.colors.positive,
                    )
                    Text(
                        "−1.20",
                        style = PokerTheme.type.numericLarge,
                        color = PokerTheme.colors.negative,
                    )
                    Text(
                        "0.00",
                        style = PokerTheme.type.numericLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle("Display — Space Grotesk")
                Specimen("displayLarge 40/44 SemiBold", MaterialTheme.typography.displayLarge, "12.50")
                Specimen("headlineMedium 20/26 SemiBold", MaterialTheme.typography.headlineMedium, "Thursday game")
                Specimen("titleLarge 20/26 SemiBold", MaterialTheme.typography.titleLarge, "Settlement")

                SectionTitle("Body — Inter")
                Specimen("titleMedium 16/22 SemiBold", MaterialTheme.typography.titleMedium, "Who is playing")
                Specimen("bodyLarge 16/24 Regular", MaterialTheme.typography.bodyLarge, "Count the chips in front of each player.")
                Specimen("bodyMedium 14/20 Regular", MaterialTheme.typography.bodyMedium, "Chips marked 1/2 make a chip worth 0.005.")
                Specimen("labelSmall 11/16 SemiBold +0.8", MaterialTheme.typography.labelSmall, "PLAYER")

                SectionTitle("Numeric — IBM Plex Mono, tabular")
                Specimen("numericHero 40/44", PokerTheme.type.numericHero, "1041.50")
                Specimen("numericLarge 22/28", PokerTheme.type.numericLarge, "2h 47m")
                Specimen("numericMedium 17/24", PokerTheme.type.numericMedium, "0.005 / 0.01")
                Specimen("numericSmall 14/20", PokerTheme.type.numericSmall, "1200")

                SectionTitle("Digits must not shift width as they tick")
                Column {
                    listOf("1h 09m", "1h 10m", "1h 11m", "0h 00m").forEach {
                        Text(it, style = PokerTheme.type.numericLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            color = PokerTheme.colors.divider,
        )
    }
}

@Composable
private fun Swatch(name: String, color: Color, hex: String, use: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(color, MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(
                use,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            hex,
            style = PokerTheme.type.numericCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Specimen(label: String, style: androidx.compose.ui.text.TextStyle, sample: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(sample, style = style, textAlign = TextAlign.Start)
    }
}
