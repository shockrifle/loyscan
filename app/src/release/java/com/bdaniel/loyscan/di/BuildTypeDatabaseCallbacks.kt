package com.bdaniel.loyscan.di

import androidx.room.RoomDatabase

/** Release builds ship no demo data. The debug source set has the same function, returning the demo seeder. */
fun buildTypeDatabaseCallbacks(): List<RoomDatabase.Callback> = emptyList()
