package com.zango.pokertracker.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChipConversionTest {

    /** The worked example from the spec: table plays 0.005/0.01 on chips marked 1/2. */
    private val rate = ChipRate(5_000)

    @Test
    fun `chips convert to cash by multiplying by the chip value`() {
        assertEquals(Money(500_000), rate.cashFor(Chips(100)))
        assertEquals(Money(5_000), rate.cashFor(Chips(1)))
        assertEquals(Money.ZERO, rate.cashFor(Chips.ZERO))
    }

    @Test
    fun `cash converts back to chips when it divides evenly`() {
        assertEquals(ChipConversion.Exact(Chips(100)), rate.chipsFor(Money(500_000)))
        assertEquals(ChipConversion.Exact(Chips.ZERO), rate.chipsFor(Money.ZERO))
    }

    @Test
    fun `a cash amount that does not divide evenly surfaces the remainder`() {
        // 0.5001 is 100 chips plus 0.0001 that no whole number of chips can represent.
        val result = rate.chipsFor(Money(500_100))
        assertEquals(ChipConversion.Inexact(Chips(100), Money(100)), result)
        assertNull(result.exactOrNull())
    }

    @Test
    fun `exactOrNull unwraps a clean conversion`() {
        assertEquals(Chips(100), rate.chipsFor(Money(500_000)).exactOrNull())
    }

    @Test
    fun `conversion round trips for every whole chip count`() {
        for (count in 0..1_000L) {
            val chips = Chips(count)
            assertEquals(ChipConversion.Exact(chips), rate.chipsFor(rate.cashFor(chips)))
        }
    }

    @Test
    fun `negative cash converts with a signed remainder`() {
        // A losing net can be expressed in chips too; truncation stays toward zero.
        assertEquals(ChipConversion.Exact(Chips(-100)), rate.chipsFor(Money(-500_000)))
        assertEquals(
            ChipConversion.Inexact(Chips(-100), Money(-100)),
            rate.chipsFor(Money(-500_100)),
        )
    }

    @Test
    fun `a rate of one chip per unit is the degenerate identity case`() {
        val oneToOne = ChipRate(Money.MICROS_PER_UNIT)
        assertEquals(Money.ofUnits(25), oneToOne.cashFor(Chips(25)))
        assertEquals(ChipConversion.Exact(Chips(25)), oneToOne.chipsFor(Money.ofUnits(25)))
    }

    @Test
    fun `chip value is exposed as money`() {
        assertEquals(Money(5_000), rate.chipValue)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero chip value is rejected`() {
        ChipRate(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative chip value is rejected`() {
        ChipRate(-1)
    }

    @Test(expected = ArithmeticException::class)
    fun `an absurd chip count overflows loudly instead of wrapping`() {
        rate.cashFor(Chips(Long.MAX_VALUE))
    }

    @Test
    fun `deriving the rate from marked chips and real stakes`() {
        // "Our chips are marked 1/2 and we play 0.005/0.01" -> one chip is worth 0.005.
        val derived = ChipRate.derive(cash = Money(10_000), chips = Chips(2))
        assertEquals(ChipRateDerivation.Valid(ChipRate(5_000)), derived)
    }

    @Test
    fun `deriving from the small blind gives the same rate as the big blind`() {
        assertEquals(
            ChipRate.derive(cash = Money(5_000), chips = Chips(1)),
            ChipRate.derive(cash = Money(10_000), chips = Chips(2)),
        )
    }

    @Test
    fun `deriving a rate that is not a whole number of micros is rejected`() {
        // 0.01 across 3 chips is 0.00333... which micros cannot hold exactly.
        assertEquals(
            ChipRateDerivation.Invalid(ChipRateError.NOT_DIVISIBLE),
            ChipRate.derive(cash = Money(10_000), chips = Chips(3)),
        )
    }

    @Test
    fun `deriving from non positive inputs is rejected with a specific reason`() {
        assertEquals(
            ChipRateDerivation.Invalid(ChipRateError.NON_POSITIVE_CASH),
            ChipRate.derive(cash = Money.ZERO, chips = Chips(2)),
        )
        assertEquals(
            ChipRateDerivation.Invalid(ChipRateError.NON_POSITIVE_CASH),
            ChipRate.derive(cash = Money(-10_000), chips = Chips(2)),
        )
        assertEquals(
            ChipRateDerivation.Invalid(ChipRateError.NON_POSITIVE_CHIPS),
            ChipRate.derive(cash = Money(10_000), chips = Chips.ZERO),
        )
    }
}
