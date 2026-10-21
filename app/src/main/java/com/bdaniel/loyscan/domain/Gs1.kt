package com.bdaniel.loyscan.domain

/**
 * GS1 element strings: data in fields, each an Application Identifier (AI, 2-4 digits) and its value. A field of
 * predefined length ends there; any other ends at the GS separator (U+001D, how a scanner sends the FNC1 between two
 * fields) or at the end of the data.
 */
object Gs1 {

    const val SEPARATOR = '\u001D'

    /**
     * [elementString] as printed under a GS1 barcode (its human-readable interpretation): each AI in brackets, as in
     * `(01)09501101020917(10)12AB`. `null` if it isn't a GS1 element string this table can split.
     */
    fun humanReadable(elementString: String): String? {
        if (elementString.isEmpty()) return null
        val out = StringBuilder()
        var i = 0
        while (i < elementString.length) {
            val prefix = elementString.substring(i, minOf(i + 2, elementString.length))
            val aiLength = AI_LENGTH[prefix] ?: return null
            val ai = elementString.substring(i, minOf(i + aiLength, elementString.length))
            if (ai.length < aiLength || !ai.all { it in '0'..'9' }) return null
            val start = i + aiLength
            val end = when (val length = PREDEFINED_LENGTH[prefix]) {
                null -> elementString.indexOf(SEPARATOR, start).takeIf { it >= 0 } ?: elementString.length
                else -> start + length
            }
            if (end > elementString.length || end == start) return null
            val value = elementString.substring(start, end)
            if (SEPARATOR in value) return null
            out.append('(').append(ai).append(')').append(value)
            i = end
            // The separator after the last field isn't sent, so one there means a field is missing.
            if (i < elementString.length && elementString[i] == SEPARATOR) {
                i++
                if (i == elementString.length) return null
            }
        }
        return out.toString()
    }

    /**
     * An AI's length follows from its first two digits. Only prefixes in use: from the AI table of ZXing core 3.5.3
     * (`FieldParser`), which reads GS1 DataBar Expanded.
     */
    private val AI_LENGTH: Map<String, Int> = buildMap {
        (listOf("00", "01", "02", "10", "11", "12", "13", "15", "16", "17", "20", "21", "22", "30", "37") +
            (90..99).map { "$it" }).forEach { put(it, 2) }
        listOf("23", "24", "25", "40", "41", "42", "71").forEach { put(it, 3) }
        listOf("31", "32", "33", "34", "35", "36", "39", "43", "70", "72", "80", "81", "82").forEach { put(it, 4) }
    }

    /**
     * Value lengths of the AIs whose length is predefined by their first two digits (GS1 General Specifications): only
     * these are not followed by a separator. Other fixed-length AIs are, so they are read up to it.
     */
    private val PREDEFINED_LENGTH: Map<String, Int> = buildMap {
        put("00", 18)
        put("01", 14)
        put("02", 14)
        listOf("11", "12", "13", "15", "16", "17").forEach { put(it, 6) }
        put("20", 2)
        listOf("31", "32", "33", "34", "35", "36").forEach { put(it, 6) }
        put("41", 13)
    }
}
