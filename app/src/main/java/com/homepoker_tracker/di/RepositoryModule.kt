package com.homepoker_tracker.di

import com.homepoker_tracker.data.repository.PokerRepository
import com.homepoker_tracker.data.repository.PokerRepositoryImpl
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
