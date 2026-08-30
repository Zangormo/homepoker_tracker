package com.zango.pokertracker.core.money

/** Why a string could not be read as a [Money] amount. */
enum class MoneyParseError {
    /** Nothing was typed, or only whitespace. */
    EMPTY,

    /** Not a decimal number: stray characters, two separators, a lone sign or separator. */
    MALFORMED,

    /** More decimal places than micro precision can hold ([Money.MAX_SCALE]). */
    TOO_MANY_DECIMALS,

    /** A well-formed number, but too large to hold in micros. */
    OUT_OF_RANGE,
}

sealed interface MoneyParseResult {
    data class Valid(val money: Money) : MoneyParseResult
    data class Invalid(val error: MoneyParseError) : MoneyParseResult
}

/**
 * Reads user-typed cash amounts such as `"0.001"`, `"1.5"`, `"12"`.
 *
 * Deliberately strict: it does not accept grouping separators, exponents or currency symbols,
 * because silently reinterpreting a typo as a different amount is worse than rejecting it. Both
 * `.` and `,` are accepted as the decimal separator, since soft keyboards emit whichever the
 * device locale prefers, but a string containing both is ambiguous and rejected.
 *
 * A leading `-` is accepted so the same parser can read net results, which are negative for a
 * losing player. Fields that must not be negative (buy-ins, blinds, chip value) are responsible
 * for rejecting negatives themselves.
 */
object MoneyParser {

    fun parse(raw: String): MoneyParseResult {
        val input = raw.trim()
        if (input.isEmpty()) return MoneyParseResult.Invalid(MoneyParseError.EMPTY)

        val negative = input[0] == '-'
        val body = if (negative || input[0] == '+') input.substring(1) else input
        if (body.isEmpty()) return MoneyParseResult.Invalid(MoneyParseError.MALFORMED)

        val dot = body.indexOf('.')
        val comma = body.indexOf(',')
        if (dot >= 0 && comma >= 0) return MoneyParseResult.Invalid(MoneyParseError.MALFORMED)

        val separator = if (dot >= 0) '.' else ','
        val separatorAt = if (dot >= 0) dot else comma
        if (separatorAt >= 0 && body.indexOf(separator, separatorAt + 1) >= 0) {
            return MoneyParseResult.Invalid(MoneyParseError.MALFORMED)
        }

        val wholeText = if (separatorAt >= 0) body.substring(0, separatorAt) else body
        val fractionText = if (separatorAt >= 0) body.substring(separatorAt + 1) else ""

        if (wholeText.isEmpty() && fractionText.isEmpty()) {
            return MoneyParseResult.Invalid(MoneyParseError.MALFORMED)
        }
        if (!wholeText.isAsciiDigits() || !fractionText.isAsciiDigits()) {
            return MoneyParseResult.Invalid(MoneyParseError.MALFORMED)
        }
        if (fractionText.length > Money.MAX_SCALE) {
            return MoneyParseResult.Invalid(MoneyParseError.TOO_MANY_DECIMALS)
        }

        return try {
            val whole = if (wholeText.isEmpty()) 0L else wholeText.toLong()
            val fractionMicros = fractionText.padEnd(Money.MAX_SCALE, '0').toLong()
            val magnitude = Math.addExact(
                Math.multiplyExact(whole, Money.MICROS_PER_UNIT),
                fractionMicros,
            )
            MoneyParseResult.Valid(Money(if (negative) -magnitude else magnitude))
        } catch (_: ArithmeticException) {
            MoneyParseResult.Invalid(MoneyParseError.OUT_OF_RANGE)
        } catch (_: NumberFormatException) {
            MoneyParseResult.Invalid(MoneyParseError.OUT_OF_RANGE)
        }
    }

    fun parseOrNull(raw: String): Money? = (parse(raw) as? MoneyParseResult.Valid)?.money

    /** Restricted to ASCII: [Char.isDigit] would also accept digits from other scripts. */
    private fun String.isAsciiDigits(): Boolean = all { it in '0'..'9' }
}
