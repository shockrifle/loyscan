package com.bdaniel.loyscan.di

import com.bdaniel.loyscan.data.repository.DataStoreUserPreferencesRepository
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.RoomLoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.RoomStoreRepository
import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.data.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindLoyaltyCardRepository(impl: RoomLoyaltyCardRepository): LoyaltyCardRepository

    @Binds
    abstract fun bindStoreRepository(impl: RoomStoreRepository): StoreRepository

    @Binds
    abstract fun bindUserPreferencesRepository(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository
}
