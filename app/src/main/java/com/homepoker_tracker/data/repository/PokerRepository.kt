package com.homepoker_tracker.data.repository

import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.domain.model.GameSummary
import com.homepoker_tracker.domain.model.GameSnapshot
import com.homepoker_tracker.domain.model.NewGameSetup
import com.homepoker_tracker.domain.model.Player
import kotlinx.coroutines.flow.Flow

sealed interface CreatePlayerResult {
    data class Created(val player: Player) : CreatePlayerResult
    /** A player by that name already exists; the caller can select them instead. */
    data class NameTaken(val existing: Player) : CreatePlayerResult
    data object BlankName : CreatePlayerResult
}

/**
 * The app's single door to stored data. Reads are [Flow]s so the live game screen recomposes as
 * buy-ins and cash-outs land, and writes are suspending and transactional.
 */
interface PokerRepository {

    fun observeRoster(): Flow<List<Player>>

    fun observeFullRoster(): Flow<List<Player>>

    suspend fun createPlayer(name: String): CreatePlayerResult

    suspend fun setPlayerArchived(playerId: Long, archived: Boolean)

    fun observeGame(gameId: Long): Flow<GameSnapshot?>

    suspend fun loadGame(gameId: Long): GameSnapshot?

    /** Every game, newest first, with the totals the history list shows. */
    fun observeGameSummaries(): Flow<List<GameSummary>>

    /** Creates the game, its seats and one opening buy-in each, all or nothing. */
    suspend fun createGame(setup: NewGameSetup): Long

    suspend fun addBuyIn(gamePlayerId: Long, amount: Money)

    /** Seats a roster player mid-game with their opening buy-in. Returns the new seat id. */
    suspend fun seatPlayer(gameId: Long, playerId: Long, initialBuyIn: Money): Long

    suspend fun cashOut(gamePlayerId: Long, finalChips: Chips)

    suspend fun undoCashOut(gamePlayerId: Long)

    suspend fun setFinalChipCount(gamePlayerId: Long, chips: Chips?)

    suspend fun setPayoutRounding(gameId: Long, unit: Money)

    /** Marks the game finished and closes out everyone whose stack has been counted. */
    suspend fun endGame(gameId: Long)

    suspend fun deleteGame(gameId: Long)
}
