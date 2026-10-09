package com.bdaniel.loyscan.di

import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.RoomLoyaltyCardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindLoyaltyCardRepository(impl: RoomLoyaltyCardRepository): LoyaltyCardRepository
}
