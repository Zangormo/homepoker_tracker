package com.homepoker_tracker.ui.creategame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homepoker_tracker.core.money.ChipRate
import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.domain.model.Player
import com.homepoker_tracker.ui.common.AmountPreview
import com.homepoker_tracker.ui.common.CashAmountField
import com.homepoker_tracker.ui.common.CashAmountText
import com.homepoker_tracker.ui.common.CashToChipsRow
import com.homepoker_tracker.ui.common.ChipAmountField
import com.homepoker_tracker.ui.common.ChipAmountText
import com.homepoker_tracker.ui.common.FormSection
import com.homepoker_tracker.ui.common.MinTouchTarget
import com.homepoker_tracker.ui.common.PokerTextField
import com.homepoker_tracker.ui.common.SectionLabel
import com.homepoker_tracker.ui.common.SegmentedChoice
import com.homepoker_tracker.ui.common.SelectableCard
import com.homepoker_tracker.ui.common.SelectionIndicator
import com.homepoker_tracker.ui.theme.PokerTheme
import com.homepoker_tracker.ui.theme.PokerTrackerTheme

/** The fields a failed submit can land on, in the order the host reads them. */
private enum class FormField { NAME, SMALL_BLIND, BIG_BLIND, CHIP_VALUE, BUY_IN, ROUNDING, PLAYERS }

private fun CreateGameValidation.firstProblem(): FormField? = when {
    nameError != null -> FormField.NAME
    smallBlindError != null -> FormField.SMALL_BLIND
    bigBlindError != null -> FormField.BIG_BLIND
    chipValueError != null -> FormField.CHIP_VALUE
    buyInError != null -> FormField.BUY_IN
    payoutRoundingError != null -> FormField.ROUNDING
    playersError != null -> FormField.PLAYERS
    else -> null
}

