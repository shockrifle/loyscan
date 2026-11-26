package com.bdaniel.loyscan.data.local.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardWithStore
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStoreEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Rows written through the raw database (in Room callbacks, where DAOs can't be used) must read back through the
 * DAOs unchanged, with every column converted the way [Converters] does it.
 */
@RunWith(AndroidJUnit4::class)
class ContentValuesTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun storeAndCard_writtenAsContentValues_readBackUnchanged() = runTest {
        // Every field is set to a non-default value, so a column that is missed or converted differently shows up.
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
        )
        val card = aLoyaltyCardEntity(
            storeId = "custom-0001",
            label = "Mine",
            number = "ABC-123",
            scannedFormat = BarcodeFormat.QR_CODE,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastUsedAt = Instant.parse("2026-02-01T00:00:00Z"),
            useCount = 7,
            note = "a note",
            frontPhotoPath = "front.jpg",
            backPhotoPath = "back.jpg",
            showPhotoOnList = true,
            updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
        )

        val raw = db.openHelper.writableDatabase
        raw.insert("stores", SQLiteDatabase.CONFLICT_ABORT, store.toContentValues())
        raw.insert("loyalty_cards", SQLiteDatabase.CONFLICT_ABORT, card.toContentValues())

        assertThat(db.loyaltyCardDao().getByIdWithStore(card.id).first()).isEqualTo(LoyaltyCardWithStore(card, store))
    }

    @Test
    fun nullableColumns_writtenAsNull() = runTest {
        val store = aStoreEntity(logo = null, cardNumberRule = null, isCustom = false)
        val card = aLoyaltyCardEntity(label = null, scannedFormat = null, lastUsedAt = null, note = null)

        val raw = db.openHelper.writableDatabase
        raw.insert("stores", SQLiteDatabase.CONFLICT_ABORT, store.toContentValues())
        raw.insert("loyalty_cards", SQLiteDatabase.CONFLICT_ABORT, card.toContentValues())

        assertThat(db.loyaltyCardDao().getByIdWithStore(card.id).first()).isEqualTo(LoyaltyCardWithStore(card, store))
    }
}
