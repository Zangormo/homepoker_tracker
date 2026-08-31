package com.zango.pokertracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zango.pokertracker.data.local.entity.StakePresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StakePresetDao {

    /** By size, so the ladder reads the way a host would write it out. */
    @Query("SELECT * FROM stake_presets ORDER BY bigBlindMicros ASC, smallBlindMicros ASC")
    fun observeAll(): Flow<List<StakePresetEntity>>

    @Query("SELECT COUNT(*) FROM stake_presets")
    suspend fun count(): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM stake_presets
            WHERE smallBlindMicros = :smallBlindMicros AND bigBlindMicros = :bigBlindMicros
        )
        """
    )
    suspend fun exists(smallBlindMicros: Long, bigBlindMicros: Long): Boolean

    /** Ignores on conflict, so listing a level that is already there is simply a no-op. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(preset: StakePresetEntity): Long

    @Query(
        """
        DELETE FROM stake_presets
        WHERE smallBlindMicros = :smallBlindMicros AND bigBlindMicros = :bigBlindMicros
        """
    )
    suspend fun delete(smallBlindMicros: Long, bigBlindMicros: Long)
}
