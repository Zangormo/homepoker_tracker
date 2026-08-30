package com.zango.pokertracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zango.pokertracker.data.local.entity.GamePlayerEntity

@Dao
interface GamePlayerDao {

    @Insert
    suspend fun insert(seat: GamePlayerEntity): Long

    @Insert
    suspend fun insertAll(seats: List<GamePlayerEntity>): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM game_players WHERE gameId = :gameId AND playerId = :playerId)")
    suspend fun isSeated(gameId: Long, playerId: Long): Boolean

    @Query("UPDATE game_players SET cashedOutAt = :at, finalChipCount = :chips WHERE id = :id")
    suspend fun cashOut(id: Long, at: Long, chips: Long)

    /** Returns a seat to active play, discarding the chip count that went with the cash-out. */
    @Query("UPDATE game_players SET cashedOutAt = NULL, finalChipCount = NULL WHERE id = :id")
    suspend fun undoCashOut(id: Long)

    @Query("UPDATE game_players SET finalChipCount = :chips WHERE id = :id")
    suspend fun setFinalChipCount(id: Long, chips: Long?)

    /**
     * Closes out everyone still sitting when the game ends. Only seats that already have a
     * counted stack are touched, so an uncounted player is never silently booked as zero.
     */
    @Query(
        """
        UPDATE game_players SET cashedOutAt = :at
        WHERE gameId = :gameId AND cashedOutAt IS NULL AND finalChipCount IS NOT NULL
        """
    )
    suspend fun cashOutRemaining(gameId: Long, at: Long)
}
