package com.zango.pokertracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zango.pokertracker.data.local.entity.ChipReturnEntity

@Dao
interface ChipReturnDao {

    @Insert
    suspend fun insert(chipReturn: ChipReturnEntity): Long

    @Query("DELETE FROM chip_returns WHERE id = :id")
    suspend fun delete(id: Long)
}
