package com.zango.pokertracker.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zango.pokertracker.ui.livegame.FiretruckStreak
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where each game's firetruck dots are kept, so they are still there after the app is swiped away
 * or killed mid-game. Nothing else depends on them, so they live beside the database rather than
 * in it: a lost streak costs three taps, not a result.
 */
interface FiretruckStore {
    fun load(gameId: Long): FiretruckStreak

    fun save(gameId: Long, streak: FiretruckStreak)
}

@Singleton
class SharedPreferencesFiretruckStore @Inject constructor(
    @ApplicationContext context: Context,
) : FiretruckStore {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun load(gameId: Long): FiretruckStreak {
        val seatId = preferences.getLong(seatKey(gameId), NO_SEAT)
        if (seatId == NO_SEAT) return FiretruckStreak()
        return FiretruckStreak(seatId = seatId, wins = preferences.getInt(winsKey(gameId), 0))
    }

    override fun save(gameId: Long, streak: FiretruckStreak) {
        preferences.edit {
            val seatId = streak.seatId
            if (seatId == null || streak.wins == 0) {
                remove(seatKey(gameId))
                remove(winsKey(gameId))
            } else {
                putLong(seatKey(gameId), seatId)
                putInt(winsKey(gameId), streak.wins)
            }
        }
    }

    private fun seatKey(gameId: Long) = "seat_$gameId"

    private fun winsKey(gameId: Long) = "wins_$gameId"

    private companion object {
        const val PREFERENCES = "firetruck"
        const val NO_SEAT = -1L
    }
}
