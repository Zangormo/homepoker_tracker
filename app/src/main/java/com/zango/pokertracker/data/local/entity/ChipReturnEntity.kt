package com.zango.pokertracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Chips handed back to the bank mid-game in exchange for cash, while the player stays at the
 * table. It happens when the physical chips run out: someone with a big stack sells chips back so
 * the next buy-in can be paid out in them.
 *
 * One row per return, never a running total, for the same reason as [BuyInEntity]: the figure the
 * player is credited with is always the sum of what actually happened, and a mistaken entry can
 * be traced and removed.
 *
 * Stored in chips rather than cash. The chips are the physical thing that moved; the money is
 * derived from the game's chip value, which cannot change once the game has started.
 */
@Entity(
    tableName = "chip_returns",
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
data class ChipReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gamePlayerId: Long,
    val chips: Long,
    val createdAt: Long,
)
