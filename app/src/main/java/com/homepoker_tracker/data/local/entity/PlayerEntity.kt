package com.homepoker_tracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The persistent roster. Players are created once and reused across every game, so rows here are
 * never deleted once they have played; [isArchived] hides a lapsed regular from the picker while
 * keeping their history intact.
 */
@Entity(
    tableName = "players",
    indices = [Index(value = ["name"], unique = true)],
)
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
)
