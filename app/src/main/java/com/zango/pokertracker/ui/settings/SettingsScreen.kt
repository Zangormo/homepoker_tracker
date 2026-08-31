package com.zango.pokertracker.ui.settings

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.locale.AppLanguage
import com.zango.pokertracker.core.locale.AppLanguageStore
import com.zango.pokertracker.core.locale.findActivity
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Stakes
import com.zango.pokertracker.ui.common.CashAmountField
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.SectionLabel
import com.zango.pokertracker.ui.common.SelectionIndicator
import com.zango.pokertracker.ui.common.resolve
import com.zango.pokertracker.ui.theme.PokerTheme
import com.zango.pokertracker.ui.theme.PokerTrackerTheme

/**
 * Settings, which so far is the stake levels the new-game picker offers.
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
    SectionLabel(stringResource(R.string.settings_section_language))
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
                )
                CashAmountField(
                    value = editor.bigBlind,
                    onValueChange = onBigBlindChange,
                    label = stringResource(R.string.create_big_blind),
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
            )
        }
    }
}

@Preview(name = "Settings — empty", showBackground = true, heightDp = 400)
@Composable
private fun SettingsEmptyPreview() {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsContent(SettingsUiState(isLoading = false), AppLanguage.ENGLISH, {}, {}, {})
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
            SettingsContent(settingsState(), AppLanguage.RUSSIAN, {}, {}, {})
        }
    }
}
