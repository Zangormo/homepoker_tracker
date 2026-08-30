package com.zango.pokertracker.ui.livegame

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.GameStatus
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.testing.FakePokerRepository
import com.zango.pokertracker.testing.TestClock
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

private const val GAME_ID = 42L
private const val START = 1_700_000_000_000L
private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

@OptIn(ExperimentalCoroutinesApi::class)
class LiveGameViewModelTest {

    private val clock = TestClock(START)
    private val repository = FakePokerRepository(clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository.roster.value = listOf(
            Player(1, "Anna", 0),
            Player(2, "Boris", 0),
            Player(3, "Chris", 0),
        )
        repository.game.value = snapshotOf(
            Fixture.seat(1, "Anna"),
            Fixture.seat(2, "Boris"),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun snapshotOf(vararg seats: com.zango.pokertracker.domain.model.Seat) = GameSnapshot(
        game = Fixture.game().copy(id = GAME_ID, startedAt = START),
        seats = seats.toList(),
    )

    private fun viewModel() = LiveGameViewModel(
        repository = repository,
        clock = clock,
        savedStateHandle = SavedStateHandle(mapOf("gameId" to GAME_ID)),
    )

    /**
     * Subscribes so `stateIn(WhileSubscribed)` is actually running, then waits for a state that
     * matches. A StateFlow replays its current value on subscription, so waiting for the shape
     * we expect is the only race-free way to read back the effect of an action.
     */
    private suspend fun LiveGameViewModel.stateWhere(
        predicate: (LiveGameUiState) -> Boolean = { true },
    ): LiveGameUiState = uiState.first { !it.isLoading && predicate(it) }

    private suspend fun LiveGameViewModel.state(): LiveGameUiState = stateWhere()

    private suspend fun LiveGameViewModel.buyInDialog(amount: String) =
        stateWhere { (it.dialog as? LiveGameDialog.AddBuyIn)?.amount == amount }
            .dialog as LiveGameDialog.AddBuyIn

    private suspend fun LiveGameViewModel.cashOutDialog(chips: String) =
        stateWhere { (it.dialog as? LiveGameDialog.CashOut)?.chips == chips }
            .dialog as LiveGameDialog.CashOut

    private suspend fun LiveGameViewModel.addPlayerDialog(
        predicate: (LiveGameDialog.AddPlayer) -> Boolean = { true },
    ) = stateWhere { (it.dialog as? LiveGameDialog.AddPlayer)?.let(predicate) == true }
        .dialog as LiveGameDialog.AddPlayer

    @Test
    fun `headline figures come from the buy-in rows`() = runTest {
        val state = viewModel().state()

        run {
            assertEquals("Thursday", state.gameName)
            assertEquals("0.005 / 0.01", state.stakes)
            assertEquals("1 chip = 0.005", state.chipValueLabel)
            assertEquals(Money(2_000_000), state.totalOnTable.cash)
            assertEquals(Chips(400), state.totalOnTable.chips)
            assertEquals(2, state.buyInCount)
            assertEquals(listOf("Anna", "Boris"), state.activeSeats.map { it.name })
        }
    }

    @Test
    fun `elapsed time is computed from the stored start, not counted`() = runTest {
        clock.now = START + 2 * HOUR + 47 * MINUTE
        assertEquals("2h 47m", viewModel().state().elapsed)
    }

    @Test
    fun `a finished game freezes its elapsed time at the moment it ended`() = runTest {
        repository.game.value = repository.game.value!!.let { snapshot ->
            snapshot.copy(
                game = snapshot.game.copy(
                    status = GameStatus.FINISHED,
                    endedAt = START + 3 * HOUR,
                ),
            )
        }
        clock.now = START + 10 * HOUR

        val state = viewModel().state()
        assertEquals("3h 0m", state.elapsed)
        assertTrue(state.isFinished)
        assertFalse(state.canEndGame)
    }

    @Test
    fun `the rebuy dialog opens prefilled with the game default`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onAddBuyIn(seatId = 1)
        val dialog = viewModel.buyInDialog("1.00")

        assertEquals("Anna", dialog.playerName)
        assertEquals(Chips(200), dialog.preview.chips)
        assertTrue(dialog.canConfirm)
    }

    @Test
    fun `a rebuy that is not a whole number of chips cannot be confirmed`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onAddBuyIn(seatId = 1)
        viewModel.onBuyInAmountChange("1.0025")
        val dialog = viewModel.buyInDialog("1.0025")

        assertEquals(
            "1.0025 is not a whole number of 0.005 chips (0.0025 left over)",
            dialog.error,
        )
        assertFalse(dialog.canConfirm)
    }

    @Test
    fun `confirming a rebuy records it and closes the dialog`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onAddBuyIn(seatId = 1)
        viewModel.onBuyInAmountChange("0.50")
        viewModel.onConfirmBuyIn()

