package com.zango.pokertracker.ui.creategame

import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The spec's worked example as a form: a 0.005/0.01 table on chips marked 1/2, a 100 BB buy-in,
 * and two players selected.
 */
private fun validForm() = CreateGameForm(
    name = "Thursday",
    smallBlind = "0.005",
    bigBlind = "0.01",
    deriveChipValue = true,
    chipsPerBigBlind = "2",
    buyInMode = BuyInMode.BIG_BLINDS,
    buyInBigBlinds = "100",
    payoutRounding = "0.01",
    selection = linkedMapOf(1L to null, 2L to null),
)

class CreateGameFormTest {

    @Test
    fun `the worked example validates into a persistable setup`() {
        val validation = validForm().validate()
        val setup = validation.setup

        assertNotNull(setup)
        assertEquals("Thursday", setup!!.name)
        assertEquals(Money(5_000), setup.smallBlind)
        assertEquals(Money(10_000), setup.bigBlind)
        assertEquals(ChipRate(5_000), setup.chipRate)
        assertEquals(Money(1_000_000), setup.defaultBuyIn)
        assertEquals(Money(10_000), setup.payoutRounding)
        assertEquals(listOf(1L, 2L), setup.entries.map { it.playerId })
        assertTrue(setup.entries.all { it.buyIn == Money(1_000_000) })
    }

    @Test
    fun `the game name is required`() {
        val validation = validForm().copy(name = "   ").validate()
        assertEquals(UiText.of(R.string.error_game_name_required), validation.nameError)
        assertFalse(validation.isValid)
    }

    @Test
    fun `blinds accept the smallest stakes the app supports`() {
        val validation = validForm()
            .copy(smallBlind = "0.001", bigBlind = "0.002", chipsPerBigBlind = "2")
            .validate()

        assertEquals(Money(1_000), validation.smallBlind)
        assertEquals(Money(2_000), validation.bigBlind)
        assertEquals(ChipRate(1_000), validation.chipRate)
        assertTrue(validation.isValid)
    }

    @Test
    fun `the big blind must be larger than the small blind`() {
        val validation = validForm().copy(smallBlind = "0.01", bigBlind = "0.01").validate()
        assertEquals(UiText.of(R.string.error_big_blind_too_small), validation.bigBlindError)
        assertNull(validation.bigBlind)
    }

    @Test
    fun `blinds must be greater than zero`() {
        val validation = validForm().copy(smallBlind = "0").validate()
        assertEquals(
            UiText.of(R.string.error_amount_positive, UiText.of(R.string.create_small_blind)),
            validation.smallBlindError,
        )
    }

    @Test
    fun `an unparseable blind explains the expected shape`() {
        val validation = validForm().copy(smallBlind = "abc").validate()
        assertEquals(
            UiText.of(R.string.error_amount_malformed, UiText.of(R.string.create_small_blind)),
            validation.smallBlindError,
        )
    }

    @Test
    fun `the chip helper derives the rate from the chip markings`() {
        // Chips marked 1/2 on a 0.005/0.01 table: the big blind is 2 chips, so a chip is 0.005.
        val validation = validForm().copy(chipsPerBigBlind = "2").validate()
        assertEquals(ChipRate(5_000), validation.chipRate)
    }

    @Test
    fun `a chip marking that does not divide evenly is refused`() {
        val validation = validForm().copy(chipsPerBigBlind = "3").validate()
        assertEquals(
            UiText.of(R.string.error_chip_split, "0.01", 3L),
            validation.chipValueError,
        )
        assertFalse(validation.isValid)
    }

    @Test
    fun `the chip value can be typed directly instead`() {
        val validation = validForm()
            .copy(deriveChipValue = false, chipValue = "0.005", chipsPerBigBlind = "")
            .validate()

        assertEquals(ChipRate(5_000), validation.chipRate)
        assertTrue(validation.isValid)
    }

    @Test
    fun `the helper waits for the blinds before it can derive anything`() {
        val validation = validForm().copy(smallBlind = "", bigBlind = "").validate()
        assertEquals(UiText.of(R.string.error_blinds_first), validation.chipValueError)
    }

