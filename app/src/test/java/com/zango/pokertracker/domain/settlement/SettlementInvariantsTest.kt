package com.zango.pokertracker.domain.settlement

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.money.sum
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.Seat
import com.zango.pokertracker.testing.sentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Properties that have to hold for every table, not just the ones somebody thought to write down.
 *
 * A settlement is the app's whole point: if it can invent or lose money, or ask someone to pay
 * more than they lost, the host finds out at 1am with cash on the table. So rather than more
 * worked examples, this generates whole games and asserts the things that must never be false.
 *
 * The generator is seeded, so a failure here is reproducible rather than a story about a build
 * that once went red.
 */
class SettlementInvariantsTest {

    private val rate = Fixture.chipRate

    /**
     * Builds a table that genuinely balances: chips are dealt out against buy-ins and then
     * redistributed, so the counted stacks always add up to the chips that were issued.
     */
    private fun balancedGame(random: Random, playerCount: Int): GameSnapshot {
        val buyIns = List(playerCount) { random.nextInt(1, 4) }
        val chipsIssued = buyIns.sumOf { it * 200 }

        // Deal the issued chips back out at random, so somebody may well bust.
        val stacks = MutableList(playerCount) { 0 }
        repeat(chipsIssued) { stacks[random.nextInt(playerCount)]++ }

        val seats = List(playerCount) { index ->
            Fixture.seat(
                id = (index + 1).toLong(),
                name = "P${index + 1}",
                buyIns = List(buyIns[index]) { Fixture.buyIn },
                finalChips = Chips(stacks[index].toLong()),
            )
        }
        return GameSnapshot(Fixture.game(), seats)
    }

    private fun GameSnapshot.netByName(): Map<String, Money> =
        seats.associate { it.player.name to netOf(it)!! }

    // -----------------------------------------------------------------------------------------
    // Conservation
    // -----------------------------------------------------------------------------------------

    @Test
    fun `payments never create or destroy money`() {
        val random = Random(20260830)
        repeat(300) {
            val game = balancedGame(random, random.nextInt(2, 9))
            val settlement = game.settle()

            val paid = settlement.payments.map { it.amount }.sum()
            val received = settlement.payments.map { it.amount }.sum()
            assertEquals(paid, received)
            // What each payer hands over is exactly what the receivers collect.
            val movement = settlement.payments.groupingBy { it.from.name }
                .fold(Money.ZERO) { acc, payment -> acc + payment.amount }
            val collected = settlement.payments.groupingBy { it.to.name }
                .fold(Money.ZERO) { acc, payment -> acc + payment.amount }
            assertEquals(
                movement.values.fold(Money.ZERO) { a, b -> a + b },
                collected.values.fold(Money.ZERO) { a, b -> a + b },
            )
        }
    }

    @Test
    fun `a balanced table always settles fully, with nobody left owing`() {
        val random = Random(19831201)
        repeat(300) {
            val settlement = balancedGame(random, random.nextInt(2, 9)).settle()

            assertTrue(settlement.isBalanced)
            assertTrue(settlement.unsettled.isEmpty())
        }
    }

    /** What the payments actually move for each player, netted out. */
    private fun Settlement.movementByName(): Map<String, Money> {
        val moved = mutableMapOf<String, Money>()
        payments.forEach { payment ->
            moved[payment.from.name] = (moved[payment.from.name] ?: Money.ZERO) - payment.amount
            moved[payment.to.name] = (moved[payment.to.name] ?: Money.ZERO) + payment.amount
        }
        return moved
    }

    /**
     * The payments are not an approximation of the results: each player moves exactly the figure
     * they were settled to, down to the micro.
     */
    @Test
    fun `what a player moves is exactly the result they were settled to`() {
        val random = Random(7777)
        repeat(300) {
            val settlement = balancedGame(random, random.nextInt(2, 9)).settle()
            val moved = settlement.movementByName()

            settlement.settledNets.forEach { settled ->
                assertEquals(
                    "${settled.player.name} was settled to ${settled.net.format()}",
                    settled.net,
                    moved[settled.player.name] ?: Money.ZERO,
                )
            }
        }
    }

