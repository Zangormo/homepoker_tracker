package com.zango.pokertracker.testing

import com.zango.pokertracker.data.local.FiretruckStore
import com.zango.pokertracker.ui.livegame.FiretruckStreak

/** Keeps streaks in a map. Shared between view models to stand in for a restarted app. */
class FakeFiretruckStore : FiretruckStore {
    val streaks = mutableMapOf<Long, FiretruckStreak>()

    override fun load(gameId: Long): FiretruckStreak = streaks[gameId] ?: FiretruckStreak()

    override fun save(gameId: Long, streak: FiretruckStreak) {
        streaks[gameId] = streak
    }
}
