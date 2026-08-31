package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A player's lifetime record, built from the same rows the live game works from.
 *
 * Fixtures use the spec's table throughout: 0.005 a chip, so a 1.00 buy-in is 200 chips.
 */
class PlayerStatsTest {

    private fun result(
        gameId: Long,
        buyIns: Int = 1,
        paidIn: Money = Money(1_000_000),
        returned: Long = 0,
        finalChips: Long? = null,
        inProgress: Boolean = false,
    ) = PlayerGameResult(
        gameId = gameId,
        gameName = "Game $gameId",
        startedAt = 1_000L * gameId,
        endedAt = if (inProgress) null else 2_000L * gameId,
        isInProgress = inProgress,
        chipRate = Fixture.chipRate,
        buyInCount = buyIns,
        totalBuyIn = paidIn,
        returnedChips = Chips(returned),
        finalChips = finalChips?.let { Chips(it) },
    )

    private fun stats(vararg games: PlayerGameResult) =
        PlayerStats(Fixture.player(1, "Anna"), games.toList())

    @Test
    fun `a game is won or lost by what came off the table against what went on it`() {
        // 1.00 in, 300 chips out at 0.005 each is 1.50, so half a unit up.
        val game = result(1, finalChips = 300)

        assertEquals(Chips(300), game.chipsOut)
        assertEquals(Money(1_500_000), game.cashOut)
        assertEquals(Money(500_000), game.net)
        assertTrue(game.isSettled)
    }

    @Test
    fun `chips sold back to the bank count as money out just like the final stack`() {
        // Anna bought in twice for 2.00, sold 200 chips back, and finished holding 150.
        val game = result(1, buyIns = 2, paidIn = Money(2_000_000), returned = 200, finalChips = 150)

        assertEquals(Chips(350), game.chipsOut)
        assertEquals(Money(1_750_000), game.cashOut)
        assertEquals(Money(-250_000), game.net)
    }

    @Test
    fun `a stack nobody has counted yet has no result at all`() {
        val game = result(1, inProgress = true)

        assertNull(game.chipsOut)
        assertNull(game.cashOut)
        assertNull(game.net)
        assertFalse(game.isSettled)
    }

    @Test
    fun `lifetime totals add up the games behind them`() {
        val stats = stats(
            result(1, buyIns = 2, paidIn = Money(2_000_000), finalChips = 600),
            result(2, finalChips = 100),
        )

        assertEquals(2, stats.gamesPlayed)
        assertEquals(3, stats.buyInCount)
        assertEquals(Money(3_000_000), stats.totalPaidIn)
        // 600 chips is 3.00 and 100 chips is 0.50: 3.50 out against 3.00 in.
        assertEquals(Money(3_500_000), stats.cashedOut)
        assertEquals(Money(500_000), stats.netProfit)
        assertEquals(1, stats.gamesUp)
    }

    /**
     * The point of keeping unfinished games out: the money is already paid in, but the result is
     * not decided, and booking it as a loss would show a figure that is simply wrong.
     */
    @Test
    fun `money in a game still being played is paid in but not yet lost`() {
        val stats = stats(
            result(1, finalChips = 400),
            result(2, inProgress = true),
        )

        assertEquals(Money(2_000_000), stats.totalPaidIn)
        assertEquals(Money(1_000_000), stats.settledPaidIn)
        assertEquals(Money(1_000_000), stats.netProfit)
        assertEquals(1, stats.openGames)
        assertTrue(stats.hasResults)
    }

    @Test
    fun `a player who has only ever sat in unfinished games has no profit to show`() {
        val stats = stats(result(1, inProgress = true))

        assertFalse(stats.hasResults)
        assertTrue(stats.hasPlayed)
        assertEquals(Money.ZERO, stats.netProfit)
        assertEquals(Money(1_000_000), stats.totalPaidIn)
    }

    @Test
    fun `a player who has never played reports nothing rather than zero results`() {
        val stats = stats()

        assertFalse(stats.hasPlayed)
        assertFalse(stats.hasResults)
        assertEquals(0, stats.gamesPlayed)
        assertEquals(0, stats.buyInCount)
        assertEquals(Money.ZERO, stats.totalPaidIn)
    }

    @Test
    fun `breaking exactly even is not counted as a game won`() {
        val stats = stats(result(1, finalChips = 200))

        assertEquals(Money.ZERO, stats.netProfit)
        assertEquals(0, stats.gamesUp)
        assertTrue(stats.hasResults)
    }
}
