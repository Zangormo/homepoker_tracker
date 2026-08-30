package com.homepoker_tracker.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun `whole amounts show two decimal places`() {
        assertEquals("12.00", Money.ofUnits(12).format())
        assertEquals("0.00", Money.ZERO.format())
    }

    @Test
    fun `a single decimal place is padded to two`() {
        assertEquals("4.50", Money(4_500_000).format())
        assertEquals("0.10", Money(100_000).format())
    }

    @Test
    fun `micro stakes keep the precision they need`() {
        assertEquals("0.001", Money(1_000).format())
        assertEquals("0.002", Money(2_000).format())
        assertEquals("0.005", Money(5_000).format())
        assertEquals("0.000001", Money(1).format())
    }

    @Test
    fun `significant digits beyond two places are kept, insignificant zeros are dropped`() {
        assertEquals("1234.5678", Money(1_234_567_800).format())
        assertEquals("1.20", Money(1_200_000).format())
        assertEquals("1.234", Money(1_234_000).format())
    }

    @Test
    fun `negative amounts carry a leading minus`() {
        assertEquals("-4.50", Money(-4_500_000).format())
        assertEquals("-0.001", Money(-1_000).format())
    }

    @Test
    fun `formatting never uses locale specific separators`() {
        // Built by hand rather than via String.format, so a comma-decimal device locale
        // cannot turn "4.50" into "4,50" and break round-tripping or the exported text.
        assertEquals("1234.50", Money(1_234_500_000).format())
    }

    @Test
    fun `signed formatting marks winners and losers`() {
        assertEquals("+4.50", Money(4_500_000).formatSigned())
        assertEquals("-4.50", Money(-4_500_000).formatSigned())
        assertEquals("0.00", Money.ZERO.formatSigned())
    }

    @Test
    fun `fixed two decimal formatting rounds rather than truncates`() {
        assertEquals("0.01", Money(5_000).format(minDecimals = 2, maxDecimals = 2))
        assertEquals("0.00", Money(4_999).format(minDecimals = 2, maxDecimals = 2))
        assertEquals("-0.01", Money(-5_000).format(minDecimals = 2, maxDecimals = 2))
        assertEquals("1.23", Money(1_234_000).format(minDecimals = 2, maxDecimals = 2))
    }

    @Test
    fun `zero decimals drops the separator entirely`() {
        assertEquals("12", Money.ofUnits(12).format(minDecimals = 0, maxDecimals = 0))
        assertEquals("13", Money(12_500_000).format(minDecimals = 0, maxDecimals = 0))
    }

    @Test
    fun `zero minimum keeps whole amounts bare but still shows real decimals`() {
        assertEquals("12", Money.ofUnits(12).format(minDecimals = 0))
        assertEquals("0.001", Money(1_000).format(minDecimals = 0))
    }

    @Test
    fun `toString matches the default format`() {
        assertEquals("4.50", Money(4_500_000).toString())
    }

    @Test
    fun `extreme values format without overflowing`() {
        assertEquals("-9223372036854.775808", Money(Long.MIN_VALUE).format())
        assertEquals("9223372036854.775807", Money(Long.MAX_VALUE).format())
    }

    @Test
    fun `every formatted amount parses back to the same value`() {
        val samples = listOf(0L, 1L, 1_000L, 5_000L, 100_000L, 4_500_000L, -4_500_000L, 1_234_567_800L)
        for (micros in samples) {
            val money = Money(micros)
            assertEquals(money, MoneyParser.parseOrNull(money.format()))
        }
    }
}
