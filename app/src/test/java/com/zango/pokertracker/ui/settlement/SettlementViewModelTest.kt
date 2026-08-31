package com.zango.pokertracker.ui.settlement

import androidx.lifecycle.SavedStateHandle
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.GameStatus
import com.zango.pokertracker.domain.model.Seat
import com.zango.pokertracker.domain.model.SettledPayment
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
        assertTrue(state.notes.isEmpty())
        assertTrue(state.isFinished)
    }

    @Test
    fun `the night is summed up above the payments`() = runTest {
        // Four buy-ins of 1.00 between three players, and the game ran from 1s to 9s.
        seat(
            Fixture.seat(1, "Anna", buyIns = listOf(Fixture.buyIn, Fixture.buyIn), finalChips = Chips(100)),
            Fixture.seat(2, "Boris", finalChips = Chips(440)),
            Fixture.seat(3, "Chris", finalChips = Chips(260)),
        )

        val state = state()
        assertEquals(4, state.buyInCount)
        assertEquals(Money(4_000_000), state.totalOnTable)
        assertEquals("0m", state.durationLabel)
    }

    /**
     * Anna owes Boris 1.20 and Chris 0.30. Ticking both is what makes the game square, and only
     * the second tick may report it, because the hub colours a row on that conclusion alone.
     */
    @Test
    fun `a game is only square once the last payment is ticked off`() = runTest {
        seat(
            Fixture.seat(1, "Anna", buyIns = listOf(Fixture.buyIn, Fixture.buyIn), finalChips = Chips(100)),
            Fixture.seat(2, "Boris", finalChips = Chips(440)),
            Fixture.seat(3, "Chris", finalChips = Chips(260)),
        )
        val viewModel = viewModel()
        val payments = viewModel.uiState.first { !it.isLoading }.payments

        viewModel.onPaymentToggled(payments[0])
        assertEquals(listOf("setPaymentSettled(1->2, true, all=false)"), repository.writes)

        val afterFirst = viewModel.uiState.first { it.paidCount == 1 }
        assertFalse(afterFirst.isFullyPaid)
        assertTrue(afterFirst.payments[0].isPaid)
        assertFalse(afterFirst.payments[1].isPaid)

        viewModel.onPaymentToggled(afterFirst.payments[1])
        assertEquals("setPaymentSettled(1->3, true, all=true)", repository.writes.last())
        assertTrue(viewModel.uiState.first { it.paidCount == 2 }.isFullyPaid)
    }

    @Test
    fun `unticking a payment says the game is no longer square`() = runTest {
        seat(
            Fixture.seat(1, "Anna", finalChips = Chips(100)),
            Fixture.seat(2, "Boris", finalChips = Chips(300)),
        )
        val viewModel = viewModel()
        val payment = viewModel.uiState.first { !it.isLoading }.payments.single()

        viewModel.onPaymentToggled(payment)
        val paid = viewModel.uiState.first { it.isFullyPaid }
        viewModel.onPaymentToggled(paid.payments.single())

        assertEquals("setPaymentSettled(1->2, false, all=false)", repository.writes.last())
        assertFalse(viewModel.uiState.first { it.paidCount == 0 }.isFullyPaid)
    }

    /** A mark is against a figure, not just a pair, so it cannot survive a different amount. */
    @Test
    fun `a mark for a different amount does not tick the payment off`() = runTest {
        seat(
            Fixture.seat(1, "Anna", finalChips = Chips(100)),
            Fixture.seat(2, "Boris", finalChips = Chips(300)),
        )
        repository.settledPayments.value = listOf(
            SettledPayment(GAME_ID, fromPlayerId = 1, toPlayerId = 2, amount = Money(999_000)),
        )

        assertFalse(state().payments.single().isPaid)
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
        assertEquals(Chips(500), anna.chipsOut)
        assertEquals(Money(2_500_000), anna.cashOut)
        assertEquals(Money(500_000), anna.net)
    }

    @Test
    fun `a deleted game reports itself as missing`() = runTest {
        repository.game.value = null
        assertTrue(state().isMissing)
    }
}
