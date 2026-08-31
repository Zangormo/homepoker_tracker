package com.zango.pokertracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zango.pokertracker.R
import com.zango.pokertracker.ui.theme.PokerTrackerTheme

/**
 * The tabs behind the menu button. The games and the people who play them are where the app
 * lives; settings sit below them because they are visited and left, not worked in.
 */
enum class AppDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    GAME_HUB(Routes.HISTORY, R.string.destination_game_hub, Icons.Filled.Casino),
    PLAYERS(Routes.PLAYERS, R.string.destination_players, Icons.Filled.Group),
    SETTINGS(Routes.SETTINGS, R.string.destination_settings, Icons.Filled.Settings),
}

/**
 * A drawer rather than a bottom bar: the game hub is where the app opens and where it stays all
 * night, so the second destination is deliberately one deliberate tap away instead of sitting
 * under the thumb of someone recording a buy-in.
 */
@Composable
fun PokerDrawerContent(
    currentRoute: String?,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.drawer_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        AppDestination.entries.forEach { destination ->
            NavigationDrawerItem(
                label = { Text(stringResource(destination.label)) },
                icon = { Icon(destination.icon, contentDescription = null) },
                selected = destination.route == currentRoute,
                onClick = { onSelect(destination) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Preview(name = "Drawer", showBackground = true, heightDp = 500, widthDp = 360)
@Composable
private fun DrawerPreview() {
    PokerTrackerTheme {
        PokerDrawerContent(currentRoute = Routes.HISTORY, onSelect = {})
    }
}
