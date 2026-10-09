package com.bdaniel.loyscan.domain.cardnumber

import kotlinx.serialization.json.Json

/** JSON (de)serialisation of [CardNumberRule]s. Strict: unknown keys are an error, so typos in rules fail loudly. */
object CardNumberRules {
    private val json = Json { prettyPrint = true }

    /** Parses a JSON array of rules. Throws on malformed JSON, invalid rules or duplicate ids. */
    fun parse(text: String): List<CardNumberRule> {
        val rules = json.decodeFromString<List<CardNumberRule>>(text)
        val duplicates = rules.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate rule ids: $duplicates" }
        return rules
    }

    fun toJson(rules: List<CardNumberRule>): String = json.encodeToString(rules)
}
