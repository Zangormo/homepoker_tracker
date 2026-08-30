package com.zango.pokertracker.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `one unit is a million micros`() {
        assertEquals(1_000_000L, Money.ofUnits(1).micros)
    }

    @Test
    fun `smallest supported stake survives as an exact integer`() {
        // 0.001 is the lowest small blind the app must support.
        assertEquals(1_000L, MoneyParser.parseOrNull("0.001")!!.micros)
    }

    @Test
    fun `addition and subtraction are exact`() {
        val a = Money(1_000)
        val b = Money(2_000)
        assertEquals(Money(3_000), a + b)
        assertEquals(Money(-1_000), a - b)
    }

    @Test
    fun `repeated addition of a third of a cent does not drift`() {
        // The whole reason Money exists: 3333 micros summed 3000 times is exactly 9999000,
        // whereas the equivalent Double arithmetic would not land on a round figure.
        val total = (1..3_000).fold(Money.ZERO) { acc, _ -> acc + Money(3_333) }
        assertEquals(Money(9_999_000), total)
    }

    @Test
    fun `multiplication scales micros`() {
        assertEquals(Money(500_000), Money(5_000) * 100L)
        assertEquals(Money(500_000), Money(5_000) * 100)
    }

    @Test
    fun `negation flips sign`() {
        assertEquals(Money(-4_500_000), -Money(4_500_000))
    }

    @Test
    fun `sign predicates and abs`() {
        assertTrue(Money.ZERO.isZero)
        assertTrue(Money(1).isPositive)
        assertTrue(Money(-1).isNegative)
        assertFalse(Money(-1).isPositive)
        assertEquals(Money(4_500_000), Money(-4_500_000).abs())
        assertEquals(Money(4_500_000), Money(4_500_000).abs())
    }

    @Test
    fun `comparison orders by micros`() {
        val sorted = listOf(Money(3), Money(-1), Money(0)).sorted()
        assertEquals(listOf(Money(-1), Money(0), Money(3)), sorted)
        assertTrue(Money(2) > Money(1))
    }

    @Test
    fun `sum of an empty list is zero`() {
        assertEquals(Money.ZERO, emptyList<Money>().sum())
    }

    @Test
    fun `sum adds every element`() {
        assertEquals(Money(6_000), listOf(Money(1_000), Money(2_000), Money(3_000)).sum())
    }

    @Test(expected = ArithmeticException::class)
    fun `addition overflow throws rather than wrapping`() {
        Money(Long.MAX_VALUE) + Money(1)
    }

    @Test(expected = ArithmeticException::class)
    fun `multiplication overflow throws rather than wrapping`() {
        Money(Long.MAX_VALUE) * 2L
    }

    @Test
    fun `rounding to the nearest cent rounds halves away from zero`() {
        val cent = Money(10_000)
        assertEquals(Money(10_000), Money(5_000).roundedToNearest(cent))
        assertEquals(Money(-10_000), Money(-5_000).roundedToNearest(cent))
        assertEquals(Money(0), Money(4_999).roundedToNearest(cent))
        assertEquals(Money(0), Money(-4_999).roundedToNearest(cent))
        assertEquals(Money(20_000), Money(15_000).roundedToNearest(cent))
    }

    @Test
    fun `rounding leaves exact multiples untouched`() {
        assertEquals(Money(4_500_000), Money(4_500_000).roundedToNearest(Money(10_000)))
    }

    @Test
    fun `rounding to a unit larger than the value collapses to zero`() {
        assertEquals(Money.ZERO, Money(1_000).roundedToNearest(Money(10_000)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rounding unit must be positive`() {
        Money(1_000).roundedToNearest(Money.ZERO)
    }
}
