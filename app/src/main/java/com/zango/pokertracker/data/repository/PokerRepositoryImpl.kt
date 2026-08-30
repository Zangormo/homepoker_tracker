package com.zango.pokertracker.data.repository

import androidx.room.withTransaction
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.time.Clock
import com.zango.pokertracker.data.local.PokerDatabase
import com.zango.pokertracker.data.local.dao.BuyInDao
import com.zango.pokertracker.data.local.dao.ChipReturnDao
import com.zango.pokertracker.data.local.dao.GameDao
import com.zango.pokertracker.data.local.dao.GamePlayerDao
import com.zango.pokertracker.data.local.dao.PlayerDao
import com.zango.pokertracker.data.local.entity.BuyInEntity
import com.zango.pokertracker.data.local.entity.ChipReturnEntity
import com.zango.pokertracker.data.local.entity.GameEntity
import com.zango.pokertracker.data.local.entity.GamePlayerEntity
import com.zango.pokertracker.data.local.entity.PlayerEntity
import com.zango.pokertracker.domain.model.GameSummary
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.GameStatus
import com.zango.pokertracker.domain.model.NewGameSetup
import com.zango.pokertracker.domain.model.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PokerRepositoryImpl @Inject constructor(
    private val database: PokerDatabase,
    private val playerDao: PlayerDao,
    private val gameDao: GameDao,
    private val gamePlayerDao: GamePlayerDao,
    private val buyInDao: BuyInDao,
    private val chipReturnDao: ChipReturnDao,
    private val clock: Clock,
) : PokerRepository {

    override fun observeRoster(): Flow<List<Player>> =
        playerDao.observeActiveRoster()
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun createPlayer(name: String): CreatePlayerResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CreatePlayerResult.BlankName
        // Checked explicitly rather than leaning on the unique index alone, so the caller gets
        // the existing player back and can just select them instead of seeing a failure.
        playerDao.findByName(trimmed)?.let { return CreatePlayerResult.NameTaken(it.toDomain()) }
        val createdAt = clock.nowMillis()
        val id = playerDao.insert(PlayerEntity(name = trimmed, createdAt = createdAt))
        return CreatePlayerResult.Created(
            Player(id = id, name = trimmed, createdAt = createdAt),
        )
    }

    override fun observeGame(gameId: Long): Flow<GameSnapshot?> =
        gameDao.observeWithPlayers(gameId)
            .map { it?.toDomain() }
            .distinctUntilChanged()

    override fun observeGameSummaries(): Flow<List<GameSummary>> =
        gameDao.observeSummaries()
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun createGame(setup: NewGameSetup): Long {
        require(setup.name.isNotBlank()) { "A game needs a name" }
        require(setup.smallBlind.isPositive) { "Small blind must be greater than zero" }
        require(setup.smallBlind < setup.bigBlind) { "Small blind must be below the big blind" }
        require(setup.payoutRounding.isPositive) { "Payout rounding unit must be positive" }
        require(setup.entries.isNotEmpty()) { "A game needs at least one player" }
        require(setup.entries.all { it.buyIn.isPositive }) { "Buy-ins must be greater than zero" }
        require(setup.entries.distinctBy { it.playerId }.size == setup.entries.size) {
            "A player cannot be seated twice in the same game"
        }

        val now = clock.nowMillis()
        return database.withTransaction {
            val gameId = gameDao.insert(
                GameEntity(
                    name = setup.name.trim(),
                    smallBlindMicros = setup.smallBlind.micros,
                    bigBlindMicros = setup.bigBlind.micros,
                    chipValueMicros = setup.chipRate.chipValueMicros,
                    defaultBuyInMicros = setup.defaultBuyIn.micros,
                    payoutRoundingMicros = setup.payoutRounding.micros,
                    startedAt = now,
                ),
            )
            val seatIds = gamePlayerDao.insertAll(
                setup.entries.map {
                    GamePlayerEntity(gameId = gameId, playerId = it.playerId, joinedAt = now)
                },
            )
            buyInDao.insertAll(
                seatIds.mapIndexed { index, seatId ->
                    BuyInEntity(
                        gamePlayerId = seatId,
                        amountMicros = setup.entries[index].buyIn.micros,
                        createdAt = now,
                    )
                },
            )
            gameId
        }
    }

    override suspend fun addBuyIn(gamePlayerId: Long, amount: Money) {
        require(amount.isPositive) { "A buy-in must be greater than zero" }
        buyInDao.insert(
            BuyInEntity(
                gamePlayerId = gamePlayerId,
                amountMicros = amount.micros,
                createdAt = clock.nowMillis(),
            ),
        )
    }

    override suspend fun returnChips(gamePlayerId: Long, chips: Chips) {
        require(chips.isPositive) { "A chip return must be greater than zero" }
        chipReturnDao.insert(
            ChipReturnEntity(
                gamePlayerId = gamePlayerId,
                chips = chips.count,
                createdAt = clock.nowMillis(),
            ),
        )
    }

    override suspend fun undoChipReturn(chipReturnId: Long) {
        chipReturnDao.delete(chipReturnId)
    }

    override suspend fun seatPlayer(gameId: Long, playerId: Long, initialBuyIn: Money): Long {
        require(initialBuyIn.isPositive) { "A buy-in must be greater than zero" }
        val now = clock.nowMillis()
        return database.withTransaction {
            check(!gamePlayerDao.isSeated(gameId, playerId)) {
                "That player is already seated in this game"
            }
            val seatId = gamePlayerDao.insert(
                GamePlayerEntity(gameId = gameId, playerId = playerId, joinedAt = now),
            )
            buyInDao.insert(
                BuyInEntity(
                    gamePlayerId = seatId,
                    amountMicros = initialBuyIn.micros,
                    createdAt = now,
                ),
            )
            seatId
        }
    }

    override suspend fun cashOut(gamePlayerId: Long, finalChips: Chips) {
        require(!finalChips.isNegative) { "A chip count cannot be negative" }
        gamePlayerDao.cashOut(gamePlayerId, clock.nowMillis(), finalChips.count)
    }

    override suspend fun undoCashOut(gamePlayerId: Long) {
        gamePlayerDao.undoCashOut(gamePlayerId)
    }

    override suspend fun setFinalChipCount(gamePlayerId: Long, chips: Chips?) {
        require(chips == null || !chips.isNegative) { "A chip count cannot be negative" }
        gamePlayerDao.setFinalChipCount(gamePlayerId, chips?.count)
    }

    override suspend fun deleteGame(gameId: Long) {
        gameDao.delete(gameId)
    }

    override suspend fun endGame(gameId: Long, seatsCountedAsZero: List<Long>) {
        val now = clock.nowMillis()
        database.withTransaction {
            seatsCountedAsZero.forEach { gamePlayerDao.setFinalChipCount(it, 0) }
            gamePlayerDao.cashOutRemaining(gameId, now)
            gameDao.finish(gameId, GameStatus.FINISHED, now)
        }
    }
}
