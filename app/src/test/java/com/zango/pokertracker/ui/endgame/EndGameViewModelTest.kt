package com.zango.pokertracker.ui.endgame

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.Seat
import com.zango.pokertracker.domain.model.reconcile
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

/** Sentences the host reads when the chips do not add up. Pure, so it is checked directly. */
class ReconciliationHeadlineTest {

    private fun snapshot(vararg seats: Seat) = GameSnapshot(Fixture.game(), seats.toList())

    @Test
    fun `a balanced table says so`() {
        val headline = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(150)),
        ).reconcile().headline()

        assertEquals("Every chip is accounted for", headline)
    }

    @Test
    fun `missing chips are named in chips and in cash`() {
        // The spec's example: 12 chips gone from a 0.005 table.
        val headline = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(138)),
        ).reconcile().headline()

        assertEquals("12 chips unaccounted for — worth 0.06", headline)
    }

    @Test
    fun `surplus chips are worded differently because they mean a different mistake`() {
        val headline = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(162)),
        ).reconcile().headline()

        assertEquals("12 chips more than were bought in — worth 0.06", headline)
    }

    @Test
    fun `uncounted players are reported before any arithmetic`() {
        assertEquals(
            "1 player still needs a chip count",
            snapshot(
                Fixture.seat(1, "Anna", finalChips = Chips(400)),
                Fixture.seat(2, "Boris"),
            ).reconcile().headline(),
        )
        assertEquals(
            "2 players still need a chip count",
            snapshot(Fixture.seat(1, "Anna"), Fixture.seat(2, "Boris")).reconcile().headline(),
        )
    }

    @Test
    fun `buy-ins that are not whole chips are called out as a settings problem`() {
        val headline = snapshot(
            Fixture.seat(1, "Anna", buyIns = listOf(Money(1_000_001)), finalChips = Chips(200)),
        ).reconcile().headline()

        assertEquals(
            "Buy-ins include 0.000001 that is not a whole number of chips",
            headline,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EndGameViewModelTest {

    private val clock = TestClock(1_000L)
    private val repository = FakePokerRepository(clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository.game.value = GameSnapshot(
            game = Fixture.game().copy(id = GAME_ID),
            seats = listOf(Fixture.seat(1, "Anna"), Fixture.seat(2, "Boris")),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = EndGameViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(mapOf("gameId" to GAME_ID)),
    )

    private suspend fun EndGameViewModel.stateWhere(
        predicate: (EndGameUiState) -> Boolean = { true },
    ): EndGameUiState = uiState.first { !it.isLoading && predicate(it) }

    private suspend fun EndGameViewModel.countAll(anna: String, boris: String): EndGameUiState {
        stateWhere()
        onCountChange(1, anna)
        onCountChange(2, boris)
        return stateWhere { state -> state.counts.all { it.isCounted } }
    }

    @Test
    fun `every seat gets a field, with counts taken during play already filled in`() = runTest {
        repository.game.value = repository.game.value!!.let { snapshot ->
            snapshot.copy(
                seats = listOf(
                    Fixture.seat(1, "Anna", finalChips = Chips(250), cashedOutAt = 900L),
                    Fixture.seat(2, "Boris"),
                ),
            )
        }

        val state = viewModel().stateWhere()

        assertEquals(listOf("Anna", "Boris"), state.counts.map { it.name })
        assertEquals("250", state.counts[0].text)
        assertTrue(state.counts[0].wasCashedOut)
        assertEquals("", state.counts[1].text)
        assertFalse(state.counts[1].wasCashedOut)
    }

    @Test
    fun `a count is saved as it is typed, not held until the end`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onCountChange(1, "250")

        assertEquals(listOf("setFinalChipCount(1, 250)"), repository.writes)
        val row = viewModel.stateWhere { it.counts[0].isCounted }.counts[0]
        assertEquals(Chips(250), row.chips)
        assertEquals(Money(1_250_000), row.cashOutValue)
        assertEquals(Money(250_000), row.net)
    }

    @Test
    fun `clearing a field marks the stack uncounted again`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onCountChange(1, "250")
        viewModel.onCountChange(1, "")

        assertEquals(
            listOf("setFinalChipCount(1, 250)", "setFinalChipCount(1, null)"),
            repository.writes,
        )
        assertNull(viewModel.stateWhere { !it.counts[0].isCounted }.counts[0].chips)
    }

    @Test
    fun `half-typed nonsense shows an error without destroying the saved count`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onCountChange(1, "250")
        viewModel.onCountChange(1, "250x")

        // Only the first write happened: the stored 250 survives until real digits replace it.
        assertEquals(listOf("setFinalChipCount(1, 250)"), repository.writes)
        val row = viewModel.stateWhere { it.counts[0].error != null }.counts[0]
        assertEquals("Enter Chip count as a whole number", row.error)
    }

    @Test
    fun `a fractional count is refused`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onCountChange(1, "250.5")

        val row = viewModel.stateWhere { it.counts[0].error != null }.counts[0]
        assertEquals("Chips come in whole numbers only", row.error)
    }

    @Test
    fun `the game cannot be ended while a stack is still uncounted`() = runTest {
        val viewModel = viewModel()
        val state = viewModel.stateWhere()

        assertFalse(state.canFinish)
        assertEquals("2 players still need a chip count", state.reconciliation?.headline)

        viewModel.onFinish()
        assertTrue(repository.writes.none { it.startsWith("endGame") })
    }

    @Test
    fun `a balanced game finishes without asking anything`() = runTest {
        val viewModel = viewModel()
        val state = viewModel.countAll(anna = "250", boris = "150")

        assertTrue(state.canFinish)
        assertTrue(state.reconciliation!!.isClean)

        viewModel.events.test {
            viewModel.onFinish()
            assertEquals(EndGameEvent.Finished(GAME_ID), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(repository.writes.contains("endGame($GAME_ID)"))
        assertFalse(viewModel.stateWhere { it.alreadyFinished }.canFinish)
    }

    @Test
    fun `a mismatch is not accepted silently`() = runTest {
        val viewModel = viewModel()
        val state = viewModel.countAll(anna = "250", boris = "138")

        assertEquals(
            "12 chips unaccounted for — worth 0.06",
            state.reconciliation?.headline,
        )
        // Finishing is still allowed, but only after the host is told and agrees.
        assertTrue(state.canFinish)

        viewModel.onFinish()
        assertTrue(viewModel.stateWhere { it.isConfirmingMismatch }.isConfirmingMismatch)
        assertTrue(repository.writes.none { it.startsWith("endGame") })
    }

    @Test
    fun `the host can override a mismatch and proceed`() = runTest {
        val viewModel = viewModel()
        viewModel.countAll(anna = "250", boris = "138")
        viewModel.onFinish()

        viewModel.events.test {
            viewModel.onConfirmMismatch()
            assertEquals(EndGameEvent.Finished(GAME_ID), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(repository.writes.contains("endGame($GAME_ID)"))
    }

    @Test
    fun `backing out of the mismatch leaves the game running`() = runTest {
        val viewModel = viewModel()
        viewModel.countAll(anna = "250", boris = "138")
        viewModel.onFinish()
        viewModel.onDismissMismatch()

        val state = viewModel.stateWhere { !it.isConfirmingMismatch }
        assertFalse(state.alreadyFinished)
        assertTrue(repository.writes.none { it.startsWith("endGame") })
    }

    @Test
    fun `the results table follows the counts as they are entered`() = runTest {
        val viewModel = viewModel()
        val state = viewModel.countAll(anna = "300", boris = "100")

        val anna = state.results.first { it.name == "Anna" }
        assertEquals(Money(1_000_000), anna.totalBuyIn)
        assertEquals(Chips(300), anna.finalChips)
        assertEquals(Money(1_500_000), anna.cashOut)
        assertEquals(Money(500_000), anna.net)

        val boris = state.results.first { it.name == "Boris" }
        assertEquals(Money(-500_000), boris.net)
    }

    @Test
    fun `a finished game is read-only`() = runTest {
        val viewModel = viewModel()
        viewModel.countAll(anna = "250", boris = "150")
        viewModel.onFinish()

        val state = viewModel.stateWhere { it.alreadyFinished }
        assertTrue(state.alreadyFinished)
        assertFalse(state.canFinish)
    }

    @Test
    fun `a deleted game reports itself as missing`() = runTest {
        repository.game.value = null
        assertTrue(viewModel().stateWhere().isMissing)
    }
}
