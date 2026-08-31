package com.zango.pokertracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stake level offered when setting a game up.
 *
 * The list starts as the standard ladder, grows by a level when a game is played on new blinds,
 * and is the host's to edit: a one-off night at odd stakes should not clutter the picker forever.
 * That is why these are stored rather than read back out of the games that used them.
 */
@Entity(
    tableName = "stake_presets",
    indices = [Index(value = ["smallBlindMicros", "bigBlindMicros"], unique = true)],
)
data class StakePresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smallBlindMicros: Long,
    val bigBlindMicros: Long,
    val createdAt: Long,
)
