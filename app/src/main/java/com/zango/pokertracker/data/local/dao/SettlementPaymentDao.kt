package com.zango.pokertracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zango.pokertracker.data.local.entity.SettlementPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementPaymentDao {

    @Query("SELECT * FROM settlement_payments WHERE gameId = :gameId")
    fun observeForGame(gameId: Long): Flow<List<SettlementPaymentEntity>>

    /**
     * Replaces on conflict so ticking a payment that is somehow already marked settles on one
     * row rather than failing the unique index.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: SettlementPaymentEntity): Long

    @Query(
        """
        DELETE FROM settlement_payments
        WHERE gameId = :gameId AND fromPlayerId = :fromPlayerId AND toPlayerId = :toPlayerId
        """
    )
    suspend fun delete(gameId: Long, fromPlayerId: Long, toPlayerId: Long)
}
