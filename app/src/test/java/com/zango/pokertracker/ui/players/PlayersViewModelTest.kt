package com.zango.pokertracker.ui.players

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Fixture
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

@OptIn(ExperimentalCoroutinesApi::class)
class PlayersViewModelTest {

    private val repository = FakePokerRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun player(id: Long, name: String, archived: Boolean = false) =
        Player(id = id, name = name, createdAt = 0L, isArchived = archived)

    private fun game(gameId: Long, paidIn: Long = 1_000_000, finalChips: Long? = null) =
        PlayerGameResult(
            gameId = gameId,
            gameName = "Game $gameId",
            startedAt = 1_000L * gameId,
            endedAt = 2_000L * gameId,
            isInProgress = finalChips == null,
            chipRate = Fixture.chipRate,
            buyInCount = 1,
            totalBuyIn = Money(paidIn),
            returnedChips = Chips.ZERO,
            finalChips = finalChips?.let { Chips(it) },
        )

    private fun viewModel() = PlayersViewModel(repository)

    private suspend fun PlayersViewModel.stateWhere(predicate: (PlayersUiState) -> Boolean) =
        uiState.first { !it.isLoading && predicate(it) }

    private suspend fun state() = viewModel().uiState.first { !it.isLoading }

    @Test
    fun `an empty roster says so rather than showing a blank list`() = runTest {
        assertTrue(state().isEmpty)
    }

    @Test
    fun `each row carries what the player has cost and returned`() = runTest {
        repository.roster.value = listOf(player(1, "Anna"))
        repository.playerGames.value = mapOf(
            1L to listOf(game(1, finalChips = 400), game(2, finalChips = 100)),
        )

        val row = state().active.single()
        assertEquals("Anna", row.name)
        assertEquals(2, row.gamesPlayed)
        assertEquals(2, row.buyInCount)
        assertEquals(Money(2_000_000), row.totalPaidIn)
        // 400 chips is 2.00 and 100 chips is 0.50, against 2.00 in: half a unit up.
        assertEquals(Money(500_000), row.netProfit)
        assertEquals(0, row.openGames)
    }

    @Test
    fun `a player with no settled game yet shows no profit rather than a zero`() = runTest {
        repository.roster.value = listOf(player(1, "Anna"))
        repository.playerGames.value = mapOf(1L to listOf(game(1)))

        val row = state().active.single()
        assertNull(row.netProfit)
        assertEquals(1, row.openGames)
        assertTrue(row.hasPlayed)
    }

    @Test
    fun `hidden players are listed apart from the ones games are dealt to`() = runTest {
        repository.roster.value = listOf(player(1, "Anna"), player(2, "Boris", archived = true))

        val state = state()
        assertEquals(listOf("Anna"), state.active.map { it.name })
        assertEquals(listOf("Boris"), state.archived.map { it.name })
    }

    @Test
    fun `renaming carries through to the roster`() = runTest {
        repository.roster.value = listOf(player(1, "Anna"))
        val viewModel = viewModel()
        viewModel.stateWhere { it.active.isNotEmpty() }

        viewModel.onRenameRequested(1)
        viewModel.onRenameTextChange("Anni")
        viewModel.onConfirmRename()

        assertEquals(listOf("renamePlayer(1, Anni)"), repository.writes)
        val state = viewModel.stateWhere { it.renaming == null }
        assertEquals(listOf("Anni"), state.active.map { it.name })
    }

