package com.bdaniel.loyscan.di

import android.content.Context
import androidx.room.Room
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.data.local.db.CardPhotoSweepCallback
import com.bdaniel.loyscan.data.local.db.CatalogueDatabaseCallback
import com.bdaniel.loyscan.data.photos.FileCardPhotos
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
    fun provideAppDatabase(@ApplicationContext context: Context, photos: FileCardPhotos): AppDatabase {
        return Room.databaseBuilder(
                context,
                AppDatabase::class.java, "loyscan.db"
        )
                .addCallback(CatalogueDatabaseCallback.fromAssets(context))
                .addCallback(CardPhotoSweepCallback(photos))
                .build()
    }

    @Provides
    fun provideStoreDao(appDatabase: AppDatabase) = appDatabase.storeDao()

    @Provides
    fun provideLoyaltyCardDao(appDatabase: AppDatabase) = appDatabase.loyaltyCardDao()
}