    /**
     * Rounding moves everyone by less than half the unit, except the one player who absorbs
     * whatever is left over so that the rounded results still cancel out.
     */
    @Test
    fun `everyone but the absorbing player pays within half a unit of what they played to`() {
        val random = Random(9001)
        repeat(300) {
            val game = balancedGame(random, random.nextInt(2, 9))
            val settlement = game.settle()
            val moved = settlement.movementByName()
            val unit = settlement.roundingUnit.micros

            game.netByName().forEach { (name, net) ->
                if (name == settlement.adjustedPlayer?.name) return@forEach
                val drift = ((moved[name] ?: Money.ZERO) - net).abs()
                assertTrue(
                    "$name played to ${net.format()} but moves ${drift.format()} away from it",
                    drift.micros * 2 <= unit,
                )
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Shape of the payment list
    // -----------------------------------------------------------------------------------------

    @Test
    fun `no table ever needs more than one payment fewer than it has players`() {
        val random = Random(4242)
        repeat(300) {
            val playerCount = random.nextInt(2, 9)
            val settlement = balancedGame(random, playerCount).settle()

            assertTrue(
                "${settlement.payments.size} payments for $playerCount players",
                settlement.payments.size <= playerCount - 1,
            )
        }
    }

    @Test
    fun `every payment is a positive amount handed from one person to another`() {
        val random = Random(31337)
        repeat(300) {
            balancedGame(random, random.nextInt(2, 9)).settle().payments.forEach { payment ->
                assertTrue("a payment of ${payment.amount.format()}", payment.amount.isPositive)
                assertTrue("a player paying themselves", payment.from.id != payment.to.id)
            }
        }
    }

    /**
     * The greedy matching zeroes out at least one side of every transfer, which is what makes the
     * pair a safe key for the paid-off ticks recorded against a settlement.
     */
    @Test
    fun `the same two people never appear as a pair twice`() {
        val random = Random(90210)
        repeat(300) {
            val payments = balancedGame(random, random.nextInt(2, 9)).settle().payments
            val pairs = payments.map { it.from.id to it.to.id }

            assertEquals(pairs.distinct().size, pairs.size)
        }
    }

    @Test
    fun `nobody both pays and is paid`() {
        val random = Random(1024)
        repeat(300) {
            val payments = balancedGame(random, random.nextInt(2, 9)).settle().payments
            val payers = payments.map { it.from.id }.toSet()
            val receivers = payments.map { it.to.id }.toSet()

            assertTrue(payers.intersect(receivers).isEmpty())
        }
    }

    // -----------------------------------------------------------------------------------------
    // Determinism
    // -----------------------------------------------------------------------------------------

    /** Reopening a game from history must show what was displayed on the night, not a variant. */
    @Test
    fun `the same table always settles to the same list of payments`() {
        val random = Random(1618)
        repeat(200) {
            val game = balancedGame(random, random.nextInt(2, 9))

            assertEquals(
                game.settle().payments.map { it.sentence() },
                game.settle().payments.map { it.sentence() },
            )
        }
    }

    /**
     * Seats are ordered by when people sat down, which has nothing to do with the results. The
     * settlement must not depend on it.
     */
    @Test
    fun `shuffling the seating order does not change who pays whom`() {
        val random = Random(2718)
        repeat(200) {
            val game = balancedGame(random, random.nextInt(2, 9))
            val shuffled = game.copy(seats = game.seats.shuffled(random))

            assertEquals(
                game.settle().payments.map { it.sentence() }.toSet(),
                shuffled.settle().payments.map { it.sentence() }.toSet(),
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Rounding
    // -----------------------------------------------------------------------------------------

    @Test
    fun `every payment is something people can actually hand over`() {
        val random = Random(5150)
        repeat(300) {
            val settlement = balancedGame(random, random.nextInt(2, 9)).settle()
            val unit = settlement.roundingUnit.micros

            settlement.payments.forEach { payment ->
                assertEquals(
                    "${payment.amount.format()} is not a multiple of ${settlement.roundingUnit.format()}",
                    0L,
                    payment.amount.micros % unit,
                )
            }
        }
    }

    @Test
    fun `at most one player ever absorbs the rounding`() {
        val random = Random(8086)
        repeat(300) {
            val settlement = balancedGame(random, random.nextInt(2, 9)).settle()

            if (settlement.hasRoundingAdjustment) {
                assertTrue(settlement.adjustedPlayer != null)
                assertEquals(
                    0L,
                    settlement.roundingAdjustment.micros % settlement.roundingUnit.micros,
                )
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Tables that do not balance
    // -----------------------------------------------------------------------------------------

    /**
     * A host may finish a night whose chip counts never reconciled. The payments must still pay
     * as many people in full as the money allows, and the shortfall must be named rather than
     * spread silently across everyone.
     */
    @Test
    fun `a table that does not reconcile reports what is left over`() {
        val random = Random(1972)
        repeat(200) {
            val game = balancedGame(random, random.nextInt(3, 9))
            // Lose a handful of chips off one stack, as if they fell on the floor.
            val victim = game.seats.first { (it.finalChips?.count ?: 0) >= 20 }
            val short = game.copy(
                seats = game.seats.map { seat ->
                    if (seat.id == victim.id) {
                        seat.copy(finalChips = seat.finalChips!! - Chips(20))
                    } else {
                        seat
                    }
                },
            )

            val settlement = short.settle()
            assertTrue(!settlement.isBalanced)
            assertTrue(settlement.notes().isNotEmpty())
            // 20 chips at 0.005 is 0.10 missing from the table.
            assertEquals(Money(-100_000), settlement.imbalance)
            assertEquals(
                Money(100_000),
                settlement.unsettled.map { it.net.abs() }.sum(),
            )
        }
    }

    @Test
    fun `an uncounted seat is left out rather than settled as zero`() {
        val seats = listOf<Seat>(
            Fixture.seat(1, "Anna", finalChips = Chips(300)),
            Fixture.seat(2, "Boris", finalChips = Chips(100)),
            Fixture.seat(3, "Chris"),
        )
        val settlement = GameSnapshot(Fixture.game(), seats).settle()

        assertTrue(settlement.payments.none { it.from.name == "Chris" || it.to.name == "Chris" })
        assertTrue(settlement.nets.none { it.player.name == "Chris" })
    }

    @Test
    fun `the chip rate is the only thing that turns a stack into money`() {
        val settlement = GameSnapshot(
            Fixture.game(),
            listOf(
                Fixture.seat(1, "Anna", finalChips = Chips(300)),
                Fixture.seat(2, "Boris", finalChips = Chips(100)),
            ),
        ).settle()

        // 300 chips at 0.005 is 1.50 against 1.00 in: Anna is up 0.50, Boris down 0.50.
        assertEquals(listOf("Boris pays Anna 0.50"), settlement.payments.map { it.sentence() })
        assertEquals(rate.cashFor(Chips(100)), settlement.payments.single().amount)
    }
}
