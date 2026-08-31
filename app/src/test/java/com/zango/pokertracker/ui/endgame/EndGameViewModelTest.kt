package com.zango.pokertracker.ui.endgame

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.zango.pokertracker.R
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.text.UiText
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
/** The three headline shapes, written once so each test reads as the case it is about. */
private val BALANCED = UiText.of(R.string.end_headline_balanced)

private fun missing(chips: Long, cash: String) = UiText.of(
    R.string.end_headline_missing,
    UiText.plural(R.plurals.chip_count, chips.toInt(), chips),
    cash,
)

private fun surplus(chips: Long, cash: String) = UiText.of(
    R.string.end_headline_surplus,
    UiText.plural(R.plurals.chip_count, chips.toInt(), chips),
    cash,
)

private fun needsCounts(players: Int) =
    UiText.plural(R.plurals.end_headline_needs_counts, players, players)

private fun balancedWithBlanks(blanks: Int) = UiText.of(
    R.string.end_headline_with_blanks,
    BALANCED,
    UiText.plural(R.plurals.empty_stack_count, blanks, blanks),
)

class ReconciliationHeadlineTest {

    private fun snapshot(vararg seats: Seat) = GameSnapshot(Fixture.game(), seats.toList())

    @Test
    fun `a balanced table says so`() {
        val headline = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(150)),
        ).reconcile().headline()

        assertEquals(BALANCED, headline)
    }

    @Test
    fun `missing chips are named in chips and in cash`() {
        // The spec's example: 12 chips gone from a 0.005 table.
        val headline = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(138)),
        ).reconcile().headline()

        assertEquals(missing(12, "0.06"), headline)
    }

    @Test
    fun `surplus chips are worded differently because they mean a different mistake`() {
        val headline = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(162)),
        ).reconcile().headline()

        assertEquals(surplus(12, "0.06"), headline)
    }

    @Test
    fun `a blank stack the totals prove is empty says so instead of asking`() {
        // Anna holds every chip the table bought in, so Boris cannot be holding any.
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(400)),
            Fixture.seat(2, "Boris"),
        ).reconcile()

        assertTrue(result.uncountedAreImpliedZero)
        assertEquals(balancedWithBlanks(1), result.headline())
    }

    @Test
    fun `blanks are only provable while the counted chips add up`() {
        // 12 chips short, so Boris might be holding them; nothing can be inferred.
        val result = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(388)),
            Fixture.seat(2, "Boris"),
        ).reconcile()

        assertFalse(result.uncountedAreImpliedZero)
        assertEquals(needsCounts(1), result.headline())
    }

    @Test
    fun `uncounted players are reported while their stacks are still unknown`() {
        // 300 of 400 counted, so Boris could be holding the rest: nothing can be inferred.
        assertEquals(
            needsCounts(1),
            snapshot(
                Fixture.seat(1, "Anna", finalChips = Chips(300)),
                Fixture.seat(2, "Boris"),
            ).reconcile().headline(),
        )
        assertEquals(
            needsCounts(2),
            snapshot(Fixture.seat(1, "Anna"), Fixture.seat(2, "Boris")).reconcile().headline(),
        )
    }

    @Test
    fun `buy-ins that are not whole chips are called out as a settings problem`() {
        val headline = snapshot(
            Fixture.seat(1, "Anna", buyIns = listOf(Money(1_000_001)), finalChips = Chips(200)),
        ).reconcile().headline()

        assertEquals(
            UiText.of(R.string.end_headline_remainder, "0.000001"),
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
        assertEquals(
            UiText.of(R.string.error_chips_whole_number, UiText.of(R.string.label_chip_count)),
            row.error,
        )
    }

    @Test
    fun `a fractional count is refused`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onCountChange(1, "250.5")

        val row = viewModel.stateWhere { it.counts[0].error != null }.counts[0]
        assertEquals(UiText.of(R.string.error_chips_not_whole), row.error)
    }

    @Test
    fun `the game cannot be ended while a stack is still uncounted`() = runTest {
        val viewModel = viewModel()
        val state = viewModel.stateWhere()

        assertFalse(state.canFinish)
        assertEquals(needsCounts(2), state.reconciliation?.headline)

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

        assertEquals(missing(12, "0.06"), state.reconciliation?.headline)
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
        assertEquals(Chips(300), anna.chipsOut)
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
    fun `a game can end with blanks once the counted chips add up`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        // Anna scoops all 400 chips; Boris is left blank.
        viewModel.onCountChange(1, "400")

        val state = viewModel.stateWhere { it.canFinish }
        assertTrue(state.reconciliation!!.uncountedAreImpliedZero)
        assertTrue(state.reconciliation!!.addsUp)
        assertEquals(listOf(2L), state.seatsCountedAsZero)
        // The results already read the way they will once the game is finished.
        val boris = state.results.first { it.name == "Boris" }
        assertEquals(Chips.ZERO, boris.chipsOut)
        assertEquals(Money(-1_000_000), boris.net)
    }

    @Test
    fun `finishing writes the provable zeros so nobody drops out of the settlement`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onCountChange(1, "400")
        viewModel.stateWhere { it.canFinish }

        viewModel.events.test {
            viewModel.onFinish()
            assertEquals(EndGameEvent.Finished(GAME_ID), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(repository.writes.contains("setFinalChipCount(2, 0)"))
        assertEquals(
            Chips.ZERO,
            repository.game.value!!.seats.first { it.player.name == "Boris" }.finalChips,
        )
    }

    @Test
    fun `a blank stack still blocks the game while the chips do not add up`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        // 300 of 400 counted: Boris might be holding the other 100.
        viewModel.onCountChange(1, "300")

        val state = viewModel.stateWhere { it.counts[0].isCounted }
        assertFalse(state.canFinish)
        assertFalse(state.reconciliation!!.uncountedAreImpliedZero)

        viewModel.onFinish()
        assertTrue(repository.writes.none { it.startsWith("endGame") })
    }

    @Test
    fun `a deleted game reports itself as missing`() = runTest {
        repository.game.value = null
        assertTrue(viewModel().stateWhere().isMissing)
    }
}
