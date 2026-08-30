package com.homepoker_tracker.domain.model

import com.homepoker_tracker.core.money.ChipRate
import com.homepoker_tracker.core.money.Money

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

    /** The default buy-in expressed in big blinds, or null when it is not a whole multiple. */
    val defaultBuyInInBigBlinds: Long?
        get() = if (bigBlind.isPositive && defaultBuyIn.micros % bigBlind.micros == 0L) {
            defaultBuyIn.micros / bigBlind.micros
        } else {
            null
        }
}
