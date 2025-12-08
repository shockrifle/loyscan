package com.bdaniel.loyscan.di

import android.content.Context
import androidx.room.Room
import com.bdaniel.loyscan.data.local.db.AppDatabase
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
                context,
                AppDatabase::class.java, "loyscan.db"
        ).build()
    }

    @Provides
    fun provideBrandDao(appDatabase: AppDatabase) = appDatabase.brandDao()

    @Provides
    fun provideLoyaltyCardDao(appDatabase: AppDatabase) = appDatabase.loyaltyCardDao()
}