        assertEquals(listOf("addBuyIn(1, 0.50)"), repository.writes)
        val state = viewModel.stateWhere { it.dialog == null && it.buyInCount == 3 }
        assertNull(state.dialog)
        assertEquals(Money(1_500_000), state.activeSeats.first { it.name == "Anna" }.totalBuyIn)
        assertEquals(3, state.buyInCount)
    }

    @Test
    fun `the cash-out dialog shows the cash value and the net before committing`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onCashOut(seatId = 1)
        viewModel.onChipCountChange("300")
        val dialog = viewModel.cashOutDialog("300")

        assertEquals(Money(1_500_000), dialog.cashValue)
        assertEquals(Money(1_000_000), dialog.totalBuyIn)
        assertEquals(Money(500_000), dialog.net)
        assertTrue(dialog.canConfirm)
    }

    @Test
    fun `busting out with nothing is a valid cash-out`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onCashOut(seatId = 1)
        viewModel.onChipCountChange("0")

        val dialog = viewModel.cashOutDialog("0")
        assertEquals(Money.ZERO, dialog.cashValue)
        assertEquals(Money(-1_000_000), dialog.net)
        assertTrue(dialog.canConfirm)
    }

    @Test
    fun `a fractional chip count is refused`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onCashOut(seatId = 1)
        viewModel.onChipCountChange("250.5")

        val dialog = viewModel.cashOutDialog("250.5")
        assertEquals("Chips come in whole numbers only", dialog.error)
        assertFalse(dialog.canConfirm)
    }

    @Test
    fun `cashing out moves the player out of the active list but keeps their record`() = runTest {
        clock.now = START + HOUR
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onCashOut(seatId = 1)
        viewModel.onChipCountChange("300")
        viewModel.onConfirmCashOut()

        val state = viewModel.stateWhere { it.cashedOutSeats.isNotEmpty() }
        assertEquals(listOf("Boris"), state.activeSeats.map { it.name })
        val cashedOut = state.cashedOutSeats.single()
        assertEquals("Anna", cashedOut.name)
        assertEquals(Chips(300), cashedOut.finalChips)
        assertEquals(Money(1_500_000), cashedOut.cashOutValue)
        assertEquals(Money(500_000), cashedOut.net)
        // The money is still on the table: cashing out does not remove their buy-ins.
        assertEquals(Money(2_000_000), state.totalOnTable.cash)
    }

    @Test
    fun `a cash-out can be undone`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onCashOut(seatId = 1)
        viewModel.onChipCountChange("300")
        viewModel.onConfirmCashOut()
        viewModel.onUndoCashOut(seatId = 1)

        val state = viewModel.stateWhere { it.cashedOutSeats.isEmpty() }
        assertEquals(listOf("Anna", "Boris"), state.activeSeats.map { it.name })
        assertTrue(state.cashedOutSeats.isEmpty())
        // The stale count goes with it, so nobody is left holding a number that no longer applies.
        assertNull(state.activeSeats.first { it.name == "Anna" }.finalChips)
        assertEquals(listOf("cashOut(1, 300)", "undoCashOut(1)"), repository.writes)
    }

    @Test
    fun `the add-player dialog only offers people who are not already seated`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onAddPlayer()

        val dialog = viewModel.addPlayerDialog()
        assertEquals(listOf("Chris"), dialog.candidates.map { it.name })
        assertEquals("1.00", dialog.buyIn)
        assertFalse(dialog.canConfirm)
    }

    @Test
    fun `picking from the roster and typing a new name are mutually exclusive`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onAddPlayer()

        viewModel.onSelectCandidate(3)
        assertEquals(3L, viewModel.addPlayerDialog().selectedPlayerId)

        viewModel.onNewPlayerNameChange("Dina")
        val typed = viewModel.addPlayerDialog { it.newPlayerName == "Dina" }
        assertNull(typed.selectedPlayerId)

        viewModel.onSelectCandidate(3)
        val picked = viewModel.addPlayerDialog { it.selectedPlayerId == 3L }
        assertEquals("", picked.newPlayerName)
    }

    @Test
    fun `seating a roster player mid-game gives them a buy-in`() = runTest {
        clock.now = START + HOUR
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onAddPlayer()
        viewModel.onSelectCandidate(3)
        viewModel.onConfirmAddPlayer()

        assertEquals(listOf("seatPlayer(3, 1.00)"), repository.writes)
        val state = viewModel.stateWhere { it.activeSeats.size == 3 }
        assertEquals(listOf("Anna", "Boris", "Chris"), state.activeSeats.map { it.name })
        assertEquals(Money(3_000_000), state.totalOnTable.cash)
        assertEquals(3, state.buyInCount)
    }

    @Test
    fun `a brand new player is created and seated in one go`() = runTest {
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onAddPlayer()
        viewModel.onNewPlayerNameChange("Dina")
        viewModel.onAddPlayerBuyInChange("0.50")
        viewModel.onConfirmAddPlayer()

        assertEquals(listOf("createPlayer(Dina)", "seatPlayer(1000, 0.50)"), repository.writes)
        assertEquals(
            listOf("Anna", "Boris", "Dina"),
            viewModel.stateWhere { it.activeSeats.size == 3 }.activeSeats.map { it.name },
        )
    }

    @Test
    fun `typing the name of someone already on the roster seats them rather than failing`() =
        runTest {
            val viewModel = viewModel()
            viewModel.state()
            viewModel.onAddPlayer()
            viewModel.onNewPlayerNameChange("chris")
            viewModel.onConfirmAddPlayer()

            assertEquals(listOf("seatPlayer(3, 1.00)"), repository.writes)
        }

    @Test
    fun `a repository failure surfaces as a message rather than a crash`() = runTest {
        repository.seatPlayerFailure = IllegalStateException("That player is already seated")
        val viewModel = viewModel()
        viewModel.state()

        viewModel.events.test {
            viewModel.onAddPlayer()
            viewModel.onSelectCandidate(3)
            viewModel.onConfirmAddPlayer()
            assertEquals(
                LiveGameEvent.Message("That player is already seated"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ending the game asks to navigate rather than finishing it here`() = runTest {
        val viewModel = viewModel()
        viewModel.state()

        viewModel.events.test {
            viewModel.onEndGame()
            assertEquals(LiveGameEvent.EndGame(GAME_ID), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // Nothing was written: the chip-count screen decides when the game actually ends.
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `a deleted game reports itself as missing`() = runTest {
        repository.game.value = null
        val viewModel = viewModel()

        val state = viewModel.state()
        assertTrue(state.isMissing)
        assertFalse(state.canEndGame)
    }
}
