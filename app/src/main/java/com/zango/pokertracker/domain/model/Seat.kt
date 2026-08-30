package com.zango.pokertracker.domain.model

import com.zango.pokertracker.core.money.ChipConversion
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.money.sum

/**
 * One player's participation in one game.
 *
 * [totalBuyIn] and [returnedChips] are always recomputed from their rows rather than cached, so
 * the displayed totals and the underlying transactions cannot drift apart.
 */
data class Seat(
    val id: Long,
    val player: Player,
    val joinedAt: Long,
    val cashedOutAt: Long?,
    val finalChips: Chips?,
    val buyIns: List<BuyIn>,
    val chipReturns: List<ChipReturn> = emptyList(),
) {
    val totalBuyIn: Money get() = buyIns.map { it.amount }.sum()

    val buyInCount: Int get() = buyIns.size

    /** Chips sold back to the bank mid-game. Already paid for in cash, so no longer in play. */
    val returnedChips: Chips get() = chipReturns.map { it.chips }.sum()

    val hasReturns: Boolean get() = chipReturns.isNotEmpty()

    val isActive: Boolean get() = cashedOutAt == null

    val isCashedOut: Boolean get() = cashedOutAt != null

    val hasChipCount: Boolean get() = finalChips != null

    /**
     * Every chip this player takes out of the game: what they sold back along the way plus what
     * is in front of them at the end. Null until the final stack is counted.
     */
    val chipsOut: Chips? get() = finalChips?.let { it + returnedChips }
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

    /** Every buy-in ever paid in, whether or not the chips are still out there. */
    val totalBuyIns: Money get() = seats.map { it.totalBuyIn }.sum()

    /** Chips sold back to the bank mid-game, across everyone. */
    val returnedChips: Chips get() = seats.map { it.returnedChips }.sum()

    val returnedCash: Money get() = game.chipRate.cashFor(returnedChips)

    val hasReturns: Boolean get() = !returnedChips.isZero

    /**
     * The money the bank is actually holding: everything paid in, less what has been paid back
     * out for returned chips. This is what the remaining chips on the table are worth.
     */
    val totalOnTable: Money get() = totalBuyIns - returnedCash

    val totalBuyInCount: Int get() = seats.sumOf { it.buyInCount }

    /**
     * The chips still in play, expressed from the cash side. Inexact when some buy-in was not a
     * whole number of chips, which is itself worth surfacing rather than hiding.
     */
    val chipsOnTable: ChipConversion get() = game.chipRate.chipsFor(totalOnTable)

    val countedChips: Chips get() = seats.mapNotNull { it.finalChips }.sum()

    val seatsAwaitingCount: List<Seat> get() = seats.filter { !it.hasChipCount }

    /** What the player leaves with: their returned chips plus their final stack, in cash. */
    fun cashOutValueOf(seat: Seat): Money? =
        seat.chipsOut?.let { game.chipRate.cashFor(it) }

    /** Profit or loss for a seat: what they took off the table minus what they put on it. */
    fun netOf(seat: Seat): Money? =
        cashOutValueOf(seat)?.let { it - seat.totalBuyIn }
}
