package com.bdaniel.loyscan.testing

import android.content.Context
import androidx.room.Room
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** In `@HiltAndroidTest`s, an empty in-memory database replaces the seeded on-disk one. Inject it to add rows. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object InMemoryDatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @Provides
    fun provideBrandDao(appDatabase: AppDatabase) = appDatabase.brandDao()

    @Provides
    fun provideLoyaltyCardDao(appDatabase: AppDatabase) = appDatabase.loyaltyCardDao()
}
