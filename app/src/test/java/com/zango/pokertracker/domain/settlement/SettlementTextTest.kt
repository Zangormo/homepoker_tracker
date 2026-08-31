package com.zango.pokertracker.domain.settlement

import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The text that gets pasted into a group chat.
 *
 * It is read by people who were not looking at the app and cannot ask it anything, so every line
 * has to stand on its own: no legend, no abbreviations, and no figure whose meaning depends on a
 * column heading that did not come along.
 */
class SettlementTextTest {

    private fun player(id: Long, name: String) = Player(id, name, createdAt = 0)

    private val anna = player(1, "Anna")
    private val boris = player(2, "Boris")
    private val chris = player(3, "Chris")

    private fun settlement(
        payments: List<Payment> = emptyList(),
        roundingAdjustment: Money = Money.ZERO,
        adjustedPlayer: Player? = null,
        imbalance: Money = Money.ZERO,
        unsettled: List<PlayerNet> = emptyList(),
    ) = Settlement(
        payments = payments,
        nets = emptyList(),
        settledNets = emptyList(),
        roundingUnit = Money(10_000),
        roundingAdjustment = roundingAdjustment,
        adjustedPlayer = adjustedPlayer,
        imbalance = imbalance,
        unsettled = unsettled,
    )

    // -----------------------------------------------------------------------------------------
    // Sentences
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a payment is a whole instruction with no interpretation required`() {
        assertEquals(
            "Anna pays Boris 4.50",
            Payment(from = anna, to = boris, amount = Money(4_500_000)).toSentence(),
        )
    }

    @Test
    fun `sub-cent stakes keep the precision they were played at`() {
        assertEquals(
            "Anna pays Boris 0.005",
            Payment(anna, boris, Money(5_000)).toSentence(),
        )
    }

    // -----------------------------------------------------------------------------------------
    // The whole message
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the message opens with the game it belongs to`() {
        val text = settlement(listOf(Payment(anna, boris, Money(4_500_000))))
            .toShareText("Thursday")

        assertEquals(
            """
            Thursday — settlement

            Anna pays Boris 4.50
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `every payment gets its own line, in the order they were worked out`() {
        val text = settlement(
            listOf(
                Payment(anna, boris, Money(4_500_000)),
                Payment(chris, boris, Money(1_200_000)),
            ),
        ).toShareText("Thursday")

        assertEquals(
            listOf("Thursday — settlement", "", "Anna pays Boris 4.50", "Chris pays Boris 1.20"),
            text.lines(),
        )
    }

    @Test
    fun `a table where nobody won or lost says so instead of listing nothing`() {
        val text = settlement().toShareText("Quiet one")

        assertTrue(text.contains("Everyone broke even. No payments needed."))
        assertFalse(text.contains("pays"))
    }

    @Test
    fun `the message never ends in blank lines`() {
        val text = settlement(listOf(Payment(anna, boris, Money(10_000)))).toShareText("Thursday")

        assertEquals(text.trimEnd(), text)
    }

    // -----------------------------------------------------------------------------------------
    // Caveats
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a rounding nudge names who absorbed it and how much`() {
        val notes = settlement(
            roundingAdjustment = Money(10_000),
            adjustedPlayer = chris,
        ).notes()

        assertEquals(listOf("Rounded to the nearest 0.01. Chris absorbed 0.01."), notes)
    }

    /** The absorbed figure reads as a size, not as a direction, so it never prints a minus. */
    @Test
    fun `an adjustment in the other direction reads the same way`() {
        val notes = settlement(
            roundingAdjustment = Money(-10_000),
            adjustedPlayer = chris,
        ).notes()

        assertEquals(listOf("Rounded to the nearest 0.01. Chris absorbed 0.01."), notes)
    }

    @Test
    fun `chips missing from the table are reported as short of the buy-ins`() {
        val notes = settlement(imbalance = Money(-60_000)).notes()

        assertEquals(
            listOf(
                "Chip counts came out 0.06 short of the buy-ins, so these payments do not " +
                    "fully square everyone up.",
            ),
            notes,
        )
    }

    @Test
    fun `more chips than were bought reads as over instead`() {
        assertTrue(settlement(imbalance = Money(60_000)).notes().single().contains("0.06 over"))
    }

    @Test
    fun `anyone left out of pocket is named, with which way it goes`() {
        val notes = settlement(
            imbalance = Money(-60_000),
            unsettled = listOf(
                PlayerNet(boris, Money(-60_000)),
                PlayerNet(anna, Money(20_000)),
            ),
        ).notes()

        assertTrue(notes.contains("Boris still owes 0.06."))
        assertTrue(notes.contains("Anna is still owed 0.02."))
    }

    @Test
    fun `a clean settlement carries no caveats at all`() {
        assertEquals(emptyList<String>(), settlement(listOf(Payment(anna, boris, Money(10_000)))).notes())
    }

    @Test
    fun `caveats travel with the payments into the shared text`() {
        val text = settlement(
            payments = listOf(Payment(anna, boris, Money(10_000))),
            imbalance = Money(-60_000),
            unsettled = listOf(PlayerNet(boris, Money(-60_000))),
        ).toShareText("Thursday")

        val lines = text.lines()
        assertEquals("Anna pays Boris 0.01", lines[2])
        // A blank line separates the instructions from the small print.
        assertEquals("", lines[3])
        assertTrue(lines[4].startsWith("Chip counts came out 0.06 short"))
        assertEquals("Boris still owes 0.06.", lines.last())
    }
}
