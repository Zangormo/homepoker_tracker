package com.zango.pokertracker.ui.players

import com.zango.pokertracker.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The orders the roster can be listed in, and the one filter over it.
 *
 * The awkward cases are the ones worth pinning down: players who have never sat down and so have
 * no figure to rank, and players who tie on the thing being sorted by.
 */
class PlayersOrderingTest {

    private fun row(
        name: String,
        games: Int = 0,
        profit: Money? = null,
        archived: Boolean = false,
    ) = PlayerRow(
        playerId = name.hashCode().toLong(),
        name = name,
        gamesPlayed = games,
        buyInCount = games,
        totalPaidIn = Money.ofUnits(games.toLong()),
        netProfit = profit,
        openGames = 0,
        isArchived = archived,
    )

    private val anna = row("Anna", games = 3, profit = Money.ofUnits(50))
    private val boris = row("boris", games = 9, profit = Money.ofUnits(-20))
    private val clara = row("Clara", games = 1, profit = Money.ofUnits(120))
    private val newcomer = row("Newcomer")

    private val roster = listOf(clara, newcomer, boris, anna)

    private fun names(sort: PlayerSort, onlyWithGames: Boolean = false) =
        roster.arrange(sort, onlyWithGames).map { it.name }

    @Test
    fun `alphabetical order ignores case`() {
        assertEquals(listOf("Anna", "boris", "Clara", "Newcomer"), names(PlayerSort.NAME_A_Z))
    }

    @Test
    fun `reverse alphabetical is the same order backwards`() {
        assertEquals(listOf("Newcomer", "Clara", "boris", "Anna"), names(PlayerSort.NAME_Z_A))
    }

    @Test
    fun `winnings run from biggest down, and the other way round`() {
        assertEquals(
            listOf("Clara", "Anna", "boris", "Newcomer"),
            names(PlayerSort.WINNINGS_MOST),
        )
        assertEquals(
            listOf("boris", "Anna", "Clara", "Newcomer"),
            names(PlayerSort.WINNINGS_LEAST),
        )
    }

    /**
     * Somebody with no settled game has no figure at all, which is not the same as a figure of
     * zero, so they belong at the end of both winnings orders rather than in the middle of one.
     */
    @Test
    fun `a player with no result sorts last whichever way winnings run`() {
        assertEquals("Newcomer", names(PlayerSort.WINNINGS_MOST).last())
        assertEquals("Newcomer", names(PlayerSort.WINNINGS_LEAST).last())
    }

    @Test
    fun `games run from most down, and the other way round`() {
        assertEquals(listOf("boris", "Anna", "Clara", "Newcomer"), names(PlayerSort.GAMES_MOST))
        assertEquals(listOf("Newcomer", "Clara", "Anna", "boris"), names(PlayerSort.GAMES_FEWEST))
    }

    /** Ties are broken by name, so the list does not reshuffle itself between rebuilds. */
    @Test
    fun `players level on games keep a stable order`() {
        val tied = listOf(row("Zoe", games = 2), row("Adam", games = 2), row("Mia", games = 2))

        assertEquals(
            listOf("Adam", "Mia", "Zoe"),
            tied.arrange(PlayerSort.GAMES_MOST, onlyWithGames = false).map { it.name },
        )
    }

    @Test
    fun `the filter drops everyone who has never played`() {
        assertEquals(
            listOf("Anna", "boris", "Clara"),
            names(PlayerSort.NAME_A_Z, onlyWithGames = true),
        )
    }

    @Test
    fun `filtering and ordering apply together`() {
        assertEquals(
            listOf("Clara", "Anna", "boris"),
            names(PlayerSort.WINNINGS_MOST, onlyWithGames = true),
        )
    }

    @Test
    fun `the filter keeps a player whose games are all still open`() {
        val awaitingResults = listOf(row("Pat", games = 2, profit = null))

        assertEquals(
            listOf("Pat"),
            awaitingResults.arrange(PlayerSort.NAME_A_Z, onlyWithGames = true).map { it.name },
        )
    }
}
