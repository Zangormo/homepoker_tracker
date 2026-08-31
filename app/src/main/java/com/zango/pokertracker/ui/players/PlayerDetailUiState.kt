package com.zango.pokertracker.ui.players

import com.zango.pokertracker.core.money.Money

/** One night, from this player's side of the table. */
data class PlayerGameRow(
    val gameId: Long,
    val gameName: String,
    val dateLabel: String,
    val isInProgress: Boolean,
    val buyInCount: Int,
    val totalBuyIn: Money,
    val cashOut: Money?,
    val net: Money?,
)

data class PlayerDetailUiState(
    val isLoading: Boolean = true,
    /** The player was deleted while their history was open. */
    val isMissing: Boolean = false,
    val name: String = "",
    val isArchived: Boolean = false,
    val gamesPlayed: Int = 0,
    val gamesUp: Int = 0,
    val buyInCount: Int = 0,
    /** Everything ever paid onto a table, finished games and unfinished ones alike. */
    val totalPaidIn: Money = Money.ZERO,
    val cashedOut: Money = Money.ZERO,
    /** Lifetime profit over settled games. Null until a single result exists. */
    val netProfit: Money? = null,
    /** Games with no chip count yet, which is why they sit outside [netProfit]. */
    val openGames: Int = 0,
    val games: List<PlayerGameRow> = emptyList(),
) {
    val hasPlayed: Boolean get() = games.isNotEmpty()
}
