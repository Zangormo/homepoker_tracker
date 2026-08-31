package com.zango.pokertracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.zango.pokertracker.data.local.entity.GameEntity
import com.zango.pokertracker.data.local.entity.GameSummaryRow
import com.zango.pokertracker.data.local.entity.GameWithPlayers
import com.zango.pokertracker.domain.model.GameStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Insert
    suspend fun insert(game: GameEntity): Long

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    fun observeWithPlayers(id: Long): Flow<GameWithPlayers?>

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
                WHERE gps.gameId = g.id)
            - (SELECT COALESCE(SUM(r.chips), 0) * g.chipValueMicros FROM chip_returns r
                JOIN game_players gpr ON r.gamePlayerId = gpr.id
                WHERE gpr.gameId = g.id) AS totalBuyInMicros
        FROM games g
        ORDER BY g.startedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<GameSummaryRow>>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun loadWithPlayers(id: Long): GameWithPlayers?

    @Query("UPDATE games SET status = :status, endedAt = :endedAt WHERE id = :id")
    suspend fun finish(id: Long, status: GameStatus, endedAt: Long)

    /** Records whether every payment the settlement calls for has been handed over. */
    @Query("UPDATE games SET isFullyPaid = :fullyPaid WHERE id = :id")
    suspend fun setFullyPaid(id: Long, fullyPaid: Boolean)

    /**
     * Seats and buy-ins go with it: both cascade from their foreign keys. Roster players are
     * untouched, because they belong to the roster rather than to any one game.
     */
    @Query("DELETE FROM games WHERE id = :id")
    suspend fun delete(id: Long)
}
