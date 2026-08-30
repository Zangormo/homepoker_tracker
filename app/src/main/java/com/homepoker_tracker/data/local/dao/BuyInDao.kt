package com.homepoker_tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.homepoker_tracker.data.local.entity.BuyInEntity

@Dao
interface BuyInDao {

    @Insert
    suspend fun insert(buyIn: BuyInEntity): Long

    @Insert
    suspend fun insertAll(buyIns: List<BuyInEntity>): List<Long>

    @Query("SELECT * FROM buy_ins WHERE gamePlayerId = :gamePlayerId ORDER BY createdAt ASC")
    suspend fun findForSeat(gamePlayerId: Long): List<BuyInEntity>

    @Query("DELETE FROM buy_ins WHERE id = :id")
    suspend fun delete(id: Long)
}
