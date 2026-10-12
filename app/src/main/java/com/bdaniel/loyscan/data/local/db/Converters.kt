package com.bdaniel.loyscan.data.local.db

import androidx.room.TypeConverter
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Room column types for the store and card entities: lists and card-number rules as JSON text, instants as epoch
 * milliseconds. Decoding is strict (unknown keys and invalid rules throw), like the catalogue parser.
 */
class Converters {

    @TypeConverter
    fun fromStringList(list: List<String>): String = Json.encodeToString(list)

    @TypeConverter
    fun toStringList(json: String): List<String> = Json.decodeFromString(json)

    @TypeConverter
    fun fromCardNumberRule(rule: CardNumberRule?): String? = rule?.let { Json.encodeToString(it) }

    @TypeConverter
    fun toCardNumberRule(json: String?): CardNumberRule? = json?.let { Json.decodeFromString<CardNumberRule>(it) }

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? = epochMillis?.let(Instant::ofEpochMilli)
}
