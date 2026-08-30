package com.homepoker_tracker.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {

    private fun micros(raw: String): Long? = MoneyParser.parseOrNull(raw)?.micros

    private fun error(raw: String): MoneyParseError =
        (MoneyParser.parse(raw) as MoneyParseResult.Invalid).error

    @Test
    fun `parses the shapes a host actually types`() {
        assertEquals(1_000L, micros("0.001"))
        assertEquals(1_500_000L, micros("1.5"))
        assertEquals(12_000_000L, micros("12"))
        assertEquals(0L, micros("0"))
    }

    @Test
    fun `parses full micro precision`() {
        assertEquals(1_234_567L, micros("1.234567"))
        assertEquals(1L, micros("0.000001"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(12_000_000L, micros("  12  "))
    }

    @Test
    fun `a comma is accepted as a decimal separator`() {
        // Soft keyboards emit whichever separator the device locale prefers.
        assertEquals(1_500_000L, micros("1,5"))
        assertEquals(1_000L, micros("0,001"))
    }

    @Test
    fun `a bare separator on either side is filled in`() {
        assertEquals(500_000L, micros(".5"))
        assertEquals(5_000_000L, micros("5."))
    }

    @Test
    fun `leading zeros are harmless`() {
        assertEquals(12_000_000L, micros("0012"))
        assertEquals(1_000L, micros("00.001"))
    }

    @Test
    fun `trailing zeros do not change the value`() {
        assertEquals(1_000_000L, micros("1.000000"))
        assertEquals(1_500_000L, micros("1.50"))
    }

    @Test
    fun `signs are honoured`() {
        assertEquals(-4_500_000L, micros("-4.5"))
        assertEquals(4_500_000L, micros("+4.5"))
    }

    @Test
    fun `empty input is reported as empty rather than malformed`() {
        assertEquals(MoneyParseError.EMPTY, error(""))
        assertEquals(MoneyParseError.EMPTY, error("   "))
    }

    @Test
    fun `non numeric input is malformed`() {
        assertEquals(MoneyParseError.MALFORMED, error("abc"))
        assertEquals(MoneyParseError.MALFORMED, error("1.2x"))
        assertEquals(MoneyParseError.MALFORMED, error("$1.20"))
        assertEquals(MoneyParseError.MALFORMED, error("1 000"))
    }

    @Test
    fun `grouping separators are rejected rather than guessed at`() {
        // "1,234" could be 1234 or 1.234 depending on locale; refusing beats picking wrong.
        assertEquals(MoneyParseError.MALFORMED, error("1,234.56"))
        assertEquals(MoneyParseError.MALFORMED, error("1.234,56"))
    }

    @Test
    fun `repeated separators are malformed`() {
        assertEquals(MoneyParseError.MALFORMED, error("1.2.3"))
        assertEquals(MoneyParseError.MALFORMED, error("1,2,3"))
    }

    @Test
    fun `a lone sign or separator is malformed`() {
        assertEquals(MoneyParseError.MALFORMED, error("-"))
        assertEquals(MoneyParseError.MALFORMED, error("+"))
        assertEquals(MoneyParseError.MALFORMED, error("."))
        assertEquals(MoneyParseError.MALFORMED, error("-."))
    }

    @Test
    fun `non ascii digits are malformed`() {
        assertEquals(MoneyParseError.MALFORMED, error("١٢"))
    }

    @Test
    fun `more than six decimals is a precision error, not a generic failure`() {
        assertEquals(MoneyParseError.TOO_MANY_DECIMALS, error("1.2345678"))
        assertEquals(MoneyParseError.TOO_MANY_DECIMALS, error("0.0000001"))
    }

    @Test
    fun `amounts too large for micros are out of range`() {
        assertEquals(MoneyParseError.OUT_OF_RANGE, error("99999999999999999999"))
        assertEquals(MoneyParseError.OUT_OF_RANGE, error("9223372036854776"))
    }

    @Test
    fun `parseOrNull returns null instead of a result for invalid input`() {
        assertNull(MoneyParser.parseOrNull("nope"))
    }
}
