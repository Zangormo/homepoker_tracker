package com.homepoker_tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.homepoker_tracker.data.local.entity.GameEntity
import com.homepoker_tracker.data.local.entity.GameSummaryRow
import com.homepoker_tracker.data.local.entity.GameWithPlayers
import com.homepoker_tracker.domain.model.GameStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Insert
    suspend fun insert(game: GameEntity): Long

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun findById(id: Long): GameEntity?

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    fun observeWithPlayers(id: Long): Flow<GameWithPlayers?>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun findWithPlayers(id: Long): GameWithPlayers?

    @Query("SELECT * FROM games WHERE status = :status ORDER BY startedAt DESC")
    fun observeByStatus(status: GameStatus): Flow<List<GameEntity>>

    @Query("SELECT * FROM games ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<GameEntity>>

    /**
     * Newest first, with player and buy-in totals counted in SQL rather than by loading every
     * row of every game the host has ever played.
     */
    @Query(
        """
        SELECT g.*,
            (SELECT COUNT(*) FROM game_players gp WHERE gp.gameId = g.id) AS playerCount,
            (SELECT COUNT(*) FROM buy_ins b
                JOIN game_players gpb ON b.gamePlayerId = gpb.id
                WHERE gpb.gameId = g.id) AS buyInCount,
            (SELECT COALESCE(SUM(b.amountMicros), 0) FROM buy_ins b
                JOIN game_players gps ON b.gamePlayerId = gps.id
                WHERE gps.gameId = g.id) AS totalBuyInMicros
        FROM games g
        ORDER BY g.startedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<GameSummaryRow>>

    @Query("UPDATE games SET status = :status, endedAt = :endedAt WHERE id = :id")
    suspend fun finish(id: Long, status: GameStatus, endedAt: Long)

    @Query("UPDATE games SET payoutRoundingMicros = :micros WHERE id = :id")
    suspend fun setPayoutRounding(id: Long, micros: Long)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun delete(id: Long)
}
