package com.bdaniel.loyscan.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bdaniel.loyscan.data.local.db.dao.BrandDao
import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardDao
import com.bdaniel.loyscan.data.local.db.entity.BrandEntity
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity

@Database(entities = [BrandEntity::class, LoyaltyCardEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun brandDao(): BrandDao
    abstract fun loyaltyCardDao(): LoyaltyCardDao
}