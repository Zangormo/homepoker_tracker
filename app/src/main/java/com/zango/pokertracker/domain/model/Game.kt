package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money

enum class GameStatus { IN_PROGRESS, FINISHED }

data class Player(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val isArchived: Boolean = false,
)

data class BuyIn(
    val id: Long,
    val amount: Money,
    val createdAt: Long,
)

/**
 * Chips sold back to the bank part-way through, while the player carries on. The cash they were
 * paid is [Game.chipRate] applied to [chips].
 */
data class ChipReturn(
    val id: Long,
    val chips: Chips,
    val createdAt: Long,
)

/**
 * A game's settings, in domain types. The entity stores raw micros; everything above the data
 * layer works in [Money] and [ChipRate] so a bare `Long` can never be mistaken for the wrong unit.
 */
data class Game(
    val id: Long,
    val name: String,
    val smallBlind: Money,
    val bigBlind: Money,
    val chipRate: ChipRate,
    val defaultBuyIn: Money,
    val payoutRounding: Money,
    val startedAt: Long,
    val endedAt: Long?,
    val status: GameStatus,
    /** Whether every payment the settlement calls for has been ticked off as handed over. */
    val isFullyPaid: Boolean = false,
) {
    val isInProgress: Boolean get() = status == GameStatus.IN_PROGRESS
}

/**
 * A settlement payment the host has marked as actually paid.
 *
 * Identified by the pair and the amount rather than by an id, because payments are derived from
 * the results rather than stored; there is no payment row for a mark to point at.
 */
data class SettledPayment(
    val gameId: Long,
    val fromPlayerId: Long,
    val toPlayerId: Long,
    val amount: Money,
)
