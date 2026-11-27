package com.bdaniel.loyscan.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bdaniel.loyscan.domain.BarcodeFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * v5 → v6 (#187): every card stores its barcode type, `loyalty_cards.barcodeFormat`. Each card gets the type it was
 * drawn in until now, so no barcode changes: a scan its `scannedFormat`; a typed number its store rule's format, else
 * the store's `defaultBarcodeFormat`. The rule is JSON in `stores.cardNumberRule`, so this reads its `format` field
 * alone, leniently: a rule this version can't read as a whole (or not at all) mustn't stop the database opening, and a
 * rule without a readable format gives the store's default.
 */
object CardBarcodeFormatMigration : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The default only fills the column for the moment; every row is written below. The entity has no default.
        db.execSQL("ALTER TABLE loyalty_cards ADD COLUMN barcodeFormat TEXT NOT NULL DEFAULT ''")
        val formats = db.query(
            "SELECT c.id, c.scannedFormat, s.cardNumberRule, s.defaultBarcodeFormat " +
                "FROM loyalty_cards c JOIN stores s ON s.id = c.storeId",
        ).use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    val scanned = rows.getString(1)
                    val format = scanned ?: ruleFormat(rows.getString(2)) ?: rows.getString(3)
                    add(rows.getString(0) to format)
                }
            }
        }
        formats.forEach { (id, format) ->
            db.execSQL("UPDATE loyalty_cards SET barcodeFormat = ? WHERE id = ?", arrayOf<Any>(format, id))
        }
    }

    /** The `format` of a rule's JSON, if it names a [BarcodeFormat]; `null` without a rule or a readable format. */
    private fun ruleFormat(json: String?): String? = json?.let {
        runCatching {
            val name = Json.parseToJsonElement(it).jsonObject.getValue("format").jsonPrimitive.content
            BarcodeFormat.valueOf(name).name
        }.getOrNull()
    }
}
