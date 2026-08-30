package com.zango.pokertracker.domain.settlement

import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.money.sum
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.Player

/** What one player finished up or down, before any rounding for payout. */
data class PlayerNet(
    val player: Player,
    val net: Money,
)

/** One person handing cash to another. The amount is always positive. */
data class Payment(
    val from: Player,
    val to: Player,
    val amount: Money,
)

/**
 * A complete settlement: who pays whom, plus everything the host needs to explain it.
 *
 * [nets] are the exact results. [settledNets] are those results after rounding to a payable cash
 * unit, and they are what [payments] actually satisfy; the two differ by at most the rounding
 * unit per player.
 */
data class Settlement(
    val payments: List<Payment>,
    val nets: List<PlayerNet>,
    val settledNets: List<PlayerNet>,
    val roundingUnit: Money,
    /** How much was moved onto [adjustedPlayer] so the rounded results still cancel out. */
    val roundingAdjustment: Money,
    val adjustedPlayer: Player?,
    /**
     * Sum of the exact nets. Zero for a properly reconciled game; non-zero means the chip counts
     * did not match the buy-ins and the host chose to proceed anyway.
     */
    val imbalance: Money,
    /** Amounts left over when an imbalance means the payments cannot square everyone up. */
    val unsettled: List<PlayerNet>,
) {
    val hasRoundingAdjustment: Boolean get() = !roundingAdjustment.isZero

    val isBalanced: Boolean get() = imbalance.isZero
}

/**
 * Turns a table of results into the shortest list of cash handovers that settles it.
 *
 * The matching itself is the standard greedy pass: biggest debtor against biggest creditor,
 * transfer the smaller of the two, drop whoever reaches zero, repeat. That yields at most
 * `n - 1` payments, which is the fewest possible in the general case.
 */
object SettlementCalculator {

    fun settle(nets: List<PlayerNet>, roundingUnit: Money): Settlement {
        require(roundingUnit.isPositive) { "Rounding unit must be positive" }

        val imbalance = nets.map { it.net }.sum()
        val rounded = roundNets(nets, roundingUnit, imbalance)
        val matching = match(rounded.nets)

        return Settlement(
            payments = matching.payments,
            nets = nets,
            settledNets = rounded.nets,
            roundingUnit = roundingUnit,
            roundingAdjustment = rounded.adjustment,
            adjustedPlayer = rounded.adjustedPlayer,
            imbalance = imbalance,
            unsettled = matching.unsettled,
        )
    }

    private class Claim(val player: Player, var amount: Money)

    private class Matching(val payments: List<Payment>, val unsettled: List<PlayerNet>)

    private fun match(nets: List<PlayerNet>): Matching {
        // Sorted by magnitude descending, with the name as a tie-break so the same results always
        // produce the same list of payments rather than one that shuffles between recompositions.
        val creditors = nets.filter { it.net.isPositive }
            .sortedWith(compareByDescending<PlayerNet> { it.net }.thenBy { it.player.name })
            .map { Claim(it.player, it.net) }
        val debtors = nets.filter { it.net.isNegative }
            .sortedWith(compareBy<PlayerNet> { it.net }.thenBy { it.player.name })
            .map { Claim(it.player, -it.net) }

        val payments = mutableListOf<Payment>()
        var creditorIndex = 0
        var debtorIndex = 0
        while (creditorIndex < creditors.size && debtorIndex < debtors.size) {
            val creditor = creditors[creditorIndex]
            val debtor = debtors[debtorIndex]
            val transfer = minOf(creditor.amount, debtor.amount)
            payments += Payment(from = debtor.player, to = creditor.player, amount = transfer)
            creditor.amount -= transfer
            debtor.amount -= transfer
            if (creditor.amount.isZero) creditorIndex++
            if (debtor.amount.isZero) debtorIndex++
        }

        // Anything still outstanding can only happen when the results do not sum to zero, i.e.
        // the host overrode a chip-count mismatch. Report it rather than inventing a payer.
        val unsettled = buildList {
            creditors.drop(creditorIndex).filter { !it.amount.isZero }
                .forEach { add(PlayerNet(it.player, it.amount)) }
            debtors.drop(debtorIndex).filter { !it.amount.isZero }
                .forEach { add(PlayerNet(it.player, -it.amount)) }
        }
        return Matching(payments, unsettled)
    }

    private class RoundedNets(
        val nets: List<PlayerNet>,
        val adjustment: Money,
        val adjustedPlayer: Player?,
    )

    /**
     * Rounds the results, not the payments, to the unit people can actually hand over.
     *
     * Rounding the nets first means every transfer the greedy pass produces is automatically a
     * multiple of that unit, because the minimum of two multiples is one too. Rounding the
     * payments afterwards instead cannot work: a two-player game has a single payment, so
     * absorbing the leftover back into the largest payment would simply undo its own rounding.
     *
     * Rounded results are multiples of the unit, so their total is too, which makes the leftover
     * a whole multiple as well. It lands on whoever had the largest result, keeping their figure
     * payable and every other player exactly on their rounded number.
     */
    private fun roundNets(
        nets: List<PlayerNet>,
        unit: Money,
        imbalance: Money,
    ): RoundedNets {
        val rounded = nets.map { PlayerNet(it.player, it.net.roundedToNearest(unit)) }
        val target = imbalance.roundedToNearest(unit)
        val remainder = target - rounded.map { it.net }.sum()
        if (remainder.isZero || nets.isEmpty()) {
            return RoundedNets(rounded, Money.ZERO, null)
        }

        val index = nets.indices.maxWithOrNull(
            compareBy<Int> { nets[it].net.abs() }.thenByDescending { nets[it].player.name },
        )!!
        val adjusted = rounded.toMutableList()
        adjusted[index] = adjusted[index].copy(net = adjusted[index].net + remainder)
        return RoundedNets(adjusted, remainder, nets[index].player)
    }
}

/**
 * Settles a finished game. Only seats with a counted stack take part; the end-game screen is
 * responsible for refusing to get here while a stack is still uncounted.
 */
fun GameSnapshot.settle(): Settlement {
    val nets = seats.mapNotNull { seat ->
        netOf(seat)?.let { PlayerNet(seat.player, it) }
    }
    return SettlementCalculator.settle(nets, game.payoutRounding)
}