    @Test
    fun `a name someone else already answers to is refused and the dialog stays open`() = runTest {
        repository.roster.value = listOf(player(1, "Anna"), player(2, "Boris"))
        val viewModel = viewModel()
        viewModel.stateWhere { it.active.size == 2 }

        viewModel.onRenameRequested(1)
        viewModel.onRenameTextChange("boris")
        viewModel.onConfirmRename()

        val editor = viewModel.stateWhere { it.renaming?.error != null }.renaming!!
        assertEquals("Boris is already on the roster", editor.error)
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `correcting a player's own spelling is not a clash with themselves`() = runTest {
        repository.roster.value = listOf(player(1, "anna"))
        val viewModel = viewModel()
        viewModel.stateWhere { it.active.isNotEmpty() }

        viewModel.onRenameRequested(1)
        viewModel.onRenameTextChange("Anna")
        viewModel.onConfirmRename()

        assertEquals(listOf("renamePlayer(1, Anna)"), repository.writes)
        assertEquals(listOf("Anna"), viewModel.stateWhere { it.renaming == null }.active.map { it.name })
    }

    @Test
    fun `asking to remove a player does not remove anything on its own`() = runTest {
        repository.roster.value = listOf(player(1, "Anna"))
        val viewModel = viewModel()
        viewModel.stateWhere { it.active.isNotEmpty() }

        viewModel.onDeleteRequested(1)

        val editor = viewModel.stateWhere { it.deleting != null }.deleting!!
        assertEquals("Anna", editor.name)
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `a player who has never played is deleted outright`() = runTest {
        repository.roster.value = listOf(player(1, "Anna"))
        val viewModel = viewModel()
        viewModel.stateWhere { it.active.isNotEmpty() }

        viewModel.onDeleteRequested(1)
        viewModel.onConfirmDelete()

        assertEquals(listOf("deletePlayer(1)"), repository.writes)
        assertTrue(viewModel.stateWhere { it.isEmpty }.isEmpty)
    }

    /** The whole point of the archive: past settlements were worked out from these seats. */
    @Test
    fun `a player with games behind them is hidden instead of deleted`() = runTest {
        repository.roster.value = listOf(player(1, "Anna"))
        repository.playerGames.value = mapOf(1L to listOf(game(1, finalChips = 400)))
        val viewModel = viewModel()
        viewModel.stateWhere { it.active.isNotEmpty() }

        viewModel.onDeleteRequested(1)
        assertTrue(viewModel.stateWhere { it.deleting != null }.deleting!!.hasHistory)
        viewModel.onConfirmDelete()

        assertEquals(listOf("setPlayerArchived(1, true)"), repository.writes)
        val state = viewModel.stateWhere { it.archived.isNotEmpty() }
        assertTrue(state.active.isEmpty())
        assertEquals(1, state.archived.single().gamesPlayed)
    }

    @Test
    fun `backing out of the confirmation keeps the player`() = runTest {
        repository.roster.value = listOf(player(1, "Anna"))
        val viewModel = viewModel()
        viewModel.stateWhere { it.active.isNotEmpty() }

        viewModel.onDeleteRequested(1)
        viewModel.stateWhere { it.deleting != null }
        viewModel.onDismissDelete()

        assertTrue(repository.writes.isEmpty())
        assertEquals(1, viewModel.stateWhere { it.deleting == null }.active.size)
    }

    @Test
    fun `a hidden player can be brought back for the next game`() = runTest {
        repository.roster.value = listOf(player(1, "Anna", archived = true))
        val viewModel = viewModel()
        viewModel.stateWhere { it.archived.isNotEmpty() }

        viewModel.onRestore(1)

        assertEquals(listOf("setPlayerArchived(1, false)"), repository.writes)
        val state = viewModel.stateWhere { it.active.isNotEmpty() }
        assertTrue(state.archived.isEmpty())
        assertFalse(state.active.single().isArchived)
    }

    @Test
    fun `adding someone already on the roster reports it instead of making a second row`() =
        runTest {
            repository.roster.value = listOf(player(1, "Anna"))
            val viewModel = viewModel()
            viewModel.stateWhere { it.active.isNotEmpty() }

            viewModel.onAddRequested()
            viewModel.onNewPlayerNameChange("anna")
            viewModel.onAddPlayer()

            val state = viewModel.stateWhere { it.newPlayerError != null }
            assertEquals("Anna is already on the roster", state.newPlayerError)
            assertEquals(1, state.active.size)
        }
}
