package com.zango.pokertracker.testing

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.time.Clock
import com.zango.pokertracker.data.repository.CreatePlayerResult
import com.zango.pokertracker.data.repository.DeletePlayerResult
import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.data.repository.RenamePlayerResult
import com.zango.pokertracker.domain.model.BuyIn
import com.zango.pokertracker.domain.model.ChipReturn
import com.zango.pokertracker.domain.model.GameSummary
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.GameStatus
import com.zango.pokertracker.domain.model.NewGameSetup
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.PlayerGameResult
import com.zango.pokertracker.domain.model.PlayerStats
import com.zango.pokertracker.domain.model.Seat
import com.zango.pokertracker.domain.model.SettledPayment
import com.zango.pokertracker.domain.model.Stakes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    /** Games each player has played, keyed by player id. */
    val playerGames = MutableStateFlow<Map<Long, List<PlayerGameResult>>>(emptyMap())

    /** Settlement payments ticked off as handed over. */
    val settledPayments = MutableStateFlow<List<SettledPayment>>(emptyList())

    /** Every write the ViewModel made, in order, for assertions. */
    val writes = mutableListOf<String>()

    var seatPlayerFailure: Throwable? = null

    /** Overrides what a new game opens on; otherwise the newest summary is used. */
    var lastPlayedStakes: Stakes? = null
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

    /**
     * Mirrors the real query: the whole roster in name order, each with the games they have
     * played, newest first. Tests seed [playerGames] with whatever history they need.
     */
    override fun observePlayerStats(): Flow<List<PlayerStats>> =
        combine(roster, playerGames) { players, games ->
            players.sortedBy { it.name.lowercase() }.map { player ->
                PlayerStats(
                    player = player,
                    games = games[player.id].orEmpty().sortedByDescending { it.startedAt },
                )
            }
        }

    override suspend fun renamePlayer(playerId: Long, name: String): RenamePlayerResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return RenamePlayerResult.BlankName
        val player = roster.value.firstOrNull { it.id == playerId }
            ?: return RenamePlayerResult.NotFound
        roster.value
            .firstOrNull { it.name.equals(trimmed, ignoreCase = true) && it.id != playerId }
            ?.let { return RenamePlayerResult.NameTaken(it) }
        writes += "renamePlayer($playerId, $trimmed)"
        val renamed = player.copy(name = trimmed)
        roster.update { list -> list.map { if (it.id == playerId) renamed else it } }
        return RenamePlayerResult.Renamed(renamed)
    }

    override suspend fun setPlayerArchived(playerId: Long, archived: Boolean) {
        writes += "setPlayerArchived($playerId, $archived)"
        roster.update { list ->
            list.map { if (it.id == playerId) it.copy(isArchived = archived) else it }
        }
    }

    /** Mirrors the foreign key: anyone who has ever been seated stays put. */
    override suspend fun deletePlayer(playerId: Long): DeletePlayerResult {
        val played = playerGames.value[playerId].orEmpty().size
        if (played > 0) return DeletePlayerResult.HasHistory(played)
        writes += "deletePlayer($playerId)"
        roster.update { list -> list.filterNot { it.id == playerId } }
        return DeletePlayerResult.Deleted
    }

    override fun observeGame(gameId: Long): Flow<GameSnapshot?> = game.asStateFlow()

    override fun observeGameSummaries(): Flow<List<GameSummary>> = summaries.asStateFlow()

    /** Mirrors the real query: the standard ladder plus whatever has been played, by size. */
    override fun observeStakeOptions(): Flow<List<Stakes>> =
        summaries.map { games ->
            val played = games.map { Stakes(it.game.smallBlind, it.game.bigBlind) }
            (Stakes.COMMON + played)
                .distinct()
                .sortedWith(compareBy({ it.bigBlind.micros }, { it.smallBlind.micros }))
        }

    override suspend fun lastPlayedStakes(): Stakes? =
        lastPlayedStakes ?: summaries.value.firstOrNull()
            ?.let { Stakes(it.game.smallBlind, it.game.bigBlind) }

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

    override suspend fun returnChips(gamePlayerId: Long, chips: Chips) {
        writes += "returnChips($gamePlayerId, $chips)"
        updateSeat(gamePlayerId) { seat ->
            seat.copy(chipReturns = seat.chipReturns + ChipReturn(nextId++, chips, clock.now))
        }
    }

    override suspend fun undoChipReturn(chipReturnId: Long) {
        writes += "undoChipReturn($chipReturnId)"
        game.update { snapshot ->
            snapshot?.copy(
                seats = snapshot.seats.map { seat ->
                    seat.copy(chipReturns = seat.chipReturns.filterNot { it.id == chipReturnId })
                },
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

    override fun observeSettledPayments(gameId: Long): Flow<List<SettledPayment>> =
        settledPayments.asStateFlow()

    override suspend fun setPaymentSettled(
        payment: SettledPayment,
        settled: Boolean,
        allSettled: Boolean,
    ) {
        writes += "setPaymentSettled(${payment.fromPlayerId}->${payment.toPlayerId}, " +
            "$settled, all=$allSettled)"
        settledPayments.update { marks ->
            val without = marks.filterNot {
                it.fromPlayerId == payment.fromPlayerId && it.toPlayerId == payment.toPlayerId
            }
            if (settled) without + payment else without
        }
        game.update { snapshot ->
            snapshot?.copy(game = snapshot.game.copy(isFullyPaid = allSettled))
        }
    }

    /** Mirrors the cascade: the game leaves the list and its snapshot goes with it. */
    override suspend fun deleteGame(gameId: Long) {
        writes += "deleteGame($gameId)"
        summaries.update { list -> list.filterNot { it.game.id == gameId } }
        if (game.value?.game?.id == gameId) game.value = null
    }

    /** Mirrors the real repository: only seats with a counted stack are closed out. */
    override suspend fun endGame(gameId: Long, seatsCountedAsZero: List<Long>) {
        writes += "endGame($gameId)"
        seatsCountedAsZero.forEach { seatId ->
            writes += "setFinalChipCount($seatId, 0)"
            updateSeat(seatId) { it.copy(finalChips = Chips.ZERO) }
        }
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
