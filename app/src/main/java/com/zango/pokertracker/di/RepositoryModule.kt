package com.zango.pokertracker.di

import com.zango.pokertracker.data.repository.PokerRepository
import com.zango.pokertracker.data.repository.PokerRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPokerRepository(impl: PokerRepositoryImpl): PokerRepository
}