    @Test
    fun `a buy-in given in big blinds is multiplied out`() {
        val validation = validForm().copy(buyInBigBlinds = "50").validate()
        assertEquals(Money(500_000), validation.defaultBuyIn)
    }

    @Test
    fun `a buy-in given as cash is taken at face value`() {
        val validation = validForm()
            .copy(buyInMode = BuyInMode.CASH, buyInCash = "2.50")
            .validate()

        assertEquals(Money(2_500_000), validation.defaultBuyIn)
        assertTrue(validation.isValid)
    }

    @Test
    fun `a fractional big blind multiple is refused`() {
        val validation = validForm().copy(buyInBigBlinds = "2.5").validate()
        assertEquals(
            UiText.of(R.string.error_buy_in_whole_big_blinds),
            validation.buyInError,
        )
    }

    @Test
    fun `a buy-in that is not a whole number of chips is refused at setup`() {
        // A chip is worth 0.005, so 1.0025 cannot be paid out in chips.
        val validation = validForm()
            .copy(buyInMode = BuyInMode.CASH, buyInCash = "1.0025")
            .validate()

        assertEquals(
            UiText.of(R.string.error_not_whole_chips, UiText.Cash("1.0025"), UiText.Cash("0.005"), UiText.Cash("0.0025")),
            validation.buyInError,
        )
        assertFalse(validation.isValid)
    }

    @Test
    fun `a game needs at least one player`() {
        val validation = validForm().copy(selection = emptyMap()).validate()
        assertEquals(UiText.of(R.string.error_pick_a_player), validation.playersError)
        assertFalse(validation.isValid)
    }

    @Test
    fun `a per player override replaces the default for that player only`() {
        val validation = validForm()
            .copy(selection = linkedMapOf(1L to null, 2L to Money(500_000)))
            .validate()

        val entries = validation.setup!!.entries.associate { it.playerId to it.buyIn }
        assertEquals(Money(1_000_000), entries[1L])
        assertEquals(Money(500_000), entries[2L])
    }

    @Test
    fun `an override that is not a whole number of chips is reported against that player`() {
        val validation = validForm()
            .copy(selection = linkedMapOf(1L to null, 2L to Money(1_002_500)))
            .validate()

        assertEquals(
            UiText.of(R.string.error_not_whole_chips, UiText.Cash("1.0025"), UiText.Cash("0.005"), UiText.Cash("0.0025")),
            validation.overrideErrors[2L],
        )
        assertFalse(validation.isValid)
    }

    @Test
    fun `a negative override is refused`() {
        val validation = validForm()
            .copy(selection = linkedMapOf(1L to Money(-1_000_000)))
            .validate()

        assertEquals(UiText.of(R.string.error_buy_in_positive), validation.overrideErrors[1L])
    }

    @Test
    fun `selection order is the order players were tapped`() {
        val validation = validForm()
            .copy(selection = linkedMapOf(7L to null, 3L to null, 5L to null))
            .validate()

        assertEquals(listOf(7L, 3L, 5L), validation.setup!!.entries.map { it.playerId })
    }

    @Test
    fun `the payout rounding unit is part of the game`() {
        val validation = validForm().copy(payoutRounding = "0.001").validate()
        assertEquals(Money(1_000), validation.setup!!.payoutRounding)
    }

    @Test
    fun `a zero rounding unit is refused`() {
        val validation = validForm().copy(payoutRounding = "0").validate()
        assertEquals(
            UiText.of(R.string.error_amount_positive, UiText.of(R.string.label_rounding_unit)),
            validation.payoutRoundingError,
        )
        assertFalse(validation.isValid)
    }

    @Test
    fun `an empty form reports what is missing without crashing`() {
        val validation = CreateGameForm().validate()

        assertEquals(UiText.of(R.string.error_game_name_required), validation.nameError)
        assertEquals(
            UiText.of(R.string.error_amount_required, UiText.of(R.string.create_small_blind)),
            validation.smallBlindError,
        )
        assertEquals(
            UiText.of(R.string.error_amount_required, UiText.of(R.string.create_big_blind)),
            validation.bigBlindError,
        )
        assertEquals(
            UiText.of(R.string.error_amount_required, UiText.of(R.string.label_big_blind_in_chips)),
            validation.chipValueError,
        )
        assertEquals(UiText.of(R.string.error_pick_a_player), validation.playersError)
        assertFalse(validation.isValid)
    }

