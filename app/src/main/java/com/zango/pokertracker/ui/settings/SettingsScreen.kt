package com.zango.pokertracker.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zango.pokertracker.BuildConfig
import com.zango.pokertracker.R
import com.zango.pokertracker.core.locale.AppCurrencyStore
import com.zango.pokertracker.core.locale.AppLanguage
import com.zango.pokertracker.core.locale.AppLanguageStore
import com.zango.pokertracker.core.locale.findActivity
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.domain.model.Stakes
import com.zango.pokertracker.ui.common.CashAmountField
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.common.SelectionIndicator
import com.zango.pokertracker.ui.common.acceptsBigBlind
import com.zango.pokertracker.ui.common.acceptsSmallBlind
import com.zango.pokertracker.ui.common.resolve
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme
import kotlinx.coroutines.launch

/**
 * Settings: the language, removing ads, the currency, and the stake levels the new-game picker
 * offers.
 *
 * It carries a back arrow rather than the menu button the other drawer destinations use: this is
 * somewhere the host steps into and comes straight back out of, not a place to sit during a game.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noBrowserMessage = stringResource(R.string.error_no_browser)
    val undoLabel = stringResource(R.string.action_undo)

    val context = LocalContext.current

    LaunchedEffect(viewModel, undoLabel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(context))
                is SettingsEvent.Removed -> {
                    val result = snackbarHostState.showSnackbar(event.text.resolve(context), actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onUndoRemove(event.stakes)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
        } else {
            SettingsContent(
                state = state,
                language = AppLanguageStore.current(context),
                onSelectLanguage = { language ->
                    AppLanguageStore.set(context, language)
                    // Below Android 13 nothing restarts the screen for us; above it the system
                    // already has, and recreating again is harmless.
                    context.findActivity()?.recreate()
                },
                onAdd = viewModel::onAddRequested,
                onRemove = viewModel::onRemove,
                onRemoveAds = { context.findActivity()?.let(viewModel::onRemoveAds) },
                onAdPrivacyOptions = { context.findActivity()?.let(viewModel::onAdPrivacyOptions) },
                onOpenPrivacyPolicy = {
                    openPrivacyPolicy(context) {
                        scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    state.editor?.let { editor ->
        AddStakesDialog(
            editor = editor,
            onSmallBlindChange = viewModel::onSmallBlindChange,
            onBigBlindChange = viewModel::onBigBlindChange,
            onConfirm = viewModel::onConfirmAdd,
            onDismiss = viewModel::onDismissAdd,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Stakes) -> Unit,
    onRemoveAds: () -> Unit,
    onAdPrivacyOptions: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageSection(current = language, onSelect = onSelectLanguage)

        // Second, right under the language, so a host sent here by the donation prompt finds it
        // without scrolling.
        RemoveAdsSection(state = state.removeAds, onRemoveAds = onRemoveAds)
        PrivacySection(
            showAdPrivacyOptions = state.showAdPrivacyOptions,
            onOpenPolicy = onOpenPrivacyPolicy,
            onAdPrivacyOptions = onAdPrivacyOptions,
        )

        val context = LocalContext.current
        val currencyCode by AppCurrencyStore.code.collectAsState()
        val locale = LocalConfiguration.current.locales[0]
        CurrencySection(
            current = remember(currencyCode, locale) { AppCurrencyStore.option(currencyCode, locale) },
            options = AppCurrencyStore::options,
            onSelect = { AppCurrencyStore.set(context, it) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(stringResource(R.string.settings_section_blind_sizes))
            Text(
                stringResource(R.string.settings_blind_count, state.count, Stakes.MAX_PRESETS),
                style = PokerTheme.type.numericCaption,
                color = if (state.isFull) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            stringResource(R.string.settings_blind_sizes_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        if (state.isEmpty) {
            Text(
                stringResource(R.string.settings_blind_sizes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            state.stakes.forEach { row ->
                StakeRowItem(row = row, onRemove = { onRemove(row.stakes) })
            }
        }

        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .defaultMinSize(minHeight = MinTouchTarget),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_add_blinds))
        }

        // Last and quiet: looked up when reporting a problem, never worked with.
        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

/**
 * The one thing the app sells. The button carries the price Google Play quotes for this user, so
 * the cost is known before the purchase sheet opens. Without a price (Play not reached yet) the
 * button still works: tapping it asks Play again and says so if it cannot be reached.
 */
