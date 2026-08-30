package com.zango.pokertracker.data.repository

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.GameSummary
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.NewGameSetup
import com.zango.pokertracker.domain.model.Player
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

    suspend fun createPlayer(name: String): CreatePlayerResult

    fun observeGame(gameId: Long): Flow<GameSnapshot?>

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

    /** Marks the game finished and closes out everyone whose stack has been counted. */
    suspend fun endGame(gameId: Long)
}
