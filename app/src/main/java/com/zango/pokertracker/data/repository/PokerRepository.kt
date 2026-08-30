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

    /**
     * Records chips sold back to the bank part-way through, while the player keeps their seat.
     * They are credited the cash immediately, and the chips leave the table.
     */
    suspend fun returnChips(gamePlayerId: Long, chips: Chips)

    /** Removes a chip return recorded by mistake. */
    suspend fun undoChipReturn(chipReturnId: Long)

    /** Seats a roster player mid-game with their opening buy-in. Returns the new seat id. */
    suspend fun seatPlayer(gameId: Long, playerId: Long, initialBuyIn: Money): Long

    suspend fun cashOut(gamePlayerId: Long, finalChips: Chips)

    suspend fun undoCashOut(gamePlayerId: Long)

    suspend fun setFinalChipCount(gamePlayerId: Long, chips: Chips?)

    /**
     * Marks the game finished and closes out everyone whose stack has been counted.
     *
     * [seatsCountedAsZero] are seats left blank whose stacks the counted totals prove must be
     * empty. They are written as zero inside the same transaction, so a player is never left
     * without a result and dropped out of the settlement.
     */
    suspend fun endGame(gameId: Long, seatsCountedAsZero: List<Long> = emptyList())

    /** Erases a game and everything recorded against it. Not reversible. */
    suspend fun deleteGame(gameId: Long)
}