@Composable
private fun RemoveAdsSection(state: RemoveAdsUiState, onRemoveAds: () -> Unit) {
    SectionLabel(
        stringResource(R.string.settings_section_remove_ads),
        modifier = Modifier.padding(top = 20.dp),
    )
    when {
        state.isRemoved -> Text(
            stringResource(R.string.settings_remove_ads_done),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        state.isPending -> Text(
            stringResource(R.string.settings_remove_ads_pending),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> {
            Text(
                stringResource(R.string.settings_remove_ads_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Button(
                onClick = onRemoveAds,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MinTouchTarget),
            ) {
                Text(
                    if (state.price != null) {
                        stringResource(R.string.settings_remove_ads_button_price, state.price)
                    } else {
                        stringResource(R.string.settings_remove_ads_button)
                    },
                )
            }
        }
    }
}

/**
 * The published privacy policy, opened in the phone's browser.
 *
 * TODO: this is the only place the policy's address lives; if the policy moves, change it here.
 */
private const val PRIVACY_POLICY_URL = "https://zangormo.github.io/poker_cashier_privacy_page/"

/**
 * Everything about privacy, together: the policy, for everyone, and below it the way back into
 * the ad consent choice. That second button is only composed where the law requires it (EEA, UK,
 * Switzerland); everywhere else Google reports that no such choice exists, and a button leading
 * nowhere would only confuse. Beside "Remove ads", the other thing about ads.
 */
@Composable
private fun PrivacySection(
    showAdPrivacyOptions: Boolean,
    onOpenPolicy: () -> Unit,
    onAdPrivacyOptions: () -> Unit,
) {
    SectionLabel(
        stringResource(R.string.settings_section_privacy),
        modifier = Modifier.padding(top = 20.dp),
    )
    OutlinedButton(
        onClick = onOpenPolicy,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget),
    ) {
        Text(stringResource(R.string.settings_privacy_policy_button))
        Spacer(Modifier.width(8.dp))
        // Says before the tap that this leaves the app for the browser.
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
    if (showAdPrivacyOptions) {
        Text(
            stringResource(R.string.settings_ad_privacy_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        OutlinedButton(
            onClick = onAdPrivacyOptions,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinTouchTarget),
        ) {
            Text(stringResource(R.string.settings_ad_privacy_button))
        }
    }
}

/**
 * Opens the privacy policy in whatever browser the phone has. A phone with none at all is rare
 * but possible, and gets a message rather than a crash.
 */
private fun openPrivacyPolicy(context: Context, onNoBrowser: () -> Unit) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
    } catch (_: ActivityNotFoundException) {
        onNoBrowser()
    }
}

/**
 * The language the app speaks.
 *
 * Each option is written in its own language rather than translated, so a host who has landed
 * somewhere they cannot read can still find their way back. Choosing one restarts the screen:
 * resources are resolved as a screen is built, so there is no way to change them under it.
 */
@Composable
private fun LanguageSection(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The globe is the one sign of "language" that reads in any language, which matters most
        // to someone who has ended up in one they cannot read.
        Icon(
            Icons.Filled.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        SectionLabel(stringResource(R.string.settings_section_language))
    }
    Text(
        stringResource(R.string.settings_language_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    AppLanguage.entries.forEach { language ->
        val selected = language == current
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(language) },
                ),
            shape = MaterialTheme.shapes.small,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            border = BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SelectionIndicator(selected = selected)
                Text(
                    stringResource(language.label),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/**
 * Removing is a single tap with no confirmation: the list is a convenience, nothing recorded
 * hangs off it, and the snackbar offers the level straight back.
 */
@Composable
private fun StakeRowItem(row: StakeRow, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.label,
                modifier = Modifier.weight(1f),
                style = PokerTheme.type.numericMedium,
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(MinTouchTarget)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.settings_remove_blinds, row.label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddStakesDialog(
    editor: StakesEditor,
    onSmallBlindChange: (String) -> Unit,
    onBigBlindChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.settings_add_blinds_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CashAmountField(
                    value = editor.smallBlind,
                    onValueChange = onSmallBlindChange,
                    label = stringResource(R.string.create_small_blind),
                    accepts = acceptsSmallBlind,
                )
                CashAmountField(
                    value = editor.bigBlind,
                    onValueChange = onBigBlindChange,
                    label = stringResource(R.string.create_big_blind),
                    accepts = acceptsBigBlind,
                    imeAction = ImeAction.Done,
                )
                if (editor.error != null) {
                    Text(
                        editor.error.resolve(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = editor.canAdd) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun settingsState(extra: List<Stakes> = emptyList()) = SettingsUiState(
    isLoading = false,
    removeAds = RemoveAdsUiState(price = "€2.99"),
    stakes = (Stakes.COMMON + extra)
        .sortedWith(compareBy({ it.bigBlind.micros }, { it.smallBlind.micros }))
        .map { StakeRow(it, it.label()) },
)

@Preview(name = "Settings — blind sizes", showBackground = true, heightDp = 700)
@Composable
private fun SettingsPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsContent(
                state = settingsState(listOf(Stakes(Money(20_000), Money(40_000)))),
                language = AppLanguage.ENGLISH,
                onSelectLanguage = {},
                onAdd = {},
                onRemove = {},
                onRemoveAds = {},
                onAdPrivacyOptions = {},
                onOpenPrivacyPolicy = {},
            )
        }
    }
}

@Preview(name = "Settings — ad privacy options (EEA)", showBackground = true, heightDp = 700)
@Composable
private fun SettingsAdPrivacyPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsContent(
                state = settingsState(listOf(Stakes(Money(20_000), Money(40_000))))
                    .copy(showAdPrivacyOptions = true),
                language = AppLanguage.ENGLISH,
                onSelectLanguage = {},
                onAdd = {},
                onRemove = {},
                onRemoveAds = {},
                onAdPrivacyOptions = {},
                onOpenPrivacyPolicy = {},
            )
        }
    }
}

@Preview(name = "Settings — empty", showBackground = true, heightDp = 400)
@Composable
private fun SettingsEmptyPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsContent(SettingsUiState(isLoading = false), AppLanguage.ENGLISH, {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(name = "Settings — add blinds", showBackground = true, heightDp = 480)
@Composable
private fun AddStakesPreview() {
    PokerTrackerTheme {
        AddStakesDialog(
            editor = StakesEditor("0.02", "0.01", UiText.Raw("Big blind must be larger than the small blind")),
            onSmallBlindChange = {},
            onBigBlindChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Settings — 200% font", showBackground = true, fontScale = 2.0f, heightDp = 900)
@Composable
private fun SettingsLargeFontPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsContent(
                settingsState().copy(removeAds = RemoveAdsUiState(isPending = true)),
                AppLanguage.RUSSIAN,
                {}, {}, {}, {}, {}, {},
            )
        }
    }
}
