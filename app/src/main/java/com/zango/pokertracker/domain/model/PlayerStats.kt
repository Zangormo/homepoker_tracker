package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.money.sum

/**
 * One player's part in one game, seen from the player's side rather than the game's.
 *
 * The figures are derived from the same rows the live game works from — buy-ins, chips sold back
 * to the bank, and the stack counted at the end — so a player's history and the game it came from
 * can never tell different stories.
 */
data class PlayerGameResult(
    val gameId: Long,
    val gameName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val isInProgress: Boolean,
    val chipRate: ChipRate,
    val buyInCount: Int,
    val totalBuyIn: Money,
    /** Chips sold back to the bank mid-game. Already paid for, so they count as money out. */
    val returnedChips: Chips,
    /** The stack in front of them at the end. Null until it has been counted. */
    val finalChips: Chips?,
) {
    /** Every chip taken out of the game: sold back along the way plus held at the end. */
    val chipsOut: Chips? get() = finalChips?.let { it + returnedChips }

    val cashOut: Money? get() = chipsOut?.let { chipRate.cashFor(it) }

    /** Profit or loss for this game. Null while the stack is still uncounted. */
    val net: Money? get() = cashOut?.let { it - totalBuyIn }

    /** A game only counts towards lifetime profit once the player's stack has been counted. */
    val isSettled: Boolean get() = finalChips != null
}

/**
 * A roster player and everything they have ever played, newest game first.
 *
 * Lifetime profit deliberately covers only [settled] games. A player sitting in a game that is
 * still running has paid money in with no result yet, and booking that as a loss would show a
 * figure that is simply wrong until the night is over.
 */
data class PlayerStats(
    val player: Player,
    val games: List<PlayerGameResult>,
) {
    val gamesPlayed: Int get() = games.size

    val buyInCount: Int get() = games.sumOf { it.buyInCount }

    /** Every cent ever put on a table, whether or not the game it went into has finished. */
    val totalPaidIn: Money get() = games.map { it.totalBuyIn }.sum()

    val settled: List<PlayerGameResult> get() = games.filter { it.isSettled }

    val settledPaidIn: Money get() = settled.map { it.totalBuyIn }.sum()

    val cashedOut: Money get() = settled.mapNotNull { it.cashOut }.sum()

    val netProfit: Money get() = settled.mapNotNull { it.net }.sum()

    /** Games whose stack has not been counted yet, and so sit outside [netProfit]. */
    val openGames: Int get() = games.count { !it.isSettled }

    val hasResults: Boolean get() = settled.isNotEmpty()

    val hasPlayed: Boolean get() = games.isNotEmpty()

    /** Games won, for a rough read on how the player does. Ties and losses are not counted. */
    val gamesUp: Int get() = settled.count { it.net?.isPositive == true }
}
