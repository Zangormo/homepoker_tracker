package com.zango.pokertracker.ui.settings

import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Stakes
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
 * The blind-sizes editor: the host's list of stake levels, which they prune and extend without
 * having to host a game to do it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val repository = FakePokerRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun stakes(small: Long, big: Long) = Stakes(Money(small), Money(big))

    private fun viewModel() = SettingsViewModel(repository)

    private suspend fun SettingsViewModel.stateWhere(predicate: (SettingsUiState) -> Boolean) =
        uiState.first { !it.isLoading && predicate(it) }

    private suspend fun state() = viewModel().uiState.first { !it.isLoading }

    @Test
    fun `the editor opens on the standard ladder`() = runTest {
        assertEquals(
            listOf("0.01 / 0.02", "0.05 / 0.10", "0.10 / 0.20", "0.50 / 1.00", "1.00 / 2.00"),
            state().stakes.map { it.label },
        )
    }

    /** The case this screen exists for: one odd night, then never again. */
    @Test
    fun `a level played once can be taken off the list`() = runTest {
        val oddNight = stakes(20_000, 40_000)
        repository.stakePresets.value += oddNight
        val viewModel = viewModel()
        viewModel.stateWhere { it.stakes.any { row -> row.label == "0.02 / 0.04" } }

        viewModel.onRemove(oddNight)

        assertEquals(listOf("removeStakes(0.02 / 0.04)"), repository.writes)
        val state = viewModel.stateWhere { it.stakes.none { row -> row.label == "0.02 / 0.04" } }
        assertEquals(5, state.count)
    }

    @Test
    fun `a level put back from the snackbar returns to the list`() = runTest {
        val viewModel = viewModel()
        val standard = stakes(10_000, 20_000)
        viewModel.stateWhere { it.count == 5 }

        viewModel.onRemove(standard)
        viewModel.stateWhere { it.count == 4 }
        viewModel.onUndoRemove(standard)

        assertTrue(viewModel.stateWhere { it.count == 5 }.stakes.any { it.label == "0.01 / 0.02" })
    }

    @Test
    fun `a level can be added without hosting a game on it`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere { it.count == 5 }

        viewModel.onAddRequested()
        viewModel.onSmallBlindChange("0.25")
        viewModel.onBigBlindChange("0.50")
        viewModel.onConfirmAdd()

        assertEquals(listOf("addStakes(0.25 / 0.50)"), repository.writes)
        val state = viewModel.stateWhere { it.count == 6 }
        assertNull(state.editor)
        // Sorted by size, so it lands between 0.10/0.20 and 0.50/1.00.
        assertEquals(
            listOf("0.01 / 0.02", "0.05 / 0.10", "0.10 / 0.20", "0.25 / 0.50", "0.50 / 1.00", "1.00 / 2.00"),
            state.stakes.map { it.label },
        )
    }

    @Test
    fun `a big blind below the small one is refused where it is typed`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere { it.count == 5 }

        viewModel.onAddRequested()
        viewModel.onSmallBlindChange("0.10")
        viewModel.onBigBlindChange("0.05")
        viewModel.onConfirmAdd()

        assertEquals(
            "Big blind must be larger than the small blind",
            viewModel.stateWhere { it.editor?.error != null }.editor?.error,
        )
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `a level already on the list is not added twice`() = runTest {
        val viewModel = viewModel()
        viewModel.stateWhere { it.count == 5 }

        viewModel.onAddRequested()
        viewModel.onSmallBlindChange("0.05")
        viewModel.onBigBlindChange("0.10")
        viewModel.onConfirmAdd()

        assertEquals(
            "0.05 / 0.10 is already on the list",
            viewModel.stateWhere { it.editor?.error != null }.editor?.error,
        )
        assertEquals(5, viewModel.stateWhere { true }.count)
    }

    @Test
    fun `the list stops at ten levels`() = runTest {
        repository.stakePresets.value = (1L..Stakes.MAX_PRESETS)
            .map { stakes(it * 1_000, it * 2_000) }
        val viewModel = viewModel()
        val full = viewModel.stateWhere { it.count == Stakes.MAX_PRESETS }
        assertTrue(full.isFull)

        viewModel.onAddRequested()

        // The dialog never opens: there is nowhere to put another one.
        assertNull(viewModel.stateWhere { true }.editor)
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `room made on a full list can be used again`() = runTest {
        repository.stakePresets.value = (1L..Stakes.MAX_PRESETS)
            .map { stakes(it * 1_000, it * 2_000) }
        val viewModel = viewModel()
        viewModel.stateWhere { it.isFull }

        viewModel.onRemove(stakes(1_000, 2_000))
        assertFalse(viewModel.stateWhere { it.count == Stakes.MAX_PRESETS - 1 }.isFull)

        viewModel.onAddRequested()
        viewModel.onSmallBlindChange("2")
        viewModel.onBigBlindChange("4")
        viewModel.onConfirmAdd()

        assertTrue(viewModel.stateWhere { it.count == Stakes.MAX_PRESETS }.stakes.any {
            it.label == "2.00 / 4.00"
        })
    }
}
