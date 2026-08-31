package com.zango.pokertracker.ui.players

import androidx.lifecycle.SavedStateHandle
import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.PlayerGameResult
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

private const val PLAYER_ID = 1L

/** 30 Aug 2026, 20:15 UTC. */
private const val EVENING = 1_788_120_900_000L

/**
 * One player's whole record, as the detail screen shows it.
 *
 * Fixtures use the spec's table: 0.005 a chip, so a 1.00 buy-in is 200 chips.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDetailViewModelTest {

    private val repository = FakePokerRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository.roster.value = listOf(Player(PLAYER_ID, "Anna", createdAt = 0))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun game(
        gameId: Long,
        name: String = "Game $gameId",
        startedAt: Long = EVENING,
        buyIns: Int = 1,
        paidIn: Long = 1_000_000,
        returned: Long = 0,
        finalChips: Long? = null,
        inProgress: Boolean = false,
    ) = PlayerGameResult(
        gameId = gameId,
        gameName = name,
        startedAt = startedAt,
        endedAt = if (inProgress) null else startedAt + 3_600_000,
        isInProgress = inProgress,
        chipRate = ChipRate(5_000),
        buyInCount = buyIns,
        totalBuyIn = Money(paidIn),
        returnedChips = Chips(returned),
        finalChips = finalChips?.let { Chips(it) },
    )

    private fun played(vararg games: PlayerGameResult) {
        repository.playerGames.value = mapOf(PLAYER_ID to games.toList())
    }

    private fun viewModel(playerId: Long = PLAYER_ID) = PlayerDetailViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(mapOf("playerId" to playerId)),
    )

    private suspend fun state(playerId: Long = PLAYER_ID) =
        viewModel(playerId).uiState.first { !it.isLoading }

    // -----------------------------------------------------------------------------------------
    // Headline figures
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a player who has never played says so rather than showing zeroes`() {
        runTest {
            val state = state()

            assertFalse(state.hasPlayed)
            assertNull(state.netProfit)
            assertEquals(0, state.gamesPlayed)
            assertEquals(Money.ZERO, state.totalPaidIn)
            assertEquals("Anna", state.name)
        }
    }

    @Test
    fun `lifetime figures add up every game behind them`() = runTest {
        played(
            game(1, buyIns = 2, paidIn = 2_000_000, finalChips = 600),
            game(2, finalChips = 100),
        )

        val state = state()
        assertEquals(2, state.gamesPlayed)
        assertEquals(3, state.buyInCount)
        assertEquals(Money(3_000_000), state.totalPaidIn)
        // 600 chips is 3.00 and 100 is 0.50: 3.50 out against 3.00 in.
        assertEquals(Money(3_500_000), state.cashedOut)
        assertEquals(Money(500_000), state.netProfit)
        assertEquals(1, state.gamesUp)
    }

    /**
     * Money in a game that is still running has been paid but not lost. Counting it as a loss
     * would show a figure that is simply wrong until the night is over.
     */
    @Test
    fun `an unfinished game is paid in but left out of the profit`() = runTest {
        played(
            game(1, finalChips = 400),
            game(2, inProgress = true),
        )

        val state = state()
        assertEquals(Money(2_000_000), state.totalPaidIn)
        assertEquals(Money(1_000_000), state.netProfit)
        assertEquals(1, state.openGames)
    }

    @Test
    fun `a player whose only game is unsettled has no profit to show yet`() = runTest {
        played(game(1, inProgress = true))

        val state = state()
        assertTrue(state.hasPlayed)
        assertNull(state.netProfit)
        assertEquals(1, state.openGames)
    }

    // -----------------------------------------------------------------------------------------
    // The per-game breakdown
    // -----------------------------------------------------------------------------------------

    @Test
    fun `each game reads as its own line, newest first`() = runTest {
        played(
            game(2, name = "Last Thursday", startedAt = EVENING - 7 * 86_400_000, finalChips = 100),
            game(1, name = "Thursday", startedAt = EVENING, finalChips = 400),
        )

        assertEquals(listOf("Thursday", "Last Thursday"), state().games.map { it.gameName })
    }

    @Test
    fun `a game line carries what it cost and how it ended`() = runTest {
        played(game(1, name = "Thursday", buyIns = 2, paidIn = 2_000_000, finalChips = 600))

        val row = state().games.single()
        assertEquals("Thursday", row.gameName)
        assertEquals(2, row.buyInCount)
        assertEquals(Money(2_000_000), row.totalBuyIn)
        assertEquals(Money(3_000_000), row.cashOut)
        assertEquals(Money(1_000_000), row.net)
        assertFalse(row.isInProgress)
    }

    @Test
    fun `a game still being played shows no result on its line`() = runTest {
        played(game(1, inProgress = true))

        val row = state().games.single()
        assertTrue(row.isInProgress)
        assertNull(row.cashOut)
        assertNull(row.net)
    }

    @Test
    fun `chips sold back mid-game count towards what the player took out`() = runTest {
        played(game(1, buyIns = 2, paidIn = 2_000_000, returned = 200, finalChips = 150))

        val row = state().games.single()
        // 350 chips out at 0.005 is 1.75 against 2.00 in.
        assertEquals(Money(1_750_000), row.cashOut)
        assertEquals(Money(-250_000), row.net)
    }

    @Test
    fun `each line is dated so a figure can be traced to a night`() = runTest {
        played(game(1, startedAt = EVENING))

        // Rendered in the device's zone; the format itself is asserted in GameDateFormatTest.
        assertTrue(state().games.single().dateLabel.contains("2026"))
    }

    // -----------------------------------------------------------------------------------------
    // Edges
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a player deleted while their history is open reports it rather than showing a blank`() =
        runTest {
            val state = state(playerId = 999)

            assertTrue(state.isMissing)
            assertEquals("", state.name)
        }

    @Test
    fun `a hidden player still has a readable history`() = runTest {
        repository.roster.value = listOf(Player(PLAYER_ID, "Anna", createdAt = 0, isArchived = true))
        played(game(1, finalChips = 400))

        val state = state()
        assertTrue(state.isArchived)
        assertEquals(Money(1_000_000), state.netProfit)
    }

    @Test
    fun `a rename made elsewhere reaches this screen without it asking`() = runTest {
        val viewModel = viewModel()
        assertEquals("Anna", viewModel.uiState.first { !it.isLoading }.name)

        repository.renamePlayer(PLAYER_ID, "Anni")

        assertEquals("Anni", viewModel.uiState.first { it.name == "Anni" }.name)
    }

    @Test
    fun `breaking exactly even is a settled game, not a win`() = runTest {
        played(game(1, finalChips = 200))

        val state = state()
        assertEquals(Money.ZERO, state.netProfit)
        assertEquals(0, state.gamesUp)
        assertEquals(0, state.openGames)
    }
}
