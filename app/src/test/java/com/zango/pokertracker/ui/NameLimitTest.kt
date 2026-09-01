package com.zango.pokertracker.ui

import androidx.lifecycle.SavedStateHandle
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.NameRules
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.testing.FakePokerRepository
import com.zango.pokertracker.ui.creategame.CreateGameForm
import com.zango.pokertracker.ui.creategame.CreateGameViewModel
import com.zango.pokertracker.ui.creategame.validate
import com.zango.pokertracker.ui.livegame.LiveGameDialog
import com.zango.pokertracker.ui.livegame.LiveGameViewModel
import com.zango.pokertracker.ui.players.PlayersViewModel
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

/** Thirteen characters: one past the limit on a player, wherever it is typed. */
private const val TOO_LONG = "Bartholomewww"

/** Exactly the player limit, which must still be accepted everywhere. */
private const val AT_LIMIT = "Bartholomeww"

/** Twenty-six characters: one past the longer limit a game name gets. */
private const val GAME_TOO_LONG = "Thursday night home games!"

/** Exactly the game limit. */
private const val GAME_AT_LIMIT = "Thursday night home game!"

/**
 * The name limit, checked at every door into the roster and at the one into a game.
 *
 * A name can be entered while setting a game up, from the players tab, and mid-game when seating
 * someone who was not there at the start; it can also be changed later by renaming. Each of those
 * is a separate screen with its own field, so each one is exercised here rather than trusting
 * that they all happen to call the same code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NameLimitTest {

    private val repository = FakePokerRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val expectedMessage = NameRules.playerNameTooLongMessage()

    // -----------------------------------------------------------------------------------------
    // The game's own name
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a game name over the limit is refused on the setup form`() {
        val validation = CreateGameForm(name = GAME_TOO_LONG).validate()
        assertEquals(NameRules.gameNameTooLongMessage(), validation.nameError)
    }

    @Test
    fun `a game name exactly at the limit is fine`() {
        assertNull(CreateGameForm(name = GAME_AT_LIMIT).validate().nameError)
    }

    // -----------------------------------------------------------------------------------------
    // Adding a player while setting a game up
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the setup screen refuses a long name instead of adding it`() = runTest {
        val viewModel = CreateGameViewModel(repository)
        viewModel.onNewPlayerNameChange(TOO_LONG)
        viewModel.onAddNewPlayer()

        assertEquals(
            expectedMessage,
            viewModel.uiState.first { it.newPlayerError != null }.newPlayerError,
        )
        assertTrue(repository.roster.value.isEmpty())
    }

    @Test
    fun `the setup screen takes a name at the limit`() = runTest {
        val viewModel = CreateGameViewModel(repository)
        viewModel.onNewPlayerNameChange(AT_LIMIT)
        viewModel.onAddNewPlayer()

        assertEquals(listOf(AT_LIMIT), repository.roster.value.map { it.name })
    }

    // -----------------------------------------------------------------------------------------
    // The players tab
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the players tab refuses a long name instead of adding it`() = runTest {
        val viewModel = PlayersViewModel(repository)
        viewModel.onAddRequested()
        viewModel.onNewPlayerNameChange(TOO_LONG)
        viewModel.onAddPlayer()

        assertEquals(
            expectedMessage,
            viewModel.uiState.first { it.newPlayerError != null }.newPlayerError,
        )
        assertTrue(repository.roster.value.isEmpty())
    }

    @Test
    fun `renaming a player cannot get around the limit either`() = runTest {
        repository.roster.value = listOf(Player(1, "Anna", 0))
        val viewModel = PlayersViewModel(repository)
        viewModel.uiState.first { it.active.isNotEmpty() }

        viewModel.onRenameRequested(1)
        viewModel.onRenameTextChange(TOO_LONG)
        viewModel.onConfirmRename()

        assertEquals(
            expectedMessage,
            viewModel.uiState.first { it.renaming?.error != null }.renaming?.error,
        )
        assertEquals(listOf("Anna"), repository.roster.value.map { it.name })
    }

    // -----------------------------------------------------------------------------------------
    // Seating someone mid-game who was not on the roster
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a long name cannot be seated part-way through a game`() = runTest {
        repository.game.value = GameSnapshot(
            game = Fixture.game().copy(id = GAME_ID),
            seats = listOf(Fixture.seat(1, "Anna")),
        )
        val viewModel = LiveGameViewModel(
            repository = repository,
            clock = com.zango.pokertracker.testing.TestClock(),
            savedStateHandle = SavedStateHandle(mapOf("gameId" to GAME_ID)),
        )
        viewModel.uiState.first { !it.isLoading }

        viewModel.onAddPlayer()
        viewModel.onNewPlayerNameChange(TOO_LONG)
        viewModel.onAddPlayerBuyInChange("1.00")

        val dialog = viewModel.uiState
            .first { (it.dialog as? LiveGameDialog.AddPlayer)?.nameError != null }
            .dialog as LiveGameDialog.AddPlayer
        assertEquals(expectedMessage, dialog.nameError)
        // "Seat them" stays disabled, so the name never reaches the roster.
        assertFalse(dialog.canConfirm)
    }

    @Test
    fun `a name at the limit can be seated part-way through`() = runTest {
        repository.game.value = GameSnapshot(
            game = Fixture.game().copy(id = GAME_ID),
            seats = listOf(Fixture.seat(1, "Anna")),
        )
        val viewModel = LiveGameViewModel(
            repository = repository,
            clock = com.zango.pokertracker.testing.TestClock(),
            savedStateHandle = SavedStateHandle(mapOf("gameId" to GAME_ID)),
        )
        viewModel.uiState.first { !it.isLoading }

        viewModel.onAddPlayer()
        viewModel.onNewPlayerNameChange(AT_LIMIT)
        viewModel.onAddPlayerBuyInChange("1.00")

        val dialog = viewModel.uiState
            .first { it.dialog is LiveGameDialog.AddPlayer }
            .dialog as LiveGameDialog.AddPlayer
        assertNull(dialog.nameError)
        assertTrue(dialog.canConfirm)
    }

    // -----------------------------------------------------------------------------------------
    // The door itself
    // -----------------------------------------------------------------------------------------

    /**
     * Every field above can be bypassed by a code path that has not been written yet, so the
     * repository refuses a long name on its own account rather than trusting its callers.
     */
    @Test
    fun `the repository refuses a long name however it is reached`() = runTest {
        assertEquals(
            com.zango.pokertracker.data.repository.CreatePlayerResult.NameTooLong,
            repository.createPlayer(TOO_LONG),
        )
        repository.roster.value = listOf(Player(1, "Anna", 0))
        assertEquals(
            com.zango.pokertracker.data.repository.RenamePlayerResult.NameTooLong,
            repository.renamePlayer(1, TOO_LONG),
        )
    }
}
