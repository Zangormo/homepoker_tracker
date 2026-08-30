package com.homepoker_tracker.data.local.entity

import androidx.room.Embedded

/**
 * A game plus the few aggregates the history list shows, gathered in one query.
 *
 * History does not need every buy-in row, so counting them in SQL keeps the list cheap however
 * many games have piled up over the years.
 */
data class GameSummaryRow(
    @Embedded val game: GameEntity,
    val playerCount: Int,
    val buyInCount: Int,
    val totalBuyInMicros: Long,
)
