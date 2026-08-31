package com.zango.pokertracker.di

import android.content.Context
import androidx.room.Room
import com.zango.pokertracker.core.time.Clock
import com.zango.pokertracker.core.time.SystemClock
import com.zango.pokertracker.data.local.PokerDatabase
import com.zango.pokertracker.data.local.dao.BuyInDao
import com.zango.pokertracker.data.local.dao.ChipReturnDao
import com.zango.pokertracker.data.local.dao.GameDao
import com.zango.pokertracker.data.local.dao.GamePlayerDao
import com.zango.pokertracker.data.local.dao.PlayerDao
import com.zango.pokertracker.data.local.dao.SettlementPaymentDao
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
            .addMigrations(PokerDatabase.MIGRATION_1_2, PokerDatabase.MIGRATION_2_3)
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
    fun provideChipReturnDao(database: PokerDatabase): ChipReturnDao = database.chipReturnDao()

    @Provides
    fun provideSettlementPaymentDao(database: PokerDatabase): SettlementPaymentDao =
        database.settlementPaymentDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()
}
