package com.zango.pokertracker.data.repository

import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.domain.model.GameSummary
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.NewGameSetup
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.PlayerStats
import com.zango.pokertracker.domain.model.SettledPayment
import com.zango.pokertracker.domain.model.Stakes
import kotlinx.coroutines.flow.Flow

sealed interface CreatePlayerResult {
    data class Created(val player: Player) : CreatePlayerResult
    /** A player by that name already exists; the caller can select them instead. */
    data class NameTaken(val existing: Player) : CreatePlayerResult
    data object BlankName : CreatePlayerResult
}

sealed interface RenamePlayerResult {
    data class Renamed(val player: Player) : RenamePlayerResult
    /** Someone else on the roster already answers to that name. */
    data class NameTaken(val existing: Player) : RenamePlayerResult
    data object BlankName : RenamePlayerResult
    data object NotFound : RenamePlayerResult
}

sealed interface DeletePlayerResult {
    data object Deleted : DeletePlayerResult

    /**
     * The player has sat in games, so removing them would rewrite results that have already been
     * settled. Hiding them from the roster is the way out.
     */
    data class HasHistory(val gamesPlayed: Int) : DeletePlayerResult
}

/**
 * The app's single door to stored data. Reads are [Flow]s so the live game screen recomposes as
 * buy-ins and cash-outs land, and writes are suspending and transactional.
 */
interface PokerRepository {

    fun observeRoster(): Flow<List<Player>>

    /**
     * The whole roster, hidden players included, each with everything they have ever played.
     * Sorted by name, and each player's games newest first.
     */
    fun observePlayerStats(): Flow<List<PlayerStats>>

    suspend fun createPlayer(name: String): CreatePlayerResult

    suspend fun renamePlayer(playerId: Long, name: String): RenamePlayerResult

    /** Hides a player from the game setup picker, or brings them back, without touching history. */
    suspend fun setPlayerArchived(playerId: Long, archived: Boolean)

    /**
     * Removes a player from the roster for good. Only possible while they have never played;
     * anyone with history comes back as [DeletePlayerResult.HasHistory] instead.
     */
    suspend fun deletePlayer(playerId: Long): DeletePlayerResult

    fun observeGame(gameId: Long): Flow<GameSnapshot?>

    /** Every game, newest first, with the totals the history list shows. */
    fun observeGameSummaries(): Flow<List<GameSummary>>

    /**
     * Every stake level worth offering when setting a game up: the standard ladder, plus every
     * pair of blinds the host has actually played, in ascending order of size.
     *
     * Games already record their blinds, so a custom stake joins this list by being played once
     * rather than by being saved anywhere separately.
     */
    fun observeStakeOptions(): Flow<List<Stakes>>

    /** The blinds of the most recent game, so a new one can open on the same stakes. */
    suspend fun lastPlayedStakes(): Stakes?

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

    /** The payments of this game's settlement that have been ticked off as handed over. */
    fun observeSettledPayments(gameId: Long): Flow<List<SettledPayment>>

    /**
     * Ticks one settlement payment off, or puts it back.
     *
     * [allSettled] says whether that leaves every payment of the game made. It is passed in
     * rather than worked out here because payments are derived from the results, so only the
     * screen holding the settlement knows the full list; recording the conclusion alongside the
     * mark is what lets the game hub show a settled game without recomputing every settlement
     * the host has ever produced. Both go in one transaction so they cannot disagree.
     */
    suspend fun setPaymentSettled(payment: SettledPayment, settled: Boolean, allSettled: Boolean)

    /** Erases a game and everything recorded against it. Not reversible. */
    suspend fun deleteGame(gameId: Long)
}
