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
    /**
     * Whether every payment this game's settlement calls for has been ticked off as handed
     * over. Recorded rather than derived: payments are computed from the results, and the game
     * hub cannot recompute every settlement it has ever produced just to colour a border.
     */
    @ColumnInfo(defaultValue = "0") val isFullyPaid: Boolean = false,
    /**
     * Minutes between bomb pots, counted from [startedAt], or null when the game has none. The
     * schedule is derived from the start rather than stored as a next-due time, so it survives a
     * killed process or a reboot without anything having to be written as each one comes round.
     */
    val bombPotIntervalMinutes: Int? = null,
    /** Whether the game tab keeps the firetruck count. */
    @ColumnInfo(defaultValue = "0") val isFiretruckGame: Boolean = false,
)
