package com.bdaniel.loyscan.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.testing.aStoreEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class StoreDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: StoreDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.storeDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getById_storesEveryField() = runTest {
        // Every field is set to a non-default value, so a column that isn't persisted or converted shows up.
        val store = aStoreEntity(
            id = "custom-0001",
            name = "Other Store",
            color = "#112233",
            logo = "https://example.com/logo.svg",
            aliases = listOf("Other", "OS"),
            countryCodes = listOf("HU", "AT"),
            defaultBarcodeFormat = BarcodeFormat.EAN_13,
            cardNumberRule = CardNumberRule(id = "example", format = BarcodeFormat.CODE_128, minLength = 4, maxLength = 10),
            frequentlyAdded = true,
            isCustom = true,
            updatedAt = Instant.parse("2026-05-06T07:08:09.123Z"),
            logoStyle = LogoStyle.SINGLE_COLOUR,
            logoTint = "#FFFFFF",
            logoOutline = true,
        )
        dao.insert(store)

        assertThat(dao.getById("custom-0001").first()).isEqualTo(store)
    }

    @Test
    fun getById_unknownId_emitsNull() = runTest {
        assertThat(dao.getById("missing").first()).isNull()
    }

    @Test
    fun getAll_returnsEveryStore() = runTest {
        val first = aStoreEntity(id = "first")
        val second = aStoreEntity(id = "second", name = "Second Store")
        dao.insert(first)
        dao.insert(second)

        assertThat(dao.getAll().first()).containsExactly(first, second)
    }
}
