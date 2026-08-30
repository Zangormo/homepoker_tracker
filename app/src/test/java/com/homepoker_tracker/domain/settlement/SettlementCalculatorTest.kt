package com.homepoker_tracker.domain.settlement

import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.core.money.sum
import com.homepoker_tracker.domain.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 0.01, the default practical cash unit. */
private val CENT = Money(10_000)

private fun player(name: String) = Player(id = name.hashCode().toLong(), name = name, createdAt = 0)

private fun net(name: String, micros: Long) = PlayerNet(player(name), Money(micros))

/** "Anna pays Boris 4.50", the exact shape a player reads off the screen. */
private fun Settlement.sentences(): List<String> = payments.map { it.toSentence() }

/**
 * The property that actually matters: after the payments, every player is left holding exactly
 * their settled net. Checked on every scenario rather than eyeballing individual amounts.
 */
private fun Settlement.assertPaymentsSatisfySettledNets() {
    val movement = mutableMapOf<String, Money>()
    payments.forEach { payment ->
        movement[payment.from.name] = (movement[payment.from.name] ?: Money.ZERO) - payment.amount
        movement[payment.to.name] = (movement[payment.to.name] ?: Money.ZERO) + payment.amount
    }
    val outstanding = unsettled.associate { it.player.name to it.net }
    settledNets.forEach { settled ->
        val moved = movement[settled.player.name] ?: Money.ZERO
        val left = outstanding[settled.player.name] ?: Money.ZERO
        assertEquals(
            "${settled.player.name} should end on their settled net",
            settled.net,
            moved + left,
        )
    }
}

private fun Settlement.assertPaymentsArePayable() {
    payments.forEach {
        assertTrue("payment must be positive: $it", it.amount.isPositive)
        assertTrue(
            "payment ${it.amount.format()} must be a multiple of ${roundingUnit.format()}",
            it.amount.micros % roundingUnit.micros == 0L,
        )
        assertTrue("nobody pays themselves", it.from.name != it.to.name)
    }
}

class SettlementCalculatorTest {

    @Test
    fun `an even two player game is a single payment`() {
        val settlement = SettlementCalculator.settle(
            listOf(net("Anna", -4_500_000), net("Boris", 4_500_000)),
            CENT,
        )

        assertEquals(listOf("Anna pays Boris 4.50"), settlement.sentences())
        assertTrue(settlement.isBalanced)
        settlement.assertPaymentsSatisfySettledNets()
        settlement.assertPaymentsArePayable()
    }

    @Test
    fun `a game where one player scoops everything costs n minus one payments`() {
        val settlement = SettlementCalculator.settle(
            listOf(
                net("Anna", -1_000_000),
                net("Boris", -1_000_000),
                net("Chris", -1_000_000),
                net("Dina", 3_000_000),
            ),
            CENT,
        )

        assertEquals(3, settlement.payments.size)
        assertTrue(settlement.payments.all { it.to.name == "Dina" })
        assertEquals(Money(3_000_000), settlement.payments.map { it.amount }.sum())
        settlement.assertPaymentsSatisfySettledNets()
        settlement.assertPaymentsArePayable()
    }

    @Test
    fun `a table where everyone broke even needs no payments at all`() {
        val settlement = SettlementCalculator.settle(
            listOf(net("Anna", 0), net("Boris", 0), net("Chris", 0)),
            CENT,
        )

        assertTrue(settlement.payments.isEmpty())
        assertTrue(settlement.isBalanced)
    }

    @Test
    fun `the largest debtor is matched against the largest creditor first`() {
        val settlement = SettlementCalculator.settle(
            listOf(
                net("Anna", -4_500_000),
                net("Boris", 5_700_000),
                net("Chris", -1_200_000),
            ),
            CENT,
        )

        assertEquals(
            listOf("Anna pays Boris 4.50", "Chris pays Boris 1.20"),
            settlement.sentences(),
        )
        settlement.assertPaymentsSatisfySettledNets()
    }

    @Test
    fun `a debtor larger than any single creditor is split across creditors`() {
        val settlement = SettlementCalculator.settle(
            listOf(
                net("Anna", -10_000_000),
                net("Boris", 6_000_000),
                net("Chris", 4_000_000),
            ),
            CENT,
        )

        assertEquals(
            listOf("Anna pays Boris 6.00", "Anna pays Chris 4.00"),
            settlement.sentences(),
        )
        settlement.assertPaymentsSatisfySettledNets()
    }

    @Test
    fun `players who broke even are left out of the payment list`() {
        val settlement = SettlementCalculator.settle(
            listOf(net("Anna", -2_000_000), net("Boris", 2_000_000), net("Chris", 0)),
            CENT,
        )

        assertEquals(listOf("Anna pays Boris 2.00"), settlement.sentences())
        assertTrue(settlement.payments.none { it.from.name == "Chris" || it.to.name == "Chris" })
    }

