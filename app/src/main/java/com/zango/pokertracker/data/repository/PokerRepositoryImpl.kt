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
import com.zango.pokertracker.data.local.dao.SettlementPaymentDao
import com.zango.pokertracker.data.local.entity.BuyInEntity
import com.zango.pokertracker.data.local.entity.ChipReturnEntity
import com.zango.pokertracker.data.local.entity.GameEntity
import com.zango.pokertracker.data.local.entity.GamePlayerEntity
import com.zango.pokertracker.data.local.entity.PlayerEntity
import com.zango.pokertracker.data.local.entity.SettlementPaymentEntity
import com.zango.pokertracker.domain.model.GameSummary
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.GameStatus
import com.zango.pokertracker.domain.model.NewGameSetup
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.PlayerStats
import com.zango.pokertracker.domain.model.SettledPayment
import com.zango.pokertracker.domain.model.Stakes
import com.zango.pokertracker.domain.settlement.settle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val settlementPaymentDao: SettlementPaymentDao,
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

    /**
     * The roster and every seat ever taken, joined in memory rather than in one wide SQL join.
     * Room emits both flows again whenever any of the underlying tables change, so a rename or a
     * buy-in landing mid-game is reflected without the screen asking for it.
     */
    override fun observePlayerStats(): Flow<List<PlayerStats>> =
        combine(
            playerDao.observeAllPlayers(),
            playerDao.observePlayerGameResults(),
        ) { players, results ->
            val byPlayer = results.groupBy { it.playerId }
            players.map { player ->
                PlayerStats(
                    player = player.toDomain(),
                    games = byPlayer[player.id].orEmpty().map { it.toDomain() },
                )
            }
        }.distinctUntilChanged()

    override suspend fun renamePlayer(playerId: Long, name: String): RenamePlayerResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return RenamePlayerResult.BlankName
        return database.withTransaction {
            val current = playerDao.findById(playerId)
                ?: return@withTransaction RenamePlayerResult.NotFound
            // Matching the player's own row is not a clash: it is how a correction of case or
            // spacing arrives, and refusing it would make "bob" impossible to fix to "Bob".
            playerDao.findByName(trimmed)
                ?.takeIf { it.id != playerId }
                ?.let { return@withTransaction RenamePlayerResult.NameTaken(it.toDomain()) }
            playerDao.rename(playerId, trimmed)
            RenamePlayerResult.Renamed(current.toDomain().copy(name = trimmed))
        }
    }

    override suspend fun setPlayerArchived(playerId: Long, archived: Boolean) {
        playerDao.setArchived(playerId, archived)
    }

    /**
     * Checked before deleting rather than catching the foreign key failure afterwards, so the
     * caller is told what stands in the way instead of being handed a constraint error.
     */
    override suspend fun deletePlayer(playerId: Long): DeletePlayerResult =
        database.withTransaction {
            val played = playerDao.gamesPlayed(playerId)
            if (played > 0) {
                DeletePlayerResult.HasHistory(played)
            } else {
                playerDao.delete(playerId)
                DeletePlayerResult.Deleted
            }
        }

    override fun observeGame(gameId: Long): Flow<GameSnapshot?> =
        gameDao.observeWithPlayers(gameId)
            .map { it?.toDomain() }
            .distinctUntilChanged()

    override fun observeGameSummaries(): Flow<List<GameSummary>> =
        gameDao.observeSummaries()
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeStakeOptions(): Flow<List<Stakes>> =
        gameDao.observePlayedStakes()
            .map { rows ->
                val played = rows.map { Stakes(Money(it.smallBlindMicros), Money(it.bigBlindMicros)) }
                // Sorted by size rather than by how recently they were used, so the list a host
                // scans does not reorder itself between one game and the next. What they played
                // last is already waiting in the fields.
                (Stakes.COMMON + played)
                    .distinct()
                    .sortedWith(compareBy({ it.bigBlind.micros }, { it.smallBlind.micros }))
            }
            .distinctUntilChanged()

    override suspend fun lastPlayedStakes(): Stakes? =
        gameDao.lastPlayedStakes()
            ?.let { Stakes(Money(it.smallBlindMicros), Money(it.bigBlindMicros)) }

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

    override fun observeSettledPayments(gameId: Long): Flow<List<SettledPayment>> =
        settlementPaymentDao.observeForGame(gameId)
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun setPaymentSettled(
        payment: SettledPayment,
        settled: Boolean,
        allSettled: Boolean,
    ) {
        database.withTransaction {
            if (settled) {
                settlementPaymentDao.insert(
                    SettlementPaymentEntity(
                        gameId = payment.gameId,
                        fromPlayerId = payment.fromPlayerId,
                        toPlayerId = payment.toPlayerId,
                        amountMicros = payment.amount.micros,
                        markedAt = clock.nowMillis(),
                    ),
                )
            } else {
                settlementPaymentDao.delete(
                    payment.gameId,
                    payment.fromPlayerId,
                    payment.toPlayerId,
                )
            }
            gameDao.setFullyPaid(payment.gameId, allSettled)
        }
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
            // A table where everyone came out even is square the moment it ends: its settlement
            // calls for no payments, so there is nothing for the host to tick off and nothing
            // else would ever record the game as paid up.
            val settlement = gameDao.loadWithPlayers(gameId)?.toDomain()?.settle()
            if (settlement != null && settlement.payments.isEmpty()) {
                gameDao.setFullyPaid(gameId, true)
            }
        }
    }
}