@Composable
fun CreateGameScreen(
    onBack: () -> Unit,
    onGameStarted: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val actions = rememberCreateGameActions(viewModel)
    val keyboard = LocalSoftwareKeyboardController.current

    // Set the first time the host tries to start a game that is not ready. From then on every
    // unfilled required field shows its problem, not only the ones already visited.
    var revealAllProblems by rememberSaveable { mutableStateOf(false) }
    val focusRequesters = remember { FormField.entries.associateWith { FocusRequester() } }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateGameEvent.GameStarted -> onGameStarted(event.gameId)
                is CreateGameEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("New game") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            StartGameBar(
                state = state,
                onStart = {
                    if (state.canStart) {
                        keyboard?.hide()
                        actions.onStartGame()
                    } else {
                        revealAllProblems = true
                        // Focus moves to the first thing that is wrong; taking focus inside a
                        // scrolling column brings it into view as a side effect.
                        state.validation.firstProblem()?.let { focusRequesters[it]?.requestFocus() }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        CreateGameContent(
            state = state,
            actions = actions,
            revealAllProblems = revealAllProblems,
            focusRequesters = focusRequesters,
            modifier = Modifier.padding(padding),
        )
    }

    state.overrideEditor?.let { editor ->
        OverrideDialog(editor = editor, actions = actions)
    }
}

/** Every callback the screen needs, so the content is previewable without a ViewModel. */
data class CreateGameActions(
    val onNameChange: (String) -> Unit = {},
    val onSmallBlindChange: (String) -> Unit = {},
    val onBigBlindChange: (String) -> Unit = {},
    val onDeriveChipValueChange: (Boolean) -> Unit = {},
    val onChipValueChange: (String) -> Unit = {},
    val onChipsPerBigBlindChange: (String) -> Unit = {},
    val onBuyInModeChange: (BuyInMode) -> Unit = {},
    val onBuyInBigBlindsChange: (String) -> Unit = {},
    val onBuyInCashChange: (String) -> Unit = {},
    val onPayoutRoundingChange: (String) -> Unit = {},
    val onTogglePlayer: (Long) -> Unit = {},
    val onEditOverride: (Long) -> Unit = {},
    val onClearOverride: (Long) -> Unit = {},
    val onNewPlayerNameChange: (String) -> Unit = {},
    val onAddNewPlayer: () -> Unit = {},
    val onOverrideModeChange: (BuyInMode) -> Unit = {},
    val onOverrideBigBlindsChange: (String) -> Unit = {},
    val onOverrideCashChange: (String) -> Unit = {},
    val onApplyOverride: () -> Unit = {},
    val onDismissOverride: () -> Unit = {},
    val onStartGame: () -> Unit = {},
)

@Composable
private fun rememberCreateGameActions(viewModel: CreateGameViewModel): CreateGameActions =
    remember(viewModel) {
        CreateGameActions(
            onNameChange = viewModel::onNameChange,
            onSmallBlindChange = viewModel::onSmallBlindChange,
            onBigBlindChange = viewModel::onBigBlindChange,
            onDeriveChipValueChange = viewModel::onDeriveChipValueChange,
            onChipValueChange = viewModel::onChipValueChange,
            onChipsPerBigBlindChange = viewModel::onChipsPerBigBlindChange,
            onBuyInModeChange = viewModel::onBuyInModeChange,
            onBuyInBigBlindsChange = viewModel::onBuyInBigBlindsChange,
            onBuyInCashChange = viewModel::onBuyInCashChange,
            onPayoutRoundingChange = viewModel::onPayoutRoundingChange,
            onTogglePlayer = viewModel::onTogglePlayer,
            onEditOverride = viewModel::onEditOverride,
            onClearOverride = viewModel::onClearOverride,
            onNewPlayerNameChange = viewModel::onNewPlayerNameChange,
            onAddNewPlayer = viewModel::onAddNewPlayer,
            onOverrideModeChange = viewModel::onOverrideModeChange,
            onOverrideBigBlindsChange = viewModel::onOverrideBigBlindsChange,
            onOverrideCashChange = viewModel::onOverrideCashChange,
            onApplyOverride = viewModel::onApplyOverride,
            onDismissOverride = viewModel::onDismissOverride,
            onStartGame = viewModel::onStartGame,
        )
    }

@Composable
private fun CreateGameContent(
    state: CreateGameUiState,
    actions: CreateGameActions,
    revealAllProblems: Boolean,
    focusRequesters: Map<FormField, FocusRequester>,
    modifier: Modifier = Modifier,
) {
    val focus: (FormField) -> Modifier = { field ->
        Modifier.focusRequester(focusRequesters.getValue(field))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        FormSection("Identity") {
            PokerTextField(
                value = state.form.name,
                onValueChange = actions.onNameChange,
                label = "Game name",
                required = true,
                error = state.validation.nameError,
                forceShowError = revealAllProblems,
                modifier = focus(FormField.NAME),
            )
        }

        StakesSection(state, actions, revealAllProblems, focus)
        BuyInSection(state, actions, revealAllProblems, focus)

        FormSection(
            title = "Payout rounding",
            subtitle = "The smallest note or coin players will hand each other.",
        ) {
            CashAmountField(
                value = state.form.payoutRounding,
                onValueChange = actions.onPayoutRoundingChange,
                label = "Round payments to",
                required = true,
                error = state.validation.payoutRoundingError,
                forceShowError = revealAllProblems,
                imeAction = ImeAction.Done,
                modifier = focus(FormField.ROUNDING),
            )
        }

        PlayersSection(
            state = state,
            actions = actions,
            revealAllProblems = revealAllProblems,
            focusRequester = focusRequesters.getValue(FormField.PLAYERS),
        )
    }
}

@Composable
private fun StakesSection(
    state: CreateGameUiState,
    actions: CreateGameActions,
    revealAllProblems: Boolean,
    focus: (FormField) -> Modifier,
) {
    FormSection("Stakes") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CashAmountField(
                value = state.form.smallBlind,
                onValueChange = actions.onSmallBlindChange,
                label = "Small blind",
                required = true,
                error = state.validation.smallBlindError,
                forceShowError = revealAllProblems,
                modifier = focus(FormField.SMALL_BLIND).then(Modifier.weight(1f)),
            )
            CashAmountField(
                value = state.form.bigBlind,
                onValueChange = actions.onBigBlindChange,
                label = "Big blind",
                required = true,
                error = state.validation.bigBlindError,
                forceShowError = revealAllProblems,
                modifier = focus(FormField.BIG_BLIND).then(Modifier.weight(1f)),
            )
        }

        HorizontalDivider(color = PokerTheme.colors.divider)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Work the chip value out from the markings",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Chips marked 1/2 on a 0.005/0.01 table make one chip worth 0.005.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.form.deriveChipValue,
                onCheckedChange = actions.onDeriveChipValueChange,
            )
        }

        if (state.form.deriveChipValue) {
            ChipAmountField(
                value = state.form.chipsPerBigBlind,
                onValueChange = actions.onChipsPerBigBlindChange,
                label = "Big blind, in chips",
                required = true,
                error = state.validation.chipValueError,
                forceShowError = revealAllProblems,
                modifier = focus(FormField.CHIP_VALUE),
            )
        } else {
            CashAmountField(
                value = state.form.chipValue,
                onValueChange = actions.onChipValueChange,
                label = "Cash value of one chip",
                required = true,
                error = state.validation.chipValueError,
                forceShowError = revealAllProblems,
                modifier = focus(FormField.CHIP_VALUE),
            )
        }

        state.derivedChipValue?.let { value ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "One chip is worth",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CashAmountText(value, style = PokerTheme.type.numericSmall)
            }
        }
    }
}

