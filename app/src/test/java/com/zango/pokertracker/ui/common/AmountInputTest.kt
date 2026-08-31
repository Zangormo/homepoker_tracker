package com.zango.pokertracker.ui.common

import com.zango.pokertracker.R
import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the app decides to say when a host mistypes an amount.
 *
 * Every amount field goes through these, so the same mistake has to be reported the same way
 * whether it is made while setting a game up or while adding a rebuy three hours later.
 *
 * The assertions are on *which* message applies and what goes in it, not on its English. That is
 * the point of naming the string rather than writing it: the app can be translated without these
 * tests knowing anything about the language, and rewording the copy no longer breaks them.
 */
class AmountInputTest {

    private val rate = ChipRate(5_000)
    private val buyIn = UiText.of(R.string.label_buy_in)
    private val chipCount = UiText.of(R.string.label_chip_count)

    // -----------------------------------------------------------------------------------------
    // Cash
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a well-formed positive amount parses with no complaint`() {
        val parsed = parsePositiveMoney("1.50", buyIn)

        assertEquals(Money(1_500_000), parsed.money)
        assertNull(parsed.error)
    }

    @Test
    fun `zero is refused, and the message names the field it came from`() {
        val parsed = parsePositiveMoney("0", buyIn)

        assertNull(parsed.money)
        assertEquals(UiText.of(R.string.error_amount_positive, buyIn), parsed.error)
    }

    @Test
    fun `a negative amount is refused the same way`() {
        assertEquals(
            UiText.of(R.string.error_amount_positive, buyIn),
            parsePositiveMoney("-1.00", buyIn).error,
        )
    }

    @Test
    fun `an empty field says which field is missing`() {
        val smallBlind = UiText.of(R.string.create_small_blind)

        assertEquals(
            UiText.of(R.string.error_amount_required, smallBlind),
            parsePositiveMoney("", smallBlind).error,
        )
        assertEquals(
            UiText.of(R.string.error_amount_required, smallBlind),
            parsePositiveMoney("   ", smallBlind).error,
        )
    }

    @Test
    fun `gibberish is answered with the shape that was expected`() {
        assertEquals(
            UiText.of(R.string.error_amount_malformed, buyIn),
            parsePositiveMoney("abc", buyIn).error,
        )
    }

    /** The limit travels as an argument, so a translation cannot drift from the real one. */
    @Test
    fun `more decimals than money has is reported with the number allowed`() {
        assertEquals(
            UiText.plural(R.plurals.error_amount_decimals, 6, buyIn, 6),
            parsePositiveMoney("1.0000001", buyIn).error,
        )
        assertEquals(6, Money.MAX_SCALE)
    }

    @Test
    fun `an amount too large to hold is reported rather than wrapped`() {
        assertEquals(
            UiText.of(R.string.error_amount_too_large, buyIn),
            parsePositiveMoney("99999999999999", buyIn).error,
        )
    }

    // -----------------------------------------------------------------------------------------
    // Chips
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a whole chip count parses`() {
        val parsed = parseChipCount("200", chipCount, allowZero = false)

        assertEquals(Chips(200), parsed.chips)
        assertNull(parsed.error)
    }

    /**
     * Busting out with nothing is a real result, so a final stack may be zero; nobody buys in
     * for no chips, so a buy-in may not.
     */
    @Test
    fun `zero is allowed when counting a stack and refused when buying in`() {
        assertEquals(Chips.ZERO, parseChipCount("0", chipCount, allowZero = true).chips)
        assertEquals(
            UiText.of(R.string.error_amount_positive, chipCount),
            parseChipCount("0", chipCount, allowZero = false).error,
        )
    }

    /** No field name in this one: half a chip is wrong wherever it is typed. */
    @Test
    fun `half a chip does not exist and is said so plainly`() {
        assertEquals(
            UiText.of(R.string.error_chips_not_whole),
            parseChipCount("1.5", chipCount, allowZero = true).error,
        )
    }

    @Test
    fun `a chip count typed with a redundant point zero is accepted`() {
        assertEquals(Chips(200), parseChipCount("200.0", chipCount, allowZero = true).chips)
    }

    @Test
    fun `an empty or malformed chip count names the field`() {
        assertEquals(
            UiText.of(R.string.error_amount_required, chipCount),
            parseChipCount("", chipCount, allowZero = true).error,
        )
        assertEquals(
            UiText.of(R.string.error_chips_whole_number, chipCount),
            parseChipCount("-5", chipCount, allowZero = true).error,
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
     * the night. The message carries the amount, the chip value and the leftover, so the host can
     * see which of the three to nudge.
     */
    @Test
    fun `an amount that leaves a remainder carries all three figures`() {
        assertEquals(
            UiText.of(R.string.error_not_whole_chips, "1.002", "0.005", "0.002"),
            wholeChipsError(Money(1_002_000), rate),
        )
    }

    @Test
    fun `a one-to-one chip value can never leave a remainder above a micro`() {
        assertNull(wholeChipsError(Money(1), ChipRate(1)))
    }
}
