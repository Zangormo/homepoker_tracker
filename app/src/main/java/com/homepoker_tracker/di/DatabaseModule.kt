package com.homepoker_tracker.di

import android.content.Context
import androidx.room.Room
import com.homepoker_tracker.core.time.Clock
import com.homepoker_tracker.core.time.SystemClock
import com.homepoker_tracker.data.local.PokerDatabase
import com.homepoker_tracker.data.local.dao.BuyInDao
import com.homepoker_tracker.data.local.dao.GameDao
import com.homepoker_tracker.data.local.dao.GamePlayerDao
import com.homepoker_tracker.data.local.dao.PlayerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePokerDatabase(@ApplicationContext context: Context): PokerDatabase =
        Room.databaseBuilder(context, PokerDatabase::class.java, PokerDatabase.NAME)
            // No fallbackToDestructiveMigration: a host's game history is not disposable, so a
            // future schema change must ship a real migration rather than wiping the database.
            .build()

    @Provides
    fun providePlayerDao(database: PokerDatabase): PlayerDao = database.playerDao()

    @Provides
    fun provideGameDao(database: PokerDatabase): GameDao = database.gameDao()

    @Provides
    fun provideGamePlayerDao(database: PokerDatabase): GamePlayerDao = database.gamePlayerDao()

    @Provides
    fun provideBuyInDao(database: PokerDatabase): BuyInDao = database.buyInDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()
}
