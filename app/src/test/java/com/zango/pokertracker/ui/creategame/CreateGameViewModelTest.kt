package com.zango.pokertracker.ui.creategame

import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.testing.FakePokerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Setting a game up: the roster picker, per-player buy-in overrides, and starting the night.
 *
 * The pure validation is covered by [CreateGameFormTest]; this is about what the screen does with
 * it — which players are selected, what each of them is in for, and what reaches the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateGameViewModelTest {

    private val repository = FakePokerRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository.roster.value = listOf(
            Player(1, "Anna", 0),
            Player(2, "Boris", 0),
            Player(3, "Chris", 0),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The worked example from the spec: 0.005/0.01, chips marked 1/2, 100 big blinds in. */
    private fun viewModel() = CreateGameViewModel(repository).apply {
        onNameChange("Thursday")
        onSmallBlindChange("0.005")
        onBigBlindChange("0.01")
        onChipsPerBigBlindChange("2")
    }

    private suspend fun CreateGameViewModel.stateWhere(
        predicate: (CreateGameUiState) -> Boolean = { true },
    ) = uiState.first { it.roster.isNotEmpty() && predicate(it) }

    // -----------------------------------------------------------------------------------------
    // The roster
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the roster is offered with nobody selected`() = runTest {
        val state = viewModel().stateWhere()

        assertEquals(listOf("Anna", "Boris", "Chris"), state.roster.map { it.player.name })
        assertTrue(state.roster.none { it.isSelected })
        assertEquals(0, state.selectedCount)
        assertFalse(state.canStart)
    }

    @Test
    fun `tapping a player seats them and shows what they are in for`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)

        val row = viewModel.stateWhere { it.selectedCount == 1 }.roster.first { it.isSelected }
        assertEquals("Anna", row.player.name)
        // 100 big blinds at 0.01 is 1.00, which buys 200 chips at 0.005 each.
        assertEquals(Money(1_000_000), row.buyIn)
        assertEquals(Chips(200), row.chips)
        assertFalse(row.isOverridden)
    }

    @Test
    fun `tapping again takes them back out`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.selectedCount == 1 }
        viewModel.onTogglePlayer(1)

        assertEquals(0, viewModel.stateWhere { it.selectedCount == 0 }.selectedCount)
    }

    @Test
    fun `an unselected player shows no buy-in at all`() = runTest {
        val row = viewModel().stateWhere().roster.first { it.player.id == 1L }

        assertNull(row.buyIn)
        assertNull(row.chips)
    }

    @Test
    fun `the table total is what every seated player is putting up between them`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.onTogglePlayer(2)

        val state = viewModel.stateWhere { it.selectedCount == 2 }
        assertEquals(Money(2_000_000), state.totalOnTable.cash)
        assertEquals(Chips(400), state.totalOnTable.chips)
    }

    @Test
    fun `the total is empty until somebody is seated`() = runTest {
        assertTrue(viewModel().stateWhere().totalOnTable.isEmpty)
    }

    // -----------------------------------------------------------------------------------------
    // Per-player overrides
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an override changes one player's buy-in and leaves the others on the default`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.onTogglePlayer(2)
        viewModel.stateWhere { it.selectedCount == 2 }

        viewModel.onEditOverride(1)
        viewModel.onOverrideModeChange(BuyInMode.CASH)
        viewModel.onOverrideCashChange("2.50")
        viewModel.onApplyOverride()

        val state = viewModel.stateWhere { it.overrideEditor == null }
        val anna = state.roster.first { it.player.id == 1L }
        val boris = state.roster.first { it.player.id == 2L }
        assertEquals(Money(2_500_000), anna.buyIn)
        assertTrue(anna.isOverridden)
        assertEquals(Money(1_000_000), boris.buyIn)
        assertFalse(boris.isOverridden)
        assertEquals(Money(3_500_000), state.totalOnTable.cash)
    }

    @Test
    fun `an override can be given in big blinds instead of cash`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.selectedCount == 1 }

        viewModel.onEditOverride(1)
        viewModel.onOverrideBigBlindsChange("50")
        viewModel.onApplyOverride()

        val row = viewModel.stateWhere { it.overrideEditor == null }.roster.first { it.isSelected }
        assertEquals(Money(500_000), row.buyIn)
    }

    /**
     * The editor reopens in the terms the amount was set in: a round multiple of the big blind
     * reads back as big blinds, anything else as the cash figure that was typed.
     */
    @Test
    fun `an odd amount reopens as the cash figure that was typed`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.selectedCount == 1 }

        // 2.555 is a whole 511 chips but not a whole multiple of the 0.01 big blind, so there
        // is no big-blind figure to read it back as.
        viewModel.onEditOverride(1)
        viewModel.onOverrideModeChange(BuyInMode.CASH)
        viewModel.onOverrideCashChange("2.555")
        viewModel.onApplyOverride()
        viewModel.stateWhere { it.overrideEditor == null }

        viewModel.onEditOverride(1)
        val editor = viewModel.stateWhere { it.overrideEditor != null }.overrideEditor!!
        assertEquals(BuyInMode.CASH, editor.mode)
        assertEquals("2.555", editor.cash)
    }

    @Test
    fun `a round amount reopens in big blinds, which is how hosts think of it`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.selectedCount == 1 }

        viewModel.onEditOverride(1)
        viewModel.onOverrideModeChange(BuyInMode.CASH)
        viewModel.onOverrideCashChange("2.50")
        viewModel.onApplyOverride()
        viewModel.stateWhere { it.overrideEditor == null }

        viewModel.onEditOverride(1)
        val editor = viewModel.stateWhere { it.overrideEditor != null }.overrideEditor!!
        assertEquals(BuyInMode.BIG_BLINDS, editor.mode)
        assertEquals("250", editor.bigBlinds)
    }

    @Test
    fun `an override that is not a whole number of chips cannot be applied`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.selectedCount == 1 }

        viewModel.onEditOverride(1)
        viewModel.onOverrideModeChange(BuyInMode.CASH)
        viewModel.onOverrideCashChange("1.002")

        val editor = viewModel.stateWhere { it.overrideEditor?.error != null }.overrideEditor!!
        assertEquals(UiText.of(R.string.error_not_whole_chips_short), editor.error)
        assertFalse(editor.canApply)
    }

    @Test
    fun `clearing an override puts the player back on the game default`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.selectedCount == 1 }

        viewModel.onEditOverride(1)
        viewModel.onOverrideModeChange(BuyInMode.CASH)
        viewModel.onOverrideCashChange("2.50")
        viewModel.onApplyOverride()
        viewModel.stateWhere { it.roster.any { row -> row.isOverridden } }

        viewModel.onClearOverride(1)

        val row = viewModel.stateWhere { it.roster.none { row -> row.isOverridden } }
            .roster.first { it.isSelected }
        assertEquals(Money(1_000_000), row.buyIn)
    }

    @Test
    fun `dismissing the editor changes nothing`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.selectedCount == 1 }

        viewModel.onEditOverride(1)
        viewModel.onOverrideCashChange("9.99")
        viewModel.onDismissOverride()

        val row = viewModel.stateWhere { it.overrideEditor == null }.roster.first { it.isSelected }
        assertEquals(Money(1_000_000), row.buyIn)
        assertFalse(row.isOverridden)
    }

    /**
     * A player taken out by mistake and put back comes in on the standard buy-in rather than
     * silently keeping a figure the host can no longer see.
     */
    @Test
    fun `deselecting a player drops the override they had`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.selectedCount == 1 }
        viewModel.onEditOverride(1)
        viewModel.onOverrideModeChange(BuyInMode.CASH)
        viewModel.onOverrideCashChange("2.50")
        viewModel.onApplyOverride()
        viewModel.stateWhere { it.roster.any { row -> row.isOverridden } }

        viewModel.onTogglePlayer(1)
        viewModel.onTogglePlayer(1)

        val row = viewModel.stateWhere { it.selectedCount == 1 }.roster.first { it.isSelected }
        assertFalse(row.isOverridden)
        assertEquals(Money(1_000_000), row.buyIn)
    }

    // -----------------------------------------------------------------------------------------
    // Adding someone new
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a player added here is seated straight away, as the host expects`() = runTest {
        val viewModel = viewModel()
        viewModel.onNewPlayerNameChange("Dana")
        viewModel.onAddNewPlayer()

        val state = viewModel.stateWhere { it.roster.any { row -> row.player.name == "Dana" } }
        assertTrue(state.roster.first { it.player.name == "Dana" }.isSelected)
        assertEquals("", state.newPlayerName)
    }

    /** Already on the roster is not a failure: seat them and say so. */
    @Test
    fun `adding a name that is already there selects them instead of refusing`() = runTest {
        val viewModel = viewModel()
        viewModel.onNewPlayerNameChange("anna")
        viewModel.onAddNewPlayer()

        val state = viewModel.stateWhere { it.selectedCount == 1 }
        assertTrue(state.roster.first { it.player.id == 1L }.isSelected)
        assertEquals(3, state.roster.size)
        assertNull(state.newPlayerError)
    }

    @Test
    fun `an empty name is refused where it was typed`() = runTest {
        val viewModel = viewModel()
        viewModel.onNewPlayerNameChange("   ")
        viewModel.onAddNewPlayer()

        assertEquals(
            UiText.of(R.string.error_name_required),
            viewModel.stateWhere { it.newPlayerError != null }.newPlayerError,
        )
    }

    // -----------------------------------------------------------------------------------------
    // Starting
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a complete form can start and hands over exactly what was set up`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.onTogglePlayer(2)

        val state = viewModel.stateWhere { it.canStart }
        val setup = state.validation.setup!!
        assertEquals("Thursday", setup.name)
        assertEquals(Money(5_000), setup.smallBlind)
        assertEquals(Money(10_000), setup.bigBlind)
        assertEquals(5_000L, setup.chipRate.chipValueMicros)
        assertEquals(listOf(1L, 2L), setup.entries.map { it.playerId })
        assertTrue(setup.entries.all { it.buyIn == Money(1_000_000) })
    }

    @Test
    fun `starting reports the new game so the screen can move on to it`() = runTest {
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.canStart }

        viewModel.onStartGame()

        assertEquals(listOf("createGame(Thursday)"), repository.writes)
        assertTrue(viewModel.events.first() is CreateGameEvent.GameStarted)
    }

    @Test
    fun `a form that is not ready cannot be started`() = runTest {
        val viewModel = viewModel()
        // Nobody seated yet.
        viewModel.stateWhere()

        viewModel.onStartGame()

        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `a failure to start is reported and the button comes back`() = runTest {
        repository.createGameFailure = IllegalStateException("Disk is full")
        val viewModel = viewModel()
        viewModel.onTogglePlayer(1)
        viewModel.stateWhere { it.canStart }

        viewModel.onStartGame()

        val event = viewModel.events.first()
        assertTrue(event is CreateGameEvent.Message)
        assertEquals(
            UiText.of(R.string.error_could_not_start),
            (event as CreateGameEvent.Message).text,
        )
        // Not left spinning: the host can fix the problem and try again.
        assertFalse(viewModel.stateWhere { !it.isStarting }.isStarting)
        assertTrue(viewModel.stateWhere().canStart)
    }
}
