package com.zango.pokertracker.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipsTest {

    @Test
    fun `chip arithmetic is exact`() {
        assertEquals(Chips(300), Chips(100) + Chips(200))
        assertEquals(Chips(-100), Chips(100) - Chips(200))
        assertEquals(Chips(500), Chips(100) * 5L)
        assertEquals(Chips(-100), -Chips(100))
    }

    @Test
    fun `sign predicates and abs`() {
        assertTrue(Chips.ZERO.isZero)
        assertTrue(Chips(1).isPositive)
        assertTrue(Chips(-1).isNegative)
        assertEquals(Chips(100), Chips(-100).abs())
    }

    @Test
    fun `chips sort and sum`() {
        assertEquals(listOf(Chips(-1), Chips(0), Chips(3)), listOf(Chips(3), Chips(-1), Chips(0)).sorted())
        assertEquals(Chips(600), listOf(Chips(100), Chips(200), Chips(300)).sum())
        assertEquals(Chips.ZERO, emptyList<Chips>().sum())
    }

    @Test(expected = ArithmeticException::class)
    fun `chip overflow throws rather than wrapping`() {
        Chips(Long.MAX_VALUE) + Chips(1)
    }

    @Test
    fun `chip counts render as plain integers`() {
        assertEquals("1500", Chips(1_500).toString())
    }
}

class ChipsParserTest {

    private fun count(raw: String): Long? = ChipsParser.parseOrNull(raw)?.count

    private fun error(raw: String): ChipsParseError =
        (ChipsParser.parse(raw) as ChipsParseResult.Invalid).error

    @Test
    fun `parses whole chip counts`() {
        assertEquals(0L, count("0"))
        assertEquals(1_500L, count("1500"))
        assertEquals(1_500L, count("  1500  "))
        assertEquals(1_500L, count("+1500"))
    }

    @Test
    fun `a redundant zero fraction is tolerated`() {
        assertEquals(1_500L, count("1500.0"))
        assertEquals(1_500L, count("1500,00"))
    }

    @Test
    fun `a real fraction is reported as not whole`() {
        assertEquals(ChipsParseError.NOT_WHOLE, error("10.5"))
        assertEquals(ChipsParseError.NOT_WHOLE, error("10,5"))
        assertEquals(ChipsParseError.NOT_WHOLE, error("0.001"))
    }

    @Test
    fun `empty input is reported as empty`() {
        assertEquals(ChipsParseError.EMPTY, error(""))
        assertEquals(ChipsParseError.EMPTY, error("   "))
    }

    @Test
    fun `negative and non numeric counts are malformed`() {
        assertEquals(ChipsParseError.MALFORMED, error("-5"))
        assertEquals(ChipsParseError.MALFORMED, error("abc"))
        assertEquals(ChipsParseError.MALFORMED, error("1 500"))
        assertEquals(ChipsParseError.MALFORMED, error("."))
    }

    @Test
    fun `counts too large for a long are out of range`() {
        assertEquals(ChipsParseError.OUT_OF_RANGE, error("99999999999999999999"))
    }

    @Test
    fun `parseOrNull returns null for invalid input`() {
        assertNull(ChipsParser.parseOrNull("10.5"))
    }
}
