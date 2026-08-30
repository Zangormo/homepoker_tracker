package com.zango.pokertracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zango.pokertracker.domain.model.GameStatus

/**
 * One night's game. Every monetary column is a whole number of micros and the chip value is the
 * cash worth of a single chip, which is the single source of truth for chip/cash conversion.
 */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val smallBlindMicros: Long,
    val bigBlindMicros: Long,
    val chipValueMicros: Long,
    val defaultBuyInMicros: Long,
    /** Cash unit settlements are rounded to when people actually pay each other. */
    @ColumnInfo(defaultValue = "10000") val payoutRoundingMicros: Long = 10_000,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: GameStatus = GameStatus.IN_PROGRESS,
)
