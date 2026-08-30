package com.zango.pokertracker.data.local.converter

import androidx.room.TypeConverter
import com.zango.pokertracker.domain.model.GameStatus

/**
 * Status is stored by name rather than ordinal so that reordering the enum cannot silently
 * reinterpret existing rows.
 */
class Converters {

    @TypeConverter
    fun fromGameStatus(status: GameStatus): String = status.name

    @TypeConverter
    fun toGameStatus(value: String): GameStatus =
        runCatching { GameStatus.valueOf(value) }.getOrDefault(GameStatus.IN_PROGRESS)
}
