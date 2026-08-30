package com.homepoker_tracker.data.local.converter

import androidx.room.TypeConverter
import com.homepoker_tracker.domain.model.GameStatus

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
