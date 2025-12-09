package com.bdaniel.loyscan.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bdaniel.loyscan.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
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
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Executors.newSingleThreadExecutor().execute {
                    context.assets.open("database/loyscan.sql").use { inputStream ->
                        inputStream.reader().buffered().lines()?.forEach { line ->
                            line.trim().takeIf { it.isNotEmpty() && !it.startsWith("--") }?.let {
                                db.execSQL(it)
                            }
                        }
                    }
                }
            }
        }).build()
    }

    @Provides
    fun provideBrandDao(appDatabase: AppDatabase) = appDatabase.brandDao()

    @Provides
    fun provideLoyaltyCardDao(appDatabase: AppDatabase) = appDatabase.loyaltyCardDao()
}