package com.zango.pokertracker.domain.settlement

import com.zango.pokertracker.R
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.domain.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The message that gets pasted into a group chat.
 *
 * It is read by people who were not looking at the app and cannot ask it anything, so every line
 * has to stand on its own. The lines are named rather than written, because the domain has no way
 * to know what language the phone is in — so what is asserted here is which line applies and what
 * goes in it, and the screen is what turns that into words.
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

    private val header = UiText.of(R.string.settlement_share_subject, "Thursday")
    private val blank = UiText.Raw("")

    // -----------------------------------------------------------------------------------------
    // One instruction
    // -----------------------------------------------------------------------------------------

    /**
     * The names and the amount travel as arguments rather than being glued into a sentence here,
     * which is what lets a language put the amount before the names, or the payer last.
     */
    @Test
    fun `a payment names both people and the amount, as separate arguments`() {
        assertEquals(
            UiText.of(R.string.settlement_pays, "Anna", "Boris", "4.50"),
            Payment(from = anna, to = boris, amount = Money(4_500_000)).toSentence(),
        )
    }

    @Test
    fun `sub-cent stakes keep the precision they were played at`() {
        assertEquals(
            UiText.of(R.string.settlement_pays, "Anna", "Boris", "0.005"),
            Payment(anna, boris, Money(5_000)).toSentence(),
        )
    }

    // -----------------------------------------------------------------------------------------
    // The whole message
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the message opens with the game it belongs to, then a blank line`() {
        val lines = settlement(listOf(Payment(anna, boris, Money(4_500_000))))
            .shareLines("Thursday")

        assertEquals(
            listOf(
                header,
                blank,
                UiText.of(R.string.settlement_pays, "Anna", "Boris", "4.50"),
            ),
            lines,
        )
    }

    @Test
    fun `every payment gets its own line, in the order they were worked out`() {
        val lines = settlement(
            listOf(
                Payment(anna, boris, Money(4_500_000)),
                Payment(chris, boris, Money(1_200_000)),
            ),
        ).shareLines("Thursday")

        assertEquals(
            listOf(
                header,
                blank,
                UiText.of(R.string.settlement_pays, "Anna", "Boris", "4.50"),
                UiText.of(R.string.settlement_pays, "Chris", "Boris", "1.20"),
            ),
            lines,
        )
    }

    @Test
    fun `a table where nobody won or lost says so instead of listing nothing`() {
        val lines = settlement().shareLines("Quiet one")

        assertEquals(
            listOf(
                UiText.of(R.string.settlement_share_subject, "Quiet one"),
                blank,
                UiText.of(R.string.settlement_everyone_even),
            ),
            lines,
        )
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

        assertEquals(listOf(UiText.of(R.string.note_rounded, "0.01", "Chris", "0.01")), notes)
    }

    /** The absorbed figure reads as a size, not a direction, so it never carries a minus. */
    @Test
    fun `an adjustment in the other direction reads the same way`() {
        val notes = settlement(
            roundingAdjustment = Money(-10_000),
            adjustedPlayer = chris,
        ).notes()

        assertEquals(listOf(UiText.of(R.string.note_rounded, "0.01", "Chris", "0.01")), notes)
    }

    /**
     * "short of" and "over" are a separate resource rather than two whole sentences, because the
     * rest of the sentence is identical and a translator should not have to keep them in step.
     */
    @Test
    fun `chips missing from the table are reported as short of the buy-ins`() {
        assertEquals(
            listOf(
                UiText.of(
                    R.string.note_imbalance,
                    "0.06",
                    UiText.of(R.string.note_imbalance_short),
                ),
            ),
            settlement(imbalance = Money(-60_000)).notes(),
        )
    }

    @Test
    fun `more chips than were bought reads as over instead`() {
        assertEquals(
            listOf(
                UiText.of(
                    R.string.note_imbalance,
                    "0.06",
                    UiText.of(R.string.note_imbalance_over),
                ),
            ),
            settlement(imbalance = Money(60_000)).notes(),
        )
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

        assertTrue(
            notes.toString(),
            notes.contains(UiText.of(R.string.note_still_owes, "Boris", "0.06")),
        )
        assertTrue(
            notes.toString(),
            notes.contains(UiText.of(R.string.note_still_owed, "Anna", "0.02")),
        )
    }

    @Test
    fun `a clean settlement carries no caveats at all`() {
        assertEquals(
            emptyList<UiText>(),
            settlement(listOf(Payment(anna, boris, Money(10_000)))).notes(),
        )
    }

    @Test
    fun `caveats travel with the payments, behind a blank line`() {
        val lines = settlement(
            payments = listOf(Payment(anna, boris, Money(10_000))),
            imbalance = Money(-60_000),
            unsettled = listOf(PlayerNet(boris, Money(-60_000))),
        ).shareLines("Thursday")

        assertEquals(
            listOf(
                header,
                blank,
                UiText.of(R.string.settlement_pays, "Anna", "Boris", "0.01"),
                blank,
                UiText.of(
                    R.string.note_imbalance,
                    "0.06",
                    UiText.of(R.string.note_imbalance_short),
                ),
                UiText.of(R.string.note_still_owes, "Boris", "0.06"),
            ),
            lines,
        )
    }
}
