package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipConversion
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money

/** A game as the history list knows it: settings, plus totals, without the individual rows. */
data class GameSummary(
    val game: Game,
    val playerCount: Int,
    val buyInCount: Int,
    val totalOnTable: Money,
) {
    val chipsOnTable: Chips?
        get() = (game.chipRate.chipsFor(totalOnTable) as? ChipConversion.Exact)?.chips

    /** How long the game ran, or null while it is still going. */
    val durationMillis: Long?
        get() = game.endedAt?.let { it - game.startedAt }
}
