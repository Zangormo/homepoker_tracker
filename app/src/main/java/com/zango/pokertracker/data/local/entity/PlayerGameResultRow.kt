package com.zango.pokertracker.data.local.entity

import com.zango.pokertracker.domain.model.GameStatus

/**
 * One seat flattened into a single row: the game it belongs to, plus the buy-in and chip-return
 * totals counted in SQL.
 *
 * The player screens only ever need these aggregates, so they are summed in the query rather than
 * by loading every buy-in of every game a regular has played over the years.
 */
data class PlayerGameResultRow(
    val playerId: Long,
    val gameId: Long,
    val gameName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val status: GameStatus,
    val chipValueMicros: Long,
    val finalChipCount: Long?,
    val buyInCount: Int,
    val totalBuyInMicros: Long,
    val returnedChips: Long,
)
