package com.zango.pokertracker.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A seat together with who is sitting in it and every buy-in they have made. */
data class GamePlayerWithDetails(
    @Embedded val seat: GamePlayerEntity,
    @Relation(parentColumn = "playerId", entityColumn = "id")
    val player: PlayerEntity,
    @Relation(parentColumn = "id", entityColumn = "gamePlayerId")
    val buyIns: List<BuyInEntity>,
    @Relation(parentColumn = "id", entityColumn = "gamePlayerId")
    val chipReturns: List<ChipReturnEntity>,
)

/**
 * A whole game in one observable object. The live screen's totals (money on the table, buy-in
 * count, chips in play) are all derived from this rather than from separate aggregate queries,
 * so they can never disagree with the player list they are shown beside.
 */
data class GameWithPlayers(
    @Embedded val game: GameEntity,
    @Relation(entity = GamePlayerEntity::class, parentColumn = "id", entityColumn = "gameId")
    val seats: List<GamePlayerWithDetails>,
)
