package com.bdaniel.loyscan.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.aStoreEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Room-backed store repository over a real in-memory database: rows come out as domain stores. */
@RunWith(AndroidJUnit4::class)
class RoomStoreRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomStoreRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomStoreRepository(db.storeDao())
        db.storeDao().insert(aStoreEntity(id = "first", name = "First Store", aliases = listOf("First")))
        db.storeDao().insert(aStoreEntity(id = "second", name = "Second Store", color = "#112233"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getAll_returnsDomainStores() = runTest {
        assertThat(repository.getAll().first()).containsExactly(
            aStore(id = "first", name = "First Store", aliases = listOf("First")),
            aStore(id = "second", name = "Second Store", color = "#112233"),
        )
    }

    @Test
    fun add_insertsTheStore_andGetAllEmitsIt() = runTest {
        val custom = aStore(id = "custom", name = "Custom Store", countryCodes = emptyList(), isCustom = true)

        repository.add(custom)

        assertThat(repository.getAll().first()).contains(custom)
    }
}