@Composable
private fun BuyInSection(
    state: CreateGameUiState,
    actions: CreateGameActions,
    revealAllProblems: Boolean,
    focus: (FormField) -> Modifier,
) {
    FormSection("Buy-in") {
        SegmentedChoice(
            options = listOf(BuyInMode.BIG_BLINDS, BuyInMode.CASH),
            selected = state.form.buyInMode,
            onSelect = actions.onBuyInModeChange,
            label = { if (it == BuyInMode.BIG_BLINDS) "Big blinds" else "Cash" },
            modifier = Modifier.fillMaxWidth(),
        )

        when (state.form.buyInMode) {
            BuyInMode.BIG_BLINDS -> PokerTextField(
                value = state.form.buyInBigBlinds,
                onValueChange = actions.onBuyInBigBlindsChange,
                label = "Buy-in, in big blinds",
                required = true,
                error = state.validation.buyInError,
                forceShowError = revealAllProblems,
                modifier = focus(FormField.BUY_IN),
            )

            BuyInMode.CASH -> CashAmountField(
                value = state.form.buyInCash,
                onValueChange = actions.onBuyInCashChange,
                label = "Buy-in",
                required = true,
                error = state.validation.buyInError,
                forceShowError = revealAllProblems,
                modifier = focus(FormField.BUY_IN),
            )
        }

        BuyInReadout(
            bigBlinds = state.defaultBuyInBigBlinds,
            preview = state.defaultBuyInPreview,
        )
    }
}

/**
 * The moment the host confirms the table is set up the way they think it is: one buy-in stated in
 * all three units they will hear during the game.
 */
