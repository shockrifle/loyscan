package com.bdaniel.loyscan.di

import androidx.room.RoomDatabase
import com.bdaniel.loyscan.data.local.db.DemoSeedCallback

/** Debug builds seed demo cards into a new database. The release source set has the same function, returning none. */
fun buildTypeDatabaseCallbacks(): List<RoomDatabase.Callback> = listOf(DemoSeedCallback())
