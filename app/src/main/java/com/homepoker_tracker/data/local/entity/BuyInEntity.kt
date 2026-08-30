package com.homepoker_tracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single buy-in or rebuy. One row per transaction and never a running total: the amount a
 * player is in for is always derived by summing their rows, so a mistaken entry can be traced.
 */
@Entity(
    tableName = "buy_ins",
    foreignKeys = [
        ForeignKey(
            entity = GamePlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["gamePlayerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["gamePlayerId"])],
)
data class BuyInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gamePlayerId: Long,
    val amountMicros: Long,
    val createdAt: Long,
)