    @Test
    fun `an empty table settles to nothing`() {
        val settlement = SettlementCalculator.settle(emptyList(), CENT)
        assertTrue(settlement.payments.isEmpty())
        assertTrue(settlement.isBalanced)
        assertNull(settlement.adjustedPlayer)
    }

    @Test
    fun `winners and losers are reported separately for the results table`() {
        val settlement = SettlementCalculator.settle(
            listOf(net("Anna", -2_000_000), net("Boris", 3_000_000), net("Chris", -1_000_000)),
            CENT,
        )

        assertEquals(listOf("Boris"), settlement.winners.map { it.player.name })
        assertEquals(listOf("Anna", "Chris"), settlement.losers.map { it.player.name })
    }

    @Test
    fun `the same results always produce the same payments`() {
        val nets = listOf(net("Anna", -3_000_000), net("Boris", 3_000_000), net("Chris", 0))
        val first = SettlementCalculator.settle(nets, CENT)
        val second = SettlementCalculator.settle(nets.reversed(), CENT)
        assertEquals(first.sentences(), second.sentences())
    }

    @Test
    fun `equal sized claims tie break by name rather than by input order`() {
        val settlement = SettlementCalculator.settle(
            listOf(net("Chris", 1_000_000), net("Anna", 1_000_000), net("Boris", -2_000_000)),
            CENT,
        )
        assertEquals(
            listOf("Boris pays Anna 1.00", "Boris pays Chris 1.00"),
            settlement.sentences(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non positive rounding unit is rejected`() {
        SettlementCalculator.settle(listOf(net("Anna", 0)), Money.ZERO)
    }

    @Test
    fun `random balanced tables never need more than n minus one payments`() {
        val random = Random(20260830)
        repeat(500) {
            val size = random.nextInt(2, 9)
            val raw = (0 until size - 1).map { random.nextLong(-5_000_000, 5_000_000) }
            val micros = raw + listOf(-raw.sum())
            val nets = micros.mapIndexed { index, value -> net("P$index", value) }

            val settlement = SettlementCalculator.settle(nets, CENT)

            assertTrue(
                "expected at most ${size - 1} payments, got ${settlement.payments.size}",
                settlement.payments.size <= size - 1,
            )
            assertTrue(settlement.isBalanced)
            settlement.assertPaymentsArePayable()
            settlement.assertPaymentsSatisfySettledNets()
        }
    }
}

class SettlementRoundingTest {

    @Test
    fun `sub cent results are rounded to something people can actually hand over`() {
        // Micro stakes: Anna is down 4.503 and Boris is up the same.
        val settlement = SettlementCalculator.settle(
            listOf(net("Anna", -4_503_000), net("Boris", 4_503_000)),
            CENT,
        )

        assertEquals(listOf("Anna pays Boris 4.50"), settlement.sentences())
        settlement.assertPaymentsArePayable()
    }

    @Test
    fun `a leftover cent is absorbed by the player with the largest result`() {
        // Rounding each result to a cent leaves the table a cent over; the biggest result eats it.
        val settlement = SettlementCalculator.settle(
            listOf(net("Anna", 5_000), net("Boris", 5_000), net("Chris", -10_000)),
            CENT,
        )

        assertEquals("Chris", settlement.adjustedPlayer?.name)
        assertEquals(Money(-10_000), settlement.roundingAdjustment)
        assertTrue(settlement.hasRoundingAdjustment)
        assertEquals(
            listOf("Chris pays Anna 0.01", "Chris pays Boris 0.01"),
            settlement.sentences(),
        )
        settlement.assertPaymentsSatisfySettledNets()
        settlement.assertPaymentsArePayable()
    }

    @Test
    fun `results that already sit on the rounding unit are left alone`() {
        val settlement = SettlementCalculator.settle(
            listOf(net("Anna", -4_500_000), net("Boris", 4_500_000)),
            CENT,
        )

        assertEquals(Money.ZERO, settlement.roundingAdjustment)
        assertNull(settlement.adjustedPlayer)
        assertEquals(settlement.nets, settlement.settledNets)
    }

    @Test
    fun `a micro stakes game can settle to the tenth of a cent instead`() {
        val settlement = SettlementCalculator.settle(
            listOf(net("Anna", -4_503_000), net("Boris", 4_503_000)),
            Money(1_000),
        )

        assertEquals(listOf("Anna pays Boris 4.503"), settlement.sentences())
        settlement.assertPaymentsArePayable()
    }

    @Test
    fun `rounding never invents or destroys money across the table`() {
        val random = Random(4242)
        repeat(500) {
            val size = random.nextInt(2, 7)
            val raw = (0 until size - 1).map { random.nextLong(-999_999, 999_999) }
            val nets = (raw + listOf(-raw.sum()))
                .mapIndexed { index, value -> net("P$index", value) }

            val settlement = SettlementCalculator.settle(nets, CENT)

            assertEquals(
                "settled results must still cancel out",
                Money.ZERO,
                settlement.settledNets.map { it.net }.sum(),
            )
            settlement.assertPaymentsSatisfySettledNets()
            settlement.assertPaymentsArePayable()
        }
    }

    @Test
    fun `everyone but the absorbing player lands within half a unit of their real result`() {
        // The player who absorbs the leftover can move further than that by design: the whole
        // discrepancy is concentrated on them so the rest of the table gets clean figures.
        val random = Random(99)
        repeat(200) {
            val size = random.nextInt(2, 7)
            val raw = (0 until size - 1).map { random.nextLong(-999_999, 999_999) }
            val nets = (raw + listOf(-raw.sum()))
                .mapIndexed { index, value -> net("P$index", value) }

            val settlement = SettlementCalculator.settle(nets, CENT)

            settlement.nets.zip(settlement.settledNets).forEach { (exact, settled) ->
                if (exact.player.name == settlement.adjustedPlayer?.name) return@forEach
                val drift = (settled.net - exact.net).abs()
                assertTrue(
                    "${exact.player.name} drifted ${drift.format()}",
                    drift.micros * 2 <= CENT.micros,
                )
            }
        }
    }

    @Test
    fun `the absorbing player is always the one with the largest real result`() {
        val random = Random(7)
        repeat(200) {
            val size = random.nextInt(2, 7)
            val raw = (0 until size - 1).map { random.nextLong(-999_999, 999_999) }
            val nets = (raw + listOf(-raw.sum()))
                .mapIndexed { index, value -> net("P$index", value) }

            val settlement = SettlementCalculator.settle(nets, CENT)
            val absorbing = settlement.adjustedPlayer ?: return@repeat
            val largest = nets.maxOf { it.net.abs() }

            assertEquals(largest, nets.single { it.player == absorbing }.net.abs())
        }
    }
}

class SettlementMismatchTest {

    /** Chips 0.06 short of the buy-ins: the losers owe more than the winners are due. */
    private val chipsMissing = listOf(
        net("Anna", -1_000_000),
        net("Boris", 500_000),
        net("Chris", 440_000),
    )

    /** More chips counted than were paid for: the winners are due more than the losers owe. */
    private val chipsSurplus = listOf(
        net("Anna", -1_000_000),
        net("Boris", 500_000),
        net("Chris", 560_000),
    )

    @Test
    fun `a mismatch is reported rather than quietly balanced away`() {
        val settlement = SettlementCalculator.settle(chipsMissing, CENT)

        assertEquals(Money(-60_000), settlement.imbalance)
        assertFalse(settlement.isBalanced)
        assertTrue(settlement.hasUnsettledRemainder)
    }

    @Test
    fun `missing chips still pay every winner in full and leave a loser owing`() {
        val settlement = SettlementCalculator.settle(chipsMissing, CENT)

        assertEquals(
            listOf("Anna pays Boris 0.50", "Anna pays Chris 0.44"),
            settlement.sentences(),
        )
        assertEquals(Money(940_000), settlement.payments.map { it.amount }.sum())
        assertEquals(listOf("Anna"), settlement.unsettled.map { it.player.name })
        assertEquals(Money(-60_000), settlement.unsettled.single().net)
        settlement.assertPaymentsSatisfySettledNets()
        settlement.assertPaymentsArePayable()
    }

    @Test
    fun `surplus chips collect every loser in full and leave a winner short`() {
        val settlement = SettlementCalculator.settle(chipsSurplus, CENT)

        assertEquals(Money(60_000), settlement.imbalance)
        assertEquals(Money(1_000_000), settlement.payments.map { it.amount }.sum())
        assertEquals(listOf("Boris"), settlement.unsettled.map { it.player.name })
        assertEquals(Money(60_000), settlement.unsettled.single().net)
        settlement.assertPaymentsSatisfySettledNets()
        settlement.assertPaymentsArePayable()
    }

    @Test
    fun `an unsettled remainder is spelled out in the shared text`() {
        val text = SettlementCalculator.settle(chipsMissing, CENT).toShareText("Thursday")

        assertTrue(text, text.contains("Chip counts came out 0.06 short of the buy-ins"))
        assertTrue(text, text.contains("Anna still owes 0.06."))
    }
}
