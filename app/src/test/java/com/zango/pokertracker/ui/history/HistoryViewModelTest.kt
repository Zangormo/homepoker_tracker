package com.zango.pokertracker.ui.history

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.time.formatGameDate
import com.zango.pokertracker.domain.model.Fixture
import com.zango.pokertracker.domain.model.GameStatus
import com.zango.pokertracker.domain.model.GameSummary
import com.zango.pokertracker.testing.FakePokerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/** 30 Aug 2026, 20:15 UTC. */
private const val EVENING = 1_788_120_900_000L
private const val HOUR = 60 * 60_000L

class GameDateFormatTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `a game date reads as a day and a kick-off time`() {
        assertEquals("30 Aug 2026 · 20:15", formatGameDate(EVENING, utc, Locale.UK))
    }

    @Test
    fun `the hour is on a 24 hour clock regardless of locale`() {
        // A 20:15 game must never read as 8:15 with no marker of which 8 it is.
        assertEquals("30 Aug 2026 · 20:15", formatGameDate(EVENING, utc, Locale.US))
    }

    @Test
    fun `the date follows the zone it is asked for`() {
        assertEquals(
            "30 Aug 2026 · 23:15",
            formatGameDate(EVENING, ZoneId.of("Europe/Helsinki"), Locale.UK),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val repository = FakePokerRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun summary(
        id: Long,
        name: String,
        status: GameStatus,
        startedAt: Long = EVENING,
        endedAt: Long? = null,
        players: Int = 4,
        buyIns: Int = 5,
        total: Money = Money(5_000_000),
    ) = GameSummary(
        game = Fixture.game().copy(
            id = id,
            name = name,
            status = status,
            startedAt = startedAt,
            endedAt = endedAt,
        ),
        playerCount = players,
        buyInCount = buyIns,
        totalOnTable = total,
    )

    private suspend fun state() = HistoryViewModel(repository).uiState.first { !it.isLoading }

    @Test
    fun `an empty history says so rather than showing a blank list`() = runTest {
        val state = state()
        assertTrue(state.isEmpty)
        assertTrue(state.inProgress.isEmpty())
        assertTrue(state.finished.isEmpty())
    }

    @Test
    fun `games still running are kept separate from finished ones`() = runTest {
        repository.summaries.value = listOf(
            summary(1, "Tonight", GameStatus.IN_PROGRESS),
            summary(2, "Last week", GameStatus.FINISHED, endedAt = EVENING + 3 * HOUR),
        )

        val state = state()
        assertEquals(listOf("Tonight"), state.inProgress.map { it.name })
        assertEquals(listOf("Last week"), state.finished.map { it.name })
        assertFalse(state.isEmpty)
    }

    @Test
    fun `a finished game shows how long it ran`() = runTest {
        repository.summaries.value = listOf(
            summary(
                2,
                "Last week",
                GameStatus.FINISHED,
                endedAt = EVENING + 3 * HOUR + 12 * 60_000L,
            ),
        )

        assertEquals("3h 12m", state().finished.single().durationLabel)
    }

    @Test
    fun `a running game has no duration yet`() = runTest {
        repository.summaries.value = listOf(summary(1, "Tonight", GameStatus.IN_PROGRESS))
        assertNull(state().inProgress.single().durationLabel)
    }

    @Test
    fun `each row carries the totals the host wants to scan`() = runTest {
        repository.summaries.value = listOf(
            summary(1, "Tonight", GameStatus.IN_PROGRESS, players = 6, buyIns = 9),
        )

        val row = state().inProgress.single()
        assertEquals(Money(5_000_000), row.totalOnTable)
        assertEquals(Chips(1_000), row.chipsOnTable)
        assertEquals("0.005 / 0.01", row.stakes)
        assertEquals(6, row.playerCount)
        assertEquals(9, row.buyInCount)
        assertTrue(row.isInProgress)
    }

    @Test
    fun `the repository order is preserved, so newest stays first`() = runTest {
        repository.summaries.value = listOf(
            summary(3, "Newest", GameStatus.FINISHED, startedAt = EVENING + 2 * HOUR),
            summary(2, "Middle", GameStatus.FINISHED, startedAt = EVENING + HOUR),
            summary(1, "Oldest", GameStatus.FINISHED, startedAt = EVENING),
        )

        assertEquals(listOf("Newest", "Middle", "Oldest"), state().finished.map { it.name })
    }

    @Test
    fun `a game whose buy-ins are not whole chips reports no chip total`() = runTest {
        repository.summaries.value = listOf(
            summary(1, "Odd", GameStatus.IN_PROGRESS, total = Money(5_000_001)),
        )

        assertNull(state().inProgress.single().chipsOnTable)
    }
}
