package com.homepoker_tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.homepoker_tracker.data.local.entity.PlayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {

    @Query("SELECT * FROM players WHERE isArchived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveRoster(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY isArchived ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun findById(id: Long): PlayerEntity?

    @Query("SELECT * FROM players WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): PlayerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(player: PlayerEntity): Long

    @Query("UPDATE players SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE players SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}
