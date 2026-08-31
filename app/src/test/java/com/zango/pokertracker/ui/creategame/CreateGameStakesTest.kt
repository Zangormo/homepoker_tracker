package com.zango.pokertracker.ui.creategame

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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The stake picker under the blind fields, and the stakes a new game opens on.
 *
 * The list is the host's own: seeded with the standard ladder, grown by playing a game on new
 * blinds, and pruned in settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateGameStakesTest {

    private val repository = FakePokerRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun state() =
        CreateGameViewModel(repository).uiState.first { it.stakeOptions.isNotEmpty() }

    @Test
    fun `the standard ladder is offered before the host changes anything`() = runTest {
        assertEquals(
            listOf("0.01 / 0.02", "0.05 / 0.10", "0.10 / 0.20", "0.50 / 1.00", "1.00 / 2.00"),
            state().stakeOptions.map { it.label },
        )
    }

    @Test
    fun `a level on the host's own list is offered, in size order`() = runTest {
        repository.stakePresets.value += Stakes(Money(2_000), Money(5_000))

        assertEquals(
            listOf(
                "0.002 / 0.005",
                "0.01 / 0.02",
                "0.05 / 0.10",
                "0.10 / 0.20",
                "0.50 / 1.00",
                "1.00 / 2.00",
            ),
            state().stakeOptions.map { it.label },
        )
    }

    @Test
    fun `picking a stake fills both blind fields`() = runTest {
        val viewModel = CreateGameViewModel(repository)
        val option = viewModel.uiState.first { it.stakeOptions.isNotEmpty() }
            .stakeOptions.first { it.label == "0.10 / 0.20" }

        viewModel.onStakesSelected(option)

        val form = viewModel.uiState.first { it.form.bigBlind.isNotBlank() }.form
        assertEquals("0.10", form.smallBlind)
        assertEquals("0.20", form.bigBlind)
    }

    @Test
    fun `the picker names the level the typed blinds are on`() = runTest {
        val viewModel = CreateGameViewModel(repository)
        viewModel.onSmallBlindChange("0.05")
        viewModel.onBigBlindChange("0.10")

        assertEquals(
            "0.05 / 0.10",
            viewModel.uiState.first { it.selectedStake != null }.selectedStake?.label,
        )
    }

    @Test
    fun `blinds that match no level are simply the host's own`() = runTest {
        val viewModel = CreateGameViewModel(repository)
        viewModel.onSmallBlindChange("0.03")
        viewModel.onBigBlindChange("0.07")

        assertNull(viewModel.uiState.first { it.form.bigBlind == "0.07" }.selectedStake)
    }

    @Test
    fun `a new game opens on the stakes last played`() = runTest {
        repository.lastPlayedStakes = Stakes(Money(250_000), Money(500_000))

        val form = CreateGameViewModel(repository).uiState.first { it.form.bigBlind.isNotBlank() }.form
        assertEquals("0.25", form.smallBlind)
        assertEquals("0.50", form.bigBlind)
    }

    @Test
    fun `the very first game opens with the blinds empty`() = runTest {
        val form = state().form
        assertEquals("", form.smallBlind)
        assertEquals("", form.bigBlind)
    }
}
