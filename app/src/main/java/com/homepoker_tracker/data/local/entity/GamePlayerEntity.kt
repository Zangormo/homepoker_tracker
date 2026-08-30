package com.homepoker_tracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A seat: one roster player in one game.
 *
 * Deleting a game cascades to its seats, but deleting a player who has ever sat down is refused,
 * because that would silently rewrite the results of past games. Archiving is the way out.
 */
@Entity(
    tableName = "game_players",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["gameId", "playerId"], unique = true),
        Index(value = ["playerId"]),
    ],
)
data class GamePlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val playerId: Long,
    val joinedAt: Long,
    val cashedOutAt: Long? = null,
    /** Chips in front of the player at the end. Null until counted. */
    val finalChipCount: Long? = null,
)
