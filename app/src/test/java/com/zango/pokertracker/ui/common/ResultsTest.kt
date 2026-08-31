package com.zango.pokertracker.ui.common

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.ChipReturn
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The results table shared by the end-game and settlement screens.
 *
 * Fixtures use the spec's table throughout: 0.005 a chip, so a 1.00 buy-in is 200 chips.
 */
class ResultsTest {

    private fun snapshot(vararg seats: com.zango.pokertracker.domain.model.Seat) =
        GameSnapshot(Fixture.game(), seats.toList())

    @Test
    fun `a row is one player's whole night, in the order they sat down`() {
        val rows = snapshot(
            Fixture.seat(1, "Anna", finalChips = Chips(300)),
            Fixture.seat(2, "Boris", finalChips = Chips(100)),
        ).toResultRows()

        assertEquals(listOf("Anna", "Boris"), rows.map { it.name })
        assertEquals(listOf(1L, 2L), rows.map { it.seatId })
    }

    @Test
    fun `cash out is the chips taken off the table at the game rate`() {
        val row = snapshot(Fixture.seat(1, "Anna", finalChips = Chips(300))).toResultRows().single()

        assertEquals(Money(1_000_000), row.totalBuyIn)
        assertEquals(Chips(300), row.chipsOut)
        assertEquals(Money(1_500_000), row.cashOut)
        assertEquals(Money(500_000), row.net)
    }

    /**
     * The chips column is what makes the arithmetic checkable at the table: cash out must always
     * equal that figure times the chip value, however many times the player went back to the bank.
     */
    @Test
    fun `chips sold back mid-game are part of what the player took off the table`() {
        val seat = Fixture.seat(
            1,
            "Anna",
            buyIns = listOf(Fixture.buyIn, Fixture.buyIn),
            finalChips = Chips(150),
        ).copy(chipReturns = listOf(ChipReturn(id = 10, chips = Chips(200), createdAt = 5_000)))

        val row = snapshot(seat).toResultRows().single()

        assertEquals(Money(2_000_000), row.totalBuyIn)
        assertEquals(Chips(350), row.chipsOut)
        assertEquals(Money(1_750_000), row.cashOut)
        assertEquals(Money(-250_000), row.net)
    }

    /**
     * A stack nobody has counted is not zero. The table shows a gap so the host looks for the
     * count rather than reading a loss that was never played.
     */
    @Test
    fun `an uncounted seat leaves its numeric cells empty`() {
        val row = snapshot(Fixture.seat(1, "Anna")).toResultRows().single()

        assertEquals(Money(1_000_000), row.totalBuyIn)
        assertNull(row.chipsOut)
        assertNull(row.cashOut)
        assertNull(row.net)
    }

    @Test
    fun `a busted player reads as zero out rather than as uncounted`() {
        val row = snapshot(Fixture.seat(1, "Anna", finalChips = Chips.ZERO)).toResultRows().single()

        assertEquals(Chips.ZERO, row.chipsOut)
        assertEquals(Money.ZERO, row.cashOut)
        assertEquals(Money(-1_000_000), row.net)
    }

    @Test
    fun `an empty game produces no rows rather than a blank one`() {
        assertEquals(emptyList<ResultRow>(), snapshot().toResultRows())
    }

    /** Every counted row must add up, or the settlement built on top of it cannot. */
    @Test
    fun `net is always what came off the table minus what went on it`() {
        val rows = snapshot(
            Fixture.seat(1, "Anna", buyIns = listOf(Fixture.buyIn, Fixture.buyIn), finalChips = Chips(100)),
            Fixture.seat(2, "Boris", finalChips = Chips(440)),
            Fixture.seat(3, "Chris", finalChips = Chips(260)),
        ).toResultRows()

        rows.forEach { row ->
            assertEquals(row.cashOut!! - row.totalBuyIn, row.net)
        }
        // The table is square: nothing was created or destroyed.
        assertEquals(Money.ZERO, rows.fold(Money.ZERO) { acc, row -> acc + row.net!! })
    }
}
