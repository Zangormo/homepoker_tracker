package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Money

/** Everything needed to start a game, already validated and in domain units. */
data class NewGameSetup(
    val name: String,
    val smallBlind: Money,
    val bigBlind: Money,
    val chipRate: ChipRate,
    val defaultBuyIn: Money,
    val payoutRounding: Money = DEFAULT_PAYOUT_ROUNDING,
    val entries: List<NewGameEntry>,
    /** Minutes between bomb pots, or null for a game without them. */
    val bombPotIntervalMinutes: Int? = null,
    val isFiretruckGame: Boolean = false,
    /**
     * Seat everyone in a random order. The [entries] are expected already shuffled: their order
     * is the order round the table.
     */
    val isRandomSeating: Boolean = false,
) {
    companion object {
        /** What the bomb pot timer offers before the host changes it. */
        const val DEFAULT_BOMB_POT_MINUTES: Int = 30

        /** A day. Anything longer is a typo rather than a schedule. */
        const val MAX_BOMB_POT_MINUTES: Int = 24 * 60

        /** 0.01: the smallest note-and-coin unit people can realistically hand each other. */
        val DEFAULT_PAYOUT_ROUNDING: Money = Money(10_000)

        /** Hosts almost always think of a buy-in as this many big blinds. */
        const val DEFAULT_BUY_IN_BIG_BLINDS: Long = 100
    }
}

/** One player's seat at a game about to start, with their possibly-overridden buy-in. */
data class NewGameEntry(
    val playerId: Long,
    val buyIn: Money,
)
