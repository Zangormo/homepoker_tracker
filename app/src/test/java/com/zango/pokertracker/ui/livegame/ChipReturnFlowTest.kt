package com.zango.pokertracker.ui.livegame

import androidx.lifecycle.SavedStateHandle
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.Seat
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

/**
 * Selling chips back to the bank part-way through the night.
 *
 * This happens when the physical chips run out: someone with a big stack sells chips back so the
 * next buy-in can be paid out in them. They keep their seat, they are paid the cash immediately,
 * and the chips leave the table — which is the part that has to be got right, because everything
 * the end-game screen reconciles depends on knowing how many chips are still out there.
 *
 * Fixtures use the spec's table: 0.005 a chip, so a 1.00 buy-in is 200 chips.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChipReturnFlowTest {

    private val clock = TestClock(1_000)
    private val repository = FakePokerRepository(clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        seat(Fixture.seat(1, "Anna"), Fixture.seat(2, "Boris"))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun seat(vararg seats: Seat) {
        repository.game.value = GameSnapshot(
            game = Fixture.game().copy(id = GAME_ID),
            seats = seats.toList(),
        )
    }

    private fun viewModel() = LiveGameViewModel(
        repository = repository,
        clock = clock,
        savedStateHandle = SavedStateHandle(mapOf("gameId" to GAME_ID)),
    )

    private suspend fun LiveGameViewModel.stateWhere(
        predicate: (LiveGameUiState) -> Boolean = { true },
    ) = uiState.first { !it.isLoading && predicate(it) }

    private suspend fun LiveGameViewModel.returnDialog() =
        stateWhere { it.dialog is LiveGameDialog.ReturnChips }.dialog as LiveGameDialog.ReturnChips

    // -----------------------------------------------------------------------------------------
    // The dialog
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the dialog opens empty, naming the player and what is on the table`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()

        viewModel.onReturnChips(1)

        val dialog = viewModel.returnDialog()
        assertEquals("Anna", dialog.playerName)
        assertEquals("", dialog.chips)
        // Two players in for 1.00 each is 400 chips out there.
        assertEquals(Chips(400), dialog.chipsOnTable)
        assertFalse(dialog.canConfirm)
    }

    @Test
    fun `a count shows the cash the player is about to be handed`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)

        viewModel.onReturnChipsChange("200")

        val dialog = viewModel.returnDialog()
        assertEquals(Chips(200), dialog.chipCount)
        assertEquals(Money(1_000_000), dialog.cashValue)
        assertTrue(dialog.canConfirm)
        assertNull(dialog.error)
    }

    /**
     * A player cannot hand back chips the table does not hold. Their own stack is unknown —
     * winnings are never recorded — so the table total is the only bound that can honestly be
     * enforced.
     */
    @Test
    fun `more chips than the table holds is refused, with the real figure named`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)

        viewModel.onReturnChipsChange("401")

        val dialog = viewModel.returnDialog()
        assertEquals(
            UiText.plural(R.plurals.error_more_than_on_table, 400, 400L),
            dialog.error,
        )
        assertNull(dialog.chipCount)
        assertFalse(dialog.canConfirm)
    }

    @Test
    fun `exactly the whole table is allowed, however unlikely`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)

        viewModel.onReturnChipsChange("400")

        assertTrue(viewModel.returnDialog().canConfirm)
    }

    @Test
    fun `half a chip is refused`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)

        viewModel.onReturnChipsChange("1.5")

        assertEquals(UiText.of(R.string.error_chips_not_whole), viewModel.returnDialog().error)
    }

    @Test
    fun `handing nothing back is not a transaction`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)

        viewModel.onReturnChipsChange("0")

        assertEquals(
            UiText.of(R.string.error_amount_positive, UiText.of(R.string.live_return_field)),
            viewModel.returnDialog().error,
        )
    }

    @Test
    fun `confirming with an unusable figure does nothing at all`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("401")

        viewModel.onConfirmReturnChips()

        assertTrue(repository.writes.isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // Recording it
    // -----------------------------------------------------------------------------------------

    @Test
    fun `confirming records the return and closes the dialog`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("200")

        viewModel.onConfirmReturnChips()

        assertEquals(listOf("returnChips(1, 200)"), repository.writes)
        assertNull(viewModel.stateWhere { it.dialog == null }.dialog)
    }

    @Test
    fun `the player keeps their seat and their buy-in stands`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("200")
        viewModel.onConfirmReturnChips()

        val row = viewModel.stateWhere { it.activeSeats.first().hasReturns }.activeSeats.first()
        assertEquals("Anna", row.name)
        assertFalse(row.isCashedOut)
        assertEquals(Money(1_000_000), row.totalBuyIn)
        assertEquals(Chips(200), row.returnedChips)
        assertEquals(Money(1_000_000), row.returnedCash)
    }

    /**
     * The chips are gone from the table, and so is the cash they were worth. What the bank is
     * holding drops by exactly what was paid out.
     */
    @Test
    fun `the table total drops by the cash that was handed over`() = runTest {
        val viewModel = viewModel()
        val before = viewModel.stateWhere()
        assertEquals(Money(2_000_000), before.totalOnTable.cash)

        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("200")
        viewModel.onConfirmReturnChips()

        val after = viewModel.stateWhere { it.totalOnTable.cash == Money(1_000_000) }
        assertEquals(Chips(200), after.totalOnTable.chips)
    }

    @Test
    fun `a second return adds to the first rather than replacing it`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()

        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("100")
        viewModel.onConfirmReturnChips()
        viewModel.stateWhere { it.activeSeats.first().returnedChips == Chips(100) }

        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("50")
        viewModel.onConfirmReturnChips()

        val row = viewModel.stateWhere { it.activeSeats.first().returnedChips == Chips(150) }
            .activeSeats.first()
        assertEquals(Chips(150), row.returnedChips)
        assertEquals(Money(750_000), row.returnedCash)
    }

    @Test
    fun `the bound follows the table down as chips come off it`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("300")
        viewModel.onConfirmReturnChips()
        viewModel.stateWhere { it.totalOnTable.chips == Chips(100) }

        viewModel.onReturnChips(2)

        assertEquals(Chips(100), viewModel.returnDialog().chipsOnTable)
    }

    // -----------------------------------------------------------------------------------------
    // Taking one back
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a return recorded by mistake can be taken back`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("200")
        viewModel.onConfirmReturnChips()
        val recorded = viewModel.stateWhere { it.activeSeats.first().hasReturns }
        val returnId = recorded.activeSeats.first().lastReturnId!!

        viewModel.onUndoLastReturn(1)

        assertEquals("undoChipReturn($returnId)", repository.writes.last())
        val row = viewModel.stateWhere { !it.activeSeats.first().hasReturns }.activeSeats.first()
        assertEquals(Chips.ZERO, row.returnedChips)
        assertNull(row.lastReturnId)
    }

    @Test
    fun `undoing puts the chips and the cash back on the table`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("200")
        viewModel.onConfirmReturnChips()
        viewModel.stateWhere { it.totalOnTable.cash == Money(1_000_000) }

        viewModel.onUndoLastReturn(1)

        val state = viewModel.stateWhere { it.totalOnTable.cash == Money(2_000_000) }
        assertEquals(Chips(400), state.totalOnTable.chips)
    }

    @Test
    fun `there is nothing to undo for a player who never sold chips back`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()

        viewModel.onUndoLastReturn(2)

        assertTrue(repository.writes.isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // What it means at the end
    // -----------------------------------------------------------------------------------------

    /**
     * The chips a player sold back are money they took off the table, exactly like the stack in
     * front of them at the end. Their result has to include both, or the night will not balance.
     */
    @Test
    fun `a player is credited for what they sold back as well as what they end with`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere()
        viewModel.onReturnChips(1)
        viewModel.onReturnChipsChange("200")
        viewModel.onConfirmReturnChips()
        viewModel.stateWhere { it.activeSeats.first().hasReturns }

        viewModel.onCashOut(1)
        viewModel.onChipCountChange("150")
        viewModel.onConfirmCashOut()

        val row = viewModel.stateWhere { it.cashedOutSeats.isNotEmpty() }.cashedOutSeats.first()
        // 200 sold back plus 150 in hand is 350 chips, or 1.75, against 1.00 in.
        assertEquals(Money(1_750_000), row.cashOutValue)
        assertEquals(Money(750_000), row.net)
    }
}
