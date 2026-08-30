package com.zango.pokertracker.core.money

/**
 * A count of physical chips. Always a whole number: half a chip does not exist on the table.
 *
 * Chips are not cash. What a chip is worth is a property of the game, held by [ChipRate];
 * converting between the two always goes through that rate.
 */
@JvmInline
value class Chips(val count: Long) : Comparable<Chips> {

    operator fun plus(other: Chips): Chips = Chips(Math.addExact(count, other.count))

    operator fun minus(other: Chips): Chips = Chips(Math.subtractExact(count, other.count))

    operator fun times(factor: Long): Chips = Chips(Math.multiplyExact(count, factor))

    operator fun unaryMinus(): Chips = Chips(Math.negateExact(count))

    override fun compareTo(other: Chips): Int = count.compareTo(other.count)

    val isZero: Boolean get() = count == 0L
    val isPositive: Boolean get() = count > 0L
    val isNegative: Boolean get() = count < 0L

    fun abs(): Chips = if (count < 0) -this else this

    override fun toString(): String = count.toString()

    companion object {
        val ZERO: Chips = Chips(0)
    }
}

@JvmName("sumChips")
fun Iterable<Chips>.sum(): Chips = fold(Chips.ZERO) { acc, chips -> acc + chips }

/** The cash value of a single chip, e.g. 5000 micros (0.005) for chips marked 1 at 0.005/0.01. */
@JvmInline
value class ChipRate(val chipValueMicros: Long) {

    init {
        require(chipValueMicros > 0) { "Chip value must be positive, was $chipValueMicros" }
    }

    val chipValue: Money get() = Money(chipValueMicros)

    fun cashFor(chips: Chips): Money = Money(Math.multiplyExact(chips.count, chipValueMicros))

    /**
     * Converts cash back into chips. The division must come out even; when it does not, the
     * caller gets the whole chips plus the leftover cash so it can surface a real error instead
     * of quietly dropping the remainder.
     */
    fun chipsFor(cash: Money): ChipConversion {
        val whole = cash.micros / chipValueMicros
        val remainder = cash.micros % chipValueMicros
        return if (remainder == 0L) {
            ChipConversion.Exact(Chips(whole))
        } else {
            ChipConversion.Inexact(Chips(whole), Money(remainder))
        }
    }

    companion object {
        /**
         * Derives the rate from how the table talks about itself: "our chips are marked 1/2 and
         * we play 0.005/0.01" is [cash] = 0.01, [chips] = 2, giving a chip value of 0.005.
         */
        fun derive(cash: Money, chips: Chips): ChipRateDerivation = when {
            !cash.isPositive -> ChipRateDerivation.Invalid(ChipRateError.NON_POSITIVE_CASH)
            !chips.isPositive -> ChipRateDerivation.Invalid(ChipRateError.NON_POSITIVE_CHIPS)
            cash.micros % chips.count != 0L ->
                ChipRateDerivation.Invalid(ChipRateError.NOT_DIVISIBLE)

            else -> ChipRateDerivation.Valid(ChipRate(cash.micros / chips.count))
        }
    }
}

sealed interface ChipConversion {
    data class Exact(val chips: Chips) : ChipConversion
    data class Inexact(val chips: Chips, val remainder: Money) : ChipConversion

    fun exactOrNull(): Chips? = (this as? Exact)?.chips
}

enum class ChipRateError {
    /** The cash side of the ratio was zero or negative. */
    NON_POSITIVE_CASH,

    /** The chip side of the ratio was zero or negative. */
    NON_POSITIVE_CHIPS,

    /** The cash amount does not divide evenly by the chip count, so no exact rate exists. */
    NOT_DIVISIBLE,
}

sealed interface ChipRateDerivation {
    data class Valid(val rate: ChipRate) : ChipRateDerivation
    data class Invalid(val error: ChipRateError) : ChipRateDerivation
}
