package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipConversion
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.money.sum

/**
 * One player's participation in one game.
 *
 * [totalBuyIn] is always recomputed from [buyIns] rather than cached, so the displayed total and
 * the underlying transaction list cannot drift apart.
 */
data class Seat(
    val id: Long,
    val player: Player,
    val joinedAt: Long,
    val cashedOutAt: Long?,
    val finalChips: Chips?,
    val buyIns: List<BuyIn>,
) {
    val totalBuyIn: Money get() = buyIns.map { it.amount }.sum()

    val buyInCount: Int get() = buyIns.size

    val isActive: Boolean get() = cashedOutAt == null

    val isCashedOut: Boolean get() = cashedOutAt != null

    val hasChipCount: Boolean get() = finalChips != null
}

/**
 * A whole game and everyone in it, as one consistent value. Every headline figure on the live
 * screen is derived here so the totals and the player list are always the same observation.
 */
data class GameSnapshot(
    val game: Game,
    val seats: List<Seat>,
) {
    val activeSeats: List<Seat> get() = seats.filter { it.isActive }

    val cashedOutSeats: List<Seat> get() = seats.filter { it.isCashedOut }

    /** Sum of every buy-in row across every player: the money physically on the table. */
    val totalOnTable: Money get() = seats.map { it.totalBuyIn }.sum()

    val totalBuyInCount: Int get() = seats.sumOf { it.buyInCount }

    /**
     * The money on the table expressed in chips. Inexact when some buy-in was not a whole
     * number of chips, which is itself worth surfacing rather than hiding.
     */
    val chipsOnTable: ChipConversion get() = game.chipRate.chipsFor(totalOnTable)

    val countedChips: Chips get() = seats.mapNotNull { it.finalChips }.sum()

    val seatsAwaitingCount: List<Seat> get() = seats.filter { !it.hasChipCount }

    fun cashOutValueOf(seat: Seat): Money? =
        seat.finalChips?.let { game.chipRate.cashFor(it) }

    /** Profit or loss for a seat: what they took off the table minus what they put on it. */
    fun netOf(seat: Seat): Money? =
        cashOutValueOf(seat)?.let { it - seat.totalBuyIn }
}
