package com.bdaniel.loyscan.testing

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Room's exported schemas, `app/schemas/…/<version>.json` (unit tests run in the module directory). */
private val SCHEMA_DIR = File("schemas/com.bdaniel.loyscan.data.local.db.AppDatabase")

/** The `database` object of the exported schema [version]. */
fun exportedSchema(version: Int): JsonObject =
    Json.parseToJsonElement(File(SCHEMA_DIR, "$version.json").readText()).jsonObject.getValue("database").jsonObject

/**
 * Creates the database [name] in the app's database directory exactly as Room created it at [version]: its tables,
 * indices and Room's identity row, from the exported schema. What an installed build left behind.
 *
 * Room's `MigrationTestHelper.createDatabase` does the same, but fails on Windows: its driver matches the database's
 * path to its name by `substringAfterLast('/')` (androidx.sqlite 2.6.2, checked 2026-11-05).
 */
fun createDatabaseFromExportedSchema(context: Context, name: String, version: Int): SQLiteDatabase {
    val schema = exportedSchema(version)
    val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs(); delete() }
    val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    schema.getValue("entities").jsonArray.forEach { entity ->
        val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
        val statements = listOf(entity.jsonObject.getValue("createSql")) +
            (entity.jsonObject["indices"]?.jsonArray?.map { it.jsonObject.getValue("createSql") } ?: emptyList())
        statements.forEach { db.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
    }
    schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
    db.version = version
    return db
}
