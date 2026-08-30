package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipRate
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
) {
    val isInProgress: Boolean get() = status == GameStatus.IN_PROGRESS
}
