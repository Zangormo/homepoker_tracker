package com.zango.pokertracker.ui.common

import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** The live "this is what you are setting up" readout beside a buy-in field. */
class AmountPreviewTest {

    private val rate = ChipRate(5_000)

    @Test
    fun `nothing typed yet is an empty preview rather than a zero`() {
        val preview = AmountPreview.of(null, rate)

        assertTrue(preview.isEmpty)
        assertNull(preview.cash)
        assertNull(preview.chips)
    }

    @Test
    fun `cash without a known chip rate shows the cash alone`() {
        val preview = AmountPreview.of(Money(1_000_000), null)

        assertFalse(preview.isEmpty)
        assertEquals(Money(1_000_000), preview.cash)
        assertNull(preview.chips)
        assertNull(preview.leftOver)
    }

    @Test
    fun `an exact amount shows the chips it buys and nothing left over`() {
        val preview = AmountPreview.of(Money(1_000_000), rate)

        assertEquals(Money(1_000_000), preview.cash)
        assertEquals(Chips(200), preview.chips)
        assertNull(preview.leftOver)
    }

    /**
     * The leftover is surfaced rather than rounded away: it is the difference between what the
     * host is about to take and what they can actually hand over in chips.
     */
    @Test
    fun `an amount that does not divide evenly keeps the remainder visible`() {
        val preview = AmountPreview.of(Money(1_002_000), rate)

        assertEquals(Chips(200), preview.chips)
        assertEquals(Money(2_000), preview.leftOver)
    }

    @Test
    fun `a zero amount is a real value, not an absent one`() {
        val preview = AmountPreview.of(Money.ZERO, rate)

        assertFalse(preview.isEmpty)
        assertEquals(Chips.ZERO, preview.chips)
    }
}
