package com.zango.pokertracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One settlement payment the host has ticked off as actually handed over.
 *
 * Payments themselves are never stored: they are derived from the results every time the
 * settlement is shown, so what is displayed cannot drift from what was played. A row here is only
 * a mark against one of them, which is why it carries the pair and the amount rather than an id —
 * there is nothing to point an id at.
 *
 * The greedy matching zeroes out at least one side of every transfer it makes, so the same pair
 * can never appear twice in one settlement, which is what makes the pair a safe key. The amount
 * rides along so a mark can only ever tick off the figure it was made against.
 */
@Entity(
    tableName = "settlement_payments",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["gameId", "fromPlayerId", "toPlayerId"], unique = true)],
)
data class SettlementPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val fromPlayerId: Long,
    val toPlayerId: Long,
    val amountMicros: Long,
    val markedAt: Long,
)
