package com.zango.pokertracker.ui.settlement

import androidx.lifecycle.SavedStateHandle
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.GameStatus
import com.zango.pokertracker.domain.model.Seat
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val GAME_ID = 42L

@OptIn(ExperimentalCoroutinesApi::class)
class SettlementViewModelTest {

    private val repository = FakePokerRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun seat(vararg seats: Seat) {
        repository.game.value = GameSnapshot(
            game = Fixture.game().copy(
                id = GAME_ID,
                status = GameStatus.FINISHED,
                endedAt = 9_000L,
            ),
            seats = seats.toList(),
        )
    }

    private fun viewModel() = SettlementViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(mapOf("gameId" to GAME_ID)),
    )

    private suspend fun state() = viewModel().uiState.first { !it.isLoading }

    /** Renders the structured payments back into sentences, so assertions stay readable. */
    private fun SettlementUiState.sentences(): List<String> =
        payments.map { "${it.from} pays ${it.to} ${it.amount.format()}" }

    @Test
    fun `payments read as plain instructions`() = runTest {
        // Anna is down 1.50, Boris up 1.20, Chris up 0.30.
        seat(
            Fixture.seat(1, "Anna", buyIns = listOf(Fixture.buyIn, Fixture.buyIn), finalChips = Chips(100)),
            Fixture.seat(2, "Boris", finalChips = Chips(440)),
            Fixture.seat(3, "Chris", finalChips = Chips(260)),
        )

        val state = state()

        assertEquals(
            listOf("Anna pays Boris 1.20", "Anna pays Chris 0.30"),
            state.sentences(),
        )
        assertEquals(Money(1_500_000), state.totalMoved)
        assertTrue(state.notes.isEmpty())
        assertTrue(state.isFinished)
    }

    @Test
    fun `the share text is what the payments say, ready to paste into a chat`() = runTest {
        seat(
            Fixture.seat(1, "Anna", finalChips = Chips(300)),
            Fixture.seat(2, "Boris", finalChips = Chips(100)),
        )

        assertEquals(
            """
            Thursday — settlement

            Boris pays Anna 0.50
            """.trimIndent(),
            state().shareText,
        )
    }

    @Test
    fun `a table where nobody won or lost needs no payments`() = runTest {
        seat(
            Fixture.seat(1, "Anna", finalChips = Chips(200)),
            Fixture.seat(2, "Boris", finalChips = Chips(200)),
        )

        val state = state()
        assertFalse(state.hasPayments)
        assertEquals(Money.ZERO, state.totalMoved)
        assertTrue(state.shareText.contains("Everyone broke even"))
    }

    @Test
    fun `a rounding adjustment is surfaced as a note`() = runTest {
        // Two players up 0.005 and one down 0.01: rounding to the cent cannot leave all three exact.
        seat(
            Fixture.seat(1, "Anna", buyIns = listOf(Money(995_000)), finalChips = Chips(200)),
            Fixture.seat(2, "Boris", buyIns = listOf(Money(995_000)), finalChips = Chips(200)),
            Fixture.seat(3, "Chris", buyIns = listOf(Money(1_010_000)), finalChips = Chips(200)),
        )

        val state = state()
        assertEquals(
            listOf("Rounded to the nearest 0.01. Chris absorbed 0.01."),
            state.notes,
        )
        assertEquals(listOf("Chris pays Anna 0.01", "Chris pays Boris 0.01"), state.sentences())
    }

    @Test
    fun `a game finished with chips missing says so and names who is left short`() = runTest {
        seat(
            Fixture.seat(1, "Anna", finalChips = Chips(250)),
            Fixture.seat(2, "Boris", finalChips = Chips(138)),
        )

        val state = state()
        assertEquals(listOf("Boris pays Anna 0.25"), state.sentences())
        assertTrue(state.hasProblem)
        assertEquals(
            listOf(
                "Chip counts came out 0.06 short of the buy-ins, " +
                    "so these payments do not fully square everyone up.",
                "Boris still owes 0.06.",
            ),
            state.notes,
        )
    }

    @Test
    fun `the results table carries the full record, not just the payments`() = runTest {
        seat(
            Fixture.seat(1, "Anna", buyIns = listOf(Fixture.buyIn, Fixture.buyIn), finalChips = Chips(500)),
            Fixture.seat(2, "Boris", finalChips = Chips(100)),
        )

        val anna = state().results.first { it.name == "Anna" }
        assertEquals(Money(2_000_000), anna.totalBuyIn)
        assertEquals(Chips(500), anna.finalChips)
        assertEquals(Money(2_500_000), anna.cashOut)
        assertEquals(Money(500_000), anna.net)
    }

    @Test
    fun `a deleted game reports itself as missing`() = runTest {
        repository.game.value = null
        assertTrue(state().isMissing)
    }
}
