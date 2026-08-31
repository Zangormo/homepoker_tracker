package com.zango.pokertracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zango.pokertracker.data.local.entity.PlayerEntity
import com.zango.pokertracker.data.local.entity.PlayerGameResultRow
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {

    @Query("SELECT * FROM players WHERE isArchived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveRoster(): Flow<List<PlayerEntity>>

    /** Everyone ever added, hidden regulars included, for the roster management screen. */
    @Query("SELECT * FROM players ORDER BY name COLLATE NOCASE ASC")
    fun observeAllPlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun findById(id: Long): PlayerEntity?

    @Query("SELECT * FROM players WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): PlayerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(player: PlayerEntity): Long

    @Query("UPDATE players SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE players SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT COUNT(*) FROM game_players WHERE playerId = :playerId")
    suspend fun gamesPlayed(playerId: Long): Int

    /**
     * Only ever reaches the database for a player who has never sat down: the seats foreign key
     * is `RESTRICT`, so deleting anyone with history would fail rather than rewrite past games.
     */
    @Query("DELETE FROM players WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Every seat anyone has ever taken, newest game first, with that seat's buy-in and
     * chip-return totals. One query feeds both the player list and a single player's history,
     * so the two can never disagree.
     */
    @Query(
        """
        SELECT gp.playerId AS playerId,
            g.id AS gameId,
            g.name AS gameName,
            g.startedAt AS startedAt,
            g.endedAt AS endedAt,
            g.status AS status,
            g.chipValueMicros AS chipValueMicros,
            gp.finalChipCount AS finalChipCount,
            (SELECT COUNT(*) FROM buy_ins b WHERE b.gamePlayerId = gp.id) AS buyInCount,
            (SELECT COALESCE(SUM(b.amountMicros), 0) FROM buy_ins b
                WHERE b.gamePlayerId = gp.id) AS totalBuyInMicros,
            (SELECT COALESCE(SUM(r.chips), 0) FROM chip_returns r
                WHERE r.gamePlayerId = gp.id) AS returnedChips
        FROM game_players gp
        JOIN games g ON gp.gameId = g.id
        ORDER BY g.startedAt DESC
        """
    )
    fun observePlayerGameResults(): Flow<List<PlayerGameResultRow>>
}
