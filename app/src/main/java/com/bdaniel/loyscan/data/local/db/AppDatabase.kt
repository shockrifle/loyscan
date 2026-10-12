package com.bdaniel.loyscan.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardDao
import com.bdaniel.loyscan.data.local.db.dao.StoreDao
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity

@Database(entities = [StoreEntity::class, LoyaltyCardEntity::class], version = 2)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun loyaltyCardDao(): LoyaltyCardDao
}
