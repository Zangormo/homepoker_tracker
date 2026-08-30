package com.zango.pokertracker.core.money

/** Why a string could not be read as a chip count. */
enum class ChipsParseError {
    /** Nothing was typed, or only whitespace. */
    EMPTY,

    /** Not a non-negative number: stray characters, or a negative count. */
    MALFORMED,

    /** A number with a real fractional part. Chips come in whole units only. */
    NOT_WHOLE,

    /** A well-formed count, but too large to hold in a [Long]. */
    OUT_OF_RANGE,
}

sealed interface ChipsParseResult {
    data class Valid(val chips: Chips) : ChipsParseResult
    data class Invalid(val error: ChipsParseError) : ChipsParseResult
}

/**
 * Reads a host-typed chip count. Whole numbers only, but a trailing `.0` is tolerated because
 * hosts habitually type it; anything with a real fraction is rejected as [ChipsParseError.NOT_WHOLE]
 * so the UI can say precisely what is wrong rather than "invalid input".
 */
object ChipsParser {

    fun parse(raw: String): ChipsParseResult {
        val input = raw.trim()
        if (input.isEmpty()) return ChipsParseResult.Invalid(ChipsParseError.EMPTY)

        val body = if (input[0] == '+') input.substring(1) else input
        if (body.isEmpty()) return ChipsParseResult.Invalid(ChipsParseError.MALFORMED)

        val separatorAt = body.indexOfFirst { it == '.' || it == ',' }
        val wholeText = if (separatorAt >= 0) body.substring(0, separatorAt) else body
        val fractionText = if (separatorAt >= 0) body.substring(separatorAt + 1) else ""

        if (wholeText.isEmpty() && fractionText.isEmpty()) {
            return ChipsParseResult.Invalid(ChipsParseError.MALFORMED)
        }
        if (!wholeText.isAsciiDigits() || !fractionText.isAsciiDigits()) {
            return ChipsParseResult.Invalid(ChipsParseError.MALFORMED)
        }
        if (fractionText.any { it != '0' }) {
            return ChipsParseResult.Invalid(ChipsParseError.NOT_WHOLE)
        }

        return try {
            ChipsParseResult.Valid(Chips(if (wholeText.isEmpty()) 0L else wholeText.toLong()))
        } catch (_: NumberFormatException) {
            ChipsParseResult.Invalid(ChipsParseError.OUT_OF_RANGE)
        }
    }

    fun parseOrNull(raw: String): Chips? = (parse(raw) as? ChipsParseResult.Valid)?.chips

    private fun String.isAsciiDigits(): Boolean = all { it in '0'..'9' }
}
