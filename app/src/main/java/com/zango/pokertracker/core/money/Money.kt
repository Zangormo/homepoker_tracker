package com.zango.pokertracker.core.money

/**
 * An exact cash amount, stored as a whole number of *micros* (1 currency unit = 1,000,000 micros).
 *
 * Money is never modelled as [Double] or [Float] anywhere in this app. Stakes go as low as
 * 0.001 and binary floating point cannot represent those decimals exactly, so repeated buy-ins
 * and settlement arithmetic would accumulate drift. Every operation here is integer arithmetic;
 * a value becomes text only at the moment it is displayed.
 *
 * Arithmetic is overflow-checked and throws [ArithmeticException] rather than silently wrapping.
 */
@JvmInline
value class Money(val micros: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(micros, other.micros))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(micros, other.micros))

    operator fun times(factor: Long): Money = Money(Math.multiplyExact(micros, factor))

    operator fun times(factor: Int): Money = times(factor.toLong())

    operator fun unaryMinus(): Money = Money(Math.negateExact(micros))

    override fun compareTo(other: Money): Int = micros.compareTo(other.micros)

    val isZero: Boolean get() = micros == 0L
    val isPositive: Boolean get() = micros > 0L
    val isNegative: Boolean get() = micros < 0L

    fun abs(): Money = if (micros < 0) -this else this

    /**
     * Rounds to the nearest multiple of [unit], with halves rounded away from zero, so that
     * 0.005 rounds to 0.01 and -0.005 rounds to -0.01.
     *
     * Used when turning exact settlement amounts into figures people can actually hand over
     * in cash. The caller is responsible for redistributing whatever remainder this creates.
     */
    fun roundedToNearest(unit: Money): Money {
        require(unit.micros > 0) { "Rounding unit must be positive, was ${unit.micros}" }
        val quotient = micros / unit.micros
        val remainder = micros % unit.micros
        val absRemainder = if (remainder < 0) -remainder else remainder
        // Compare 2*|remainder| against the unit without multiplying, to stay overflow-free.
        val roundsAway = absRemainder >= unit.micros - absRemainder
        val step = if (!roundsAway) 0L else if (micros < 0) -1L else 1L
        return Money(Math.multiplyExact(Math.addExact(quotient, step), unit.micros))
    }

    /**
     * Renders the amount in plain decimal notation with no currency symbol and no locale-specific
     * grouping, e.g. `"12.00"`, `"4.50"`, `"0.001"`, `"-1.25"`.
     *
     * At least [minDecimals] places are always shown so ordinary amounts read like currency, and
     * up to [maxDecimals] are shown so micro-stakes blinds are not silently rounded to nothing.
     * Digits between the two bounds are kept only when they are significant.
     */
    fun format(minDecimals: Int = 2, maxDecimals: Int = MAX_SCALE): String {
        require(minDecimals in 0..MAX_SCALE) { "minDecimals out of range: $minDecimals" }
        require(maxDecimals in minDecimals..MAX_SCALE) { "maxDecimals out of range: $maxDecimals" }

        val value = if (maxDecimals == MAX_SCALE) this else roundedToNearest(Money(pow10(MAX_SCALE - maxDecimals)))

        // Split before taking absolute values: both halves are small enough that negating them
        // cannot overflow, which keeps Long.MIN_VALUE from being a special case.
        val whole = value.micros / MICROS_PER_UNIT
        val fraction = value.micros % MICROS_PER_UNIT
        val wholeAbs = if (whole < 0) -whole else whole
        val fractionAbs = if (fraction < 0) -fraction else fraction

        val digits = fractionAbs.toString().padStart(MAX_SCALE, '0')
        var end = maxDecimals
        while (end > minDecimals && digits[end - 1] == '0') end--

        val sign = if (value.micros < 0) "-" else ""
        return if (end == 0) "$sign$wholeAbs" else "$sign$wholeAbs.${digits.substring(0, end)}"
    }

    /** Like [format] but always carries an explicit sign, for net profit/loss figures. */
    fun formatSigned(minDecimals: Int = 2, maxDecimals: Int = MAX_SCALE): String =
        if (micros > 0) "+" + format(minDecimals, maxDecimals) else format(minDecimals, maxDecimals)

    override fun toString(): String = format()

    companion object {
        const val MICROS_PER_UNIT: Long = 1_000_000L

        /** Number of decimal places a micro-precision amount can represent. */
        const val MAX_SCALE: Int = 6

        val ZERO: Money = Money(0)

        fun ofUnits(units: Long): Money = Money(Math.multiplyExact(units, MICROS_PER_UNIT))

        private val POWERS_OF_TEN = longArrayOf(1, 10, 100, 1_000, 10_000, 100_000, 1_000_000)

        internal fun pow10(exponent: Int): Long = POWERS_OF_TEN[exponent]
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, money -> acc + money }
