package com.zango.pokertracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.zango.pokertracker.data.local.entity.BuyInEntity

@Dao
interface BuyInDao {

    @Insert
    suspend fun insert(buyIn: BuyInEntity): Long

    @Insert
    suspend fun insertAll(buyIns: List<BuyInEntity>): List<Long>
}
