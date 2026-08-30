package com.zango.pokertracker.testing

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.time.Clock
import com.zango.pokertracker.data.repository.CreatePlayerResult
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.domain.model.BuyIn
import com.zango.pokertracker.domain.model.GameSummary
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.GameStatus
import com.zango.pokertracker.domain.model.NewGameSetup
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.Seat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A clock the test drives by hand, so elapsed readouts are deterministic. */
class TestClock(var now: Long = 0L) : Clock {
    override fun nowMillis(): Long = now
}

/**
 * An in-memory stand-in for the real repository.
 *
 * It keeps a live [GameSnapshot] and mutates it the way Room would, so a ViewModel under test
 * sees the same reactive behaviour it gets in the app without needing a device.
 */
class FakePokerRepository(private val clock: TestClock = TestClock()) : PokerRepository {

    val roster = MutableStateFlow<List<Player>>(emptyList())
    val game = MutableStateFlow<GameSnapshot?>(null)
    val summaries = MutableStateFlow<List<GameSummary>>(emptyList())

    /** Every write the ViewModel made, in order, for assertions. */
    val writes = mutableListOf<String>()

    var seatPlayerFailure: Throwable? = null
    private var nextId = 1_000L

    override fun observeRoster(): Flow<List<Player>> = roster.asStateFlow()

    override suspend fun createPlayer(name: String): CreatePlayerResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CreatePlayerResult.BlankName
        roster.value.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?.let { return CreatePlayerResult.NameTaken(it) }
        val player = Player(id = nextId++, name = trimmed, createdAt = clock.now)
        roster.update { it + player }
        writes += "createPlayer(${player.name})"
        return CreatePlayerResult.Created(player)
    }

    override fun observeGame(gameId: Long): Flow<GameSnapshot?> = game.asStateFlow()

    override fun observeGameSummaries(): Flow<List<GameSummary>> = summaries.asStateFlow()

    override suspend fun createGame(setup: NewGameSetup): Long {
        writes += "createGame(${setup.name})"
        return nextId++
    }

    override suspend fun addBuyIn(gamePlayerId: Long, amount: Money) {
        writes += "addBuyIn($gamePlayerId, ${amount.format()})"
        updateSeat(gamePlayerId) { seat ->
            seat.copy(
                buyIns = seat.buyIns + BuyIn(nextId++, amount, clock.now),
            )
        }
    }

    override suspend fun seatPlayer(gameId: Long, playerId: Long, initialBuyIn: Money): Long {
        seatPlayerFailure?.let { throw it }
        writes += "seatPlayer($playerId, ${initialBuyIn.format()})"
        val player = roster.value.first { it.id == playerId }
        val seatId = nextId++
        game.update { snapshot ->
            snapshot?.copy(
                seats = snapshot.seats + Seat(
                    id = seatId,
                    player = player,
                    joinedAt = clock.now,
                    cashedOutAt = null,
                    finalChips = null,
                    buyIns = listOf(BuyIn(nextId++, initialBuyIn, clock.now)),
                ),
            )
        }
        return seatId
    }

    override suspend fun cashOut(gamePlayerId: Long, finalChips: Chips) {
        writes += "cashOut($gamePlayerId, $finalChips)"
        updateSeat(gamePlayerId) { it.copy(finalChips = finalChips, cashedOutAt = clock.now) }
    }

    override suspend fun undoCashOut(gamePlayerId: Long) {
        writes += "undoCashOut($gamePlayerId)"
        updateSeat(gamePlayerId) { it.copy(finalChips = null, cashedOutAt = null) }
    }

    override suspend fun setFinalChipCount(gamePlayerId: Long, chips: Chips?) {
        writes += "setFinalChipCount($gamePlayerId, $chips)"
        updateSeat(gamePlayerId) { it.copy(finalChips = chips) }
    }

    /** Mirrors the real repository: only seats with a counted stack are closed out. */
    override suspend fun endGame(gameId: Long) {
        writes += "endGame($gameId)"
        game.update { snapshot ->
            snapshot?.copy(
                game = snapshot.game.copy(status = GameStatus.FINISHED, endedAt = clock.now),
                seats = snapshot.seats.map { seat ->
                    if (seat.cashedOutAt == null && seat.finalChips != null) {
                        seat.copy(cashedOutAt = clock.now)
                    } else {
                        seat
                    }
                },
            )
        }
    }

    private fun updateSeat(seatId: Long, transform: (Seat) -> Seat) {
        game.update { snapshot ->
            snapshot?.copy(
                seats = snapshot.seats.map { if (it.id == seatId) transform(it) else it },
            )
        }
    }
}