    @Test
    fun `values that do parse are exposed even while the form is incomplete`() {
        // The screen shows a live chip preview before the host has picked anyone.
        val validation = CreateGameForm(
            smallBlind = "0.005",
            bigBlind = "0.01",
            chipsPerBigBlind = "2",
        ).validate()

        assertEquals(ChipRate(5_000), validation.chipRate)
        assertEquals(Money(1_000_000), validation.defaultBuyIn)
        assertFalse(validation.isValid)
    }

    @Test
    fun `side games are off unless picked`() {
        val setup = validForm().validate().setup!!

        assertNull(setup.bombPotIntervalMinutes)
        assertFalse(setup.isFiretruckGame)
    }

    @Test
    fun `picked side games are carried into the setup`() {
        val setup = validForm()
            .copy(bombPotEnabled = true, bombPotMinutes = "45", isFiretruckGame = true)
            .validate().setup!!

        assertEquals(45, setup.bombPotIntervalMinutes)
        assertTrue(setup.isFiretruckGame)
    }

    @Test
    fun `bomb pots need a sensible number of minutes`() {
        listOf("", "0", "99999").forEach { minutes ->
            val validation = validForm().copy(bombPotEnabled = true, bombPotMinutes = minutes).validate()

            assertNotNull("\"$minutes\" should be refused", validation.bombPotError)
            assertNull(validation.setup)
        }
    }

    @Test
    fun `a bad interval does not matter while bomb pots are off`() {
        val validation = validForm().copy(bombPotEnabled = false, bombPotMinutes = "").validate()

        assertNull(validation.bombPotError)
        assertNotNull(validation.setup)
    }

    @Test
    fun `random seating is carried into the setup`() {
        assertFalse(validForm().validate().setup!!.isRandomSeating)
        assertTrue(validForm().copy(isRandomSeating = true).validate().setup!!.isRandomSeating)
    }

    @Test
    fun `blinds below the smallest stakes are refused`() {
        val validation = validForm().copy(smallBlind = "0.0005", bigBlind = "0.001").validate()

        assertEquals(
            UiText.plural(R.plurals.error_amount_decimals, 3, UiText.of(R.string.create_small_blind), 3),
            validation.smallBlindError,
        )
        assertEquals(
            UiText.of(R.string.error_amount_below_min, UiText.of(R.string.create_big_blind), UiText.Cash("0.002")),
            validation.bigBlindError,
        )
        assertFalse(validation.isValid)
    }

    @Test
    fun `blinds at the top of the range are accepted and anything past it is refused`() {
        val atTheTop = validForm()
            .copy(smallBlind = "10000", bigBlind = "20000", chipsPerBigBlind = "2")
            .validate()
        assertNull(atTheTop.smallBlindError)
        assertNull(atTheTop.bigBlindError)

        val pastIt = validForm().copy(smallBlind = "10001", bigBlind = "20001").validate()
        assertEquals(
            UiText.of(R.string.error_amount_above_max, UiText.of(R.string.create_small_blind), UiText.Cash("10000.00")),
            pastIt.smallBlindError,
        )
        assertEquals(
            UiText.of(R.string.error_amount_above_max, UiText.of(R.string.create_big_blind), UiText.Cash("20000.00")),
            pastIt.bigBlindError,
        )
    }

    @Test
    fun `random order holds up to nine players and says so past that`() {
        val nine = (1L..9L).associateWith { null as Money? }
        assertNull(validForm().copy(isRandomSeating = true, selection = nine).validate().playersError)

        val ten = (1L..10L).associateWith { null as Money? }
        val validation = validForm().copy(isRandomSeating = true, selection = ten).validate()
        assertEquals(
            UiText.plural(R.plurals.error_random_seating_too_many, 9, 9),
            validation.playersError,
        )
        assertNull(validation.setup)

        // Without random order the same ten can play: the limit is the table view's, not the game's.
        assertNotNull(validForm().copy(isRandomSeating = false, selection = ten).validate().setup)
    }
}