@Composable
private fun BuyInReadout(bigBlinds: Long?, preview: AmountPreview) {
    val hasLeftOver = preview.leftOver != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (hasLeftOver) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Every player starts with")
            if (preview.isEmpty) {
                Text(
                    "Fill in the blinds and the chip value to see this.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (bigBlinds != null) {
                        Text(
                            "$bigBlinds BB",
                            style = PokerTheme.type.numericLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Equals()
                    }
                    CashAmountText(preview.cash, style = PokerTheme.type.numericLarge)
                    Equals()
                    ChipAmountText(preview.chips, style = PokerTheme.type.numericLarge)
                }
            }
            preview.leftOver?.let {
                Text(
                    "${it.format()} of that does not fit into whole chips. " +
                        "Change the buy-in or the chip value.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun Equals() {
    Text(
        "=",
        style = PokerTheme.type.numericLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayersSection(
    state: CreateGameUiState,
    actions: CreateGameActions,
    revealAllProblems: Boolean,
    focusRequester: FocusRequester,
) {
    FormSection(
        title = "Players",
        subtitle = "Tap to pick who is playing. Tap again to drop anyone who did not show up.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PokerTextField(
                value = state.newPlayerName,
                onValueChange = actions.onNewPlayerNameChange,
                label = "Add someone new",
                error = state.newPlayerError,
                imeAction = ImeAction.Done,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            FilledIconButton(
                onClick = actions.onAddNewPlayer,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(MinTouchTarget),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add player to the roster")
            }
        }

        val playersError = state.validation.playersError
        if (revealAllProblems && playersError != null) {
            Text(
                playersError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (!state.hasRoster) {
            Text(
                "Nobody on the roster yet. Add the first player above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.roster.forEach { row ->
            RosterItem(
                row = row,
                onToggle = { actions.onTogglePlayer(row.player.id) },
                onEdit = { actions.onEditOverride(row.player.id) },
            )
        }

        if (state.selectedCount > 0) {
            Text(
                "${state.selectedCount} playing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RosterItem(row: RosterRow, onToggle: () -> Unit, onEdit: () -> Unit) {
    SelectableCard(selected = row.isSelected, onClick = onToggle) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectionIndicator(row.isSelected)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(row.player.name, style = MaterialTheme.typography.titleMedium)
                    if (row.isOverridden) {
                        Text(
                            "OVERRIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                when {
                    row.error != null -> Text(
                        row.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    row.isSelected && row.buyIn != null -> CashToChipsRow(
                        cash = row.buyIn,
                        chips = row.chips,
                        style = PokerTheme.type.numericSmall,
                    )

                    row.isSelected -> Text(
                        "Finish the stakes to see the buy-in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (row.isSelected) {
                IconButton(onClick = onEdit, modifier = Modifier.size(MinTouchTarget)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Change the buy-in for ${row.player.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StartGameBar(state: CreateGameUiState, onStart: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.totalOnTable.isEmpty) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("On the table at kick-off")
                    CashToChipsRow(
                        cash = state.totalOnTable.cash,
                        chips = state.totalOnTable.chips,
                        style = PokerTheme.type.numericMedium,
                    )
                }
            }
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MinTouchTarget),
            ) {
                if (state.isStarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Start game")
                }
            }
        }
    }
}

@Composable
private fun OverrideDialog(editor: OverrideEditor, actions: CreateGameActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissOverride,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Buy-in for ${editor.playerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedChoice(
                    options = listOf(BuyInMode.BIG_BLINDS, BuyInMode.CASH),
                    selected = editor.mode,
                    onSelect = actions.onOverrideModeChange,
                    label = { if (it == BuyInMode.BIG_BLINDS) "Big blinds" else "Cash" },
                    modifier = Modifier.fillMaxWidth(),
                )
                when (editor.mode) {
                    BuyInMode.BIG_BLINDS -> PokerTextField(
                        value = editor.bigBlinds,
                        onValueChange = actions.onOverrideBigBlindsChange,
                        label = "Big blinds",
                        error = editor.error,
                        forceShowError = true,
                    )

                    BuyInMode.CASH -> CashAmountField(
                        value = editor.cash,
                        onValueChange = actions.onOverrideCashChange,
                        label = "Cash",
                        error = editor.error,
                        forceShowError = true,
                        imeAction = ImeAction.Done,
                    )
                }
                CashToChipsRow(cash = editor.preview.cash, chips = editor.preview.chips)
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onApplyOverride, enabled = editor.canApply) { Text("Set") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { actions.onClearOverride(editor.playerId) }) {
                    Text("Use default")
                }
                TextButton(onClick = actions.onDismissOverride) { Text("Cancel") }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun previewPlayer(id: Long, name: String) = Player(id, name, createdAt = 0)

private val FilledForm = CreateGameForm(
    name = "Thursday",
    smallBlind = "0.005",
    bigBlind = "0.01",
    chipsPerBigBlind = "2",
    buyInBigBlinds = "100",
    selection = linkedMapOf(1L to null, 2L to Money(500_000)),
)

private fun filledState() = CreateGameUiState(
    form = FilledForm,
    validation = FilledForm.validate(),
    roster = listOf(
        RosterRow(previewPlayer(1, "Anna"), true, Money(1_000_000), Chips(200)),
        RosterRow(previewPlayer(2, "Boris"), true, Money(500_000), Chips(100), isOverridden = true),
        RosterRow(previewPlayer(3, "Chris"), false),
    ),
    derivedChipValue = Money(5_000),
    defaultBuyInPreview = AmountPreview.of(Money(1_000_000), ChipRate(5_000)),
    defaultBuyInBigBlinds = 100,
    totalOnTable = AmountPreview.of(Money(1_500_000), ChipRate(5_000)),
)

private val BrokenForm = CreateGameForm(
    name = "",
    smallBlind = "0.02",
    bigBlind = "0.01",
    chipsPerBigBlind = "",
    buyInBigBlinds = "100",
)

private fun problemState() = CreateGameUiState(
    form = BrokenForm,
    validation = BrokenForm.validate(),
    roster = listOf(
        RosterRow(previewPlayer(1, "Anna"), false),
        RosterRow(previewPlayer(2, "Boris"), false),
    ),
)

@Composable
private fun PreviewShell(state: CreateGameUiState, revealAllProblems: Boolean) {
    PokerTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CreateGameContent(
                state = state,
                actions = CreateGameActions(),
                revealAllProblems = revealAllProblems,
                focusRequesters = FormField.entries.associateWith { FocusRequester() },
            )
        }
    }
}

@Preview(name = "Create game — filled", showBackground = true, heightDp = 1500)
@Composable
private fun CreateGameFilledPreview() = PreviewShell(filledState(), revealAllProblems = false)

@Preview(name = "Create game — submit with gaps", showBackground = true, heightDp = 1500)
@Composable
private fun CreateGameProblemPreview() = PreviewShell(problemState(), revealAllProblems = true)

@Preview(name = "Create game — 200% font", showBackground = true, fontScale = 2.0f, heightDp = 2200)
@Composable
private fun CreateGameLargeFontPreview() = PreviewShell(filledState(), revealAllProblems = false)
