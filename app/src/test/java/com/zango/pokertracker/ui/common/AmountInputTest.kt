package com.zango.pokertracker.ui.common

import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The messages a host actually reads when they mistype an amount.
 *
 * Every amount field in the app goes through these, so the same mistake has to be reported the
 * same way whether it is made while setting a game up or while adding a rebuy three hours later.
 */
class AmountInputTest {

    private val rate = ChipRate(5_000)

    // -----------------------------------------------------------------------------------------
    // Cash
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a well-formed positive amount parses with no complaint`() {
        val parsed = parsePositiveMoney("1.50", "Buy-in")

        assertEquals(Money(1_500_000), parsed.money)
        assertNull(parsed.error)
    }

    @Test
    fun `zero is refused by name, because a field is not a place to say nothing happened`() {
        val parsed = parsePositiveMoney("0", "Buy-in")

        assertNull(parsed.money)
        assertEquals("Buy-in must be greater than zero", parsed.error)
    }

    @Test
    fun `a negative amount is refused the same way`() {
        assertEquals(
            "Buy-in must be greater than zero",
            parsePositiveMoney("-1.00", "Buy-in").error,
        )
    }

    @Test
    fun `an empty field says which field is missing`() {
        assertEquals("Small blind is required", parsePositiveMoney("", "Small blind").error)
        assertEquals("Small blind is required", parsePositiveMoney("   ", "Small blind").error)
    }

    @Test
    fun `gibberish is answered with the shape that was expected`() {
        assertEquals(
            "Enter Buy-in as a number, for example 0.005",
            parsePositiveMoney("abc", "Buy-in").error,
        )
    }

    @Test
    fun `more decimals than money has are named as such`() {
        assertEquals(
            "Buy-in can have at most 6 decimals",
            parsePositiveMoney("1.0000001", "Buy-in").error,
        )
    }

    @Test
    fun `an amount too large to hold is reported rather than wrapped`() {
        assertEquals(
            "Buy-in is too large",
            parsePositiveMoney("99999999999999", "Buy-in").error,
        )
    }

    // -----------------------------------------------------------------------------------------
    // Chips
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a whole chip count parses`() {
        val parsed = parseChipCount("200", "Chip count", allowZero = false)

        assertEquals(Chips(200), parsed.chips)
        assertNull(parsed.error)
    }

    /**
     * Busting out with nothing is a real result, so a final stack may be zero; nobody buys in
     * for no chips, so a buy-in may not.
     */
    @Test
    fun `zero is allowed when counting a stack and refused when buying in`() {
        assertEquals(Chips.ZERO, parseChipCount("0", "Chip count", allowZero = true).chips)
        assertEquals(
            "Chips must be greater than zero",
            parseChipCount("0", "Chips", allowZero = false).error,
        )
    }

    @Test
    fun `half a chip does not exist and is said so plainly`() {
        assertEquals(
            "Chips come in whole numbers only",
            parseChipCount("1.5", "Chip count", allowZero = true).error,
        )
    }

    @Test
    fun `a chip count typed with a redundant point zero is accepted`() {
        assertEquals(Chips(200), parseChipCount("200.0", "Chip count", allowZero = true).chips)
    }

    @Test
    fun `an empty or malformed chip count names the field`() {
        assertEquals("Chip count is required", parseChipCount("", "Chip count", true).error)
        assertEquals(
            "Enter Chip count as a whole number",
            parseChipCount("-5", "Chip count", true).error,
        )
    }

    // -----------------------------------------------------------------------------------------
    // Cash that cannot be paid out in chips
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an amount that is a whole number of chips passes silently`() {
        assertNull(wholeChipsError(Money(1_000_000), rate))
    }

    /**
     * Refused where it is typed rather than left to surface as an unexplained gap at the end of
     * the night, and the message carries the leftover so the host can nudge the figure.
     */
    @Test
    fun `an amount that leaves a remainder says how much is left over`() {
        assertEquals(
            "1.002 is not a whole number of 0.005 chips (0.002 left over)",
            wholeChipsError(Money(1_002_000), rate),
        )
    }

    @Test
    fun `a one-to-one chip value can never leave a remainder above a micro`() {
        assertNull(wholeChipsError(Money(1), ChipRate(1)))
    }
}
