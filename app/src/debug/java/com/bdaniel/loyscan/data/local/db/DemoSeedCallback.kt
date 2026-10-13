package com.bdaniel.loyscan.data.local.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.BarcodeFormat
import java.time.Instant

/**
 * Debug builds only: fills a newly created database with demo stores and cards, so the app has something to show
 * until cards can be added (#6). Release builds don't register it (`src/release/.../BuildTypeDatabaseCallbacks.kt`).
 *
 * The scanned formats are not real scans: several numbers can't be encoded in their format (9 digits as EAN_13), so
 * they exercise the barcode renderer's fallback (#4). See wiki/features/seed-data.md.
 */
class DemoSeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.beginTransaction()
        try {
            STORES.forEach { db.insert("stores", SQLiteDatabase.CONFLICT_ABORT, it.toContentValues()) }
            CARDS.forEach { db.insert("loyalty_cards", SQLiteDatabase.CONFLICT_ABORT, it.toContentValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        val SEEDED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")

        fun demoStore(id: String, name: String, color: String, logo: String) = StoreEntity(
            id = id,
            name = name,
            color = color,
            logo = logo,
            aliases = emptyList(),
            countryCodes = listOf("HU"),
            defaultBarcodeFormat = BarcodeFormat.CODE_128,
            cardNumberRule = null,
            isCustom = false,
            updatedAt = SEEDED_AT,
        )

        fun demoCard(n: Int, storeId: String, label: String, number: String, scannedFormat: BarcodeFormat) =
            LoyaltyCardEntity(
                id = "00000000-0000-4000-8000-%012d".format(n),
                storeId = storeId,
                label = label,
                number = number,
                scannedFormat = scannedFormat,
                createdAt = SEEDED_AT,
                lastUsedAt = null,
                useCount = 0,
                note = null,
                frontPhotoPath = null,
                backPhotoPath = null,
                updatedAt = SEEDED_AT,
            )

        val STORES = listOf(
            demoStore("demo-a", "Brand A", "#FF0000", "https://picsum.photos/seed/1/200"),
            demoStore("demo-b", "Brand B", "#00FF00", "https://picsum.photos/seed/2/200"),
            demoStore("demo-c", "Brand C", "#0000FF", "https://picsum.photos/seed/3/200"),
            demoStore("demo-d", "Brand D", "#FFFF00", "https://picsum.photos/seed/4/200"),
            demoStore("demo-e", "Brand E", "#FF00FF", "https://picsum.photos/seed/5/200"),
        )

        val CARDS = listOf(
            demoCard(1, "demo-a", "Card A", "123456789", BarcodeFormat.EAN_13),
            demoCard(2, "demo-b", "Card B", "987654321", BarcodeFormat.EAN_13),
            demoCard(3, "demo-c", "Card C", "112233445", BarcodeFormat.EAN_13),
            demoCard(4, "demo-a", "SuperMart Rewards", "543210987", BarcodeFormat.CODE_128),
            demoCard(5, "demo-b", "Coffee Bean Club", "678901234", BarcodeFormat.QR_CODE),
            demoCard(6, "demo-c", "Bookworm Bonus", "109876543", BarcodeFormat.EAN_13),
            demoCard(7, "demo-d", "Tech Store VIP", "9876543210", BarcodeFormat.CODE_39),
            demoCard(8, "demo-e", "Fashion Outlet Pass", "1234509876", BarcodeFormat.AZTEC),
            demoCard(9, "demo-a", "Grocery Saver", "1122334455", BarcodeFormat.EAN_8),
            demoCard(10, "demo-d", "Pet Supplies Club", "6677889900", BarcodeFormat.DATA_MATRIX),
            demoCard(11, "demo-b", "Cinema Fan Card", "0011223344", BarcodeFormat.PDF_417),
        )
    }
}
