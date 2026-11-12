package com.bdaniel.loyscan.testing

// Reading Kotlin source as text, for the tests that check a rule across the sources (WaitTimeoutsTest,
// DesignValuesTest): what is code, and what is a comment or a string that only mentions it.

/** [source] with every comment, string and char literal replaced by spaces, keeping the line breaks. */
fun blankCommentsAndStrings(source: String): String {
    val out = StringBuilder(source)
    fun blank(from: Int, to: Int) {
        for (i in from until minOf(to, source.length)) if (out[i] != '\n') out[i] = ' '
    }
    var i = 0
    while (i < source.length) {
        val end = when {
            source.startsWith("//", i) -> source.indexOf('\n', i).let { if (it < 0) source.length else it }
            source.startsWith("/*", i) -> source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
            source.startsWith("\"\"\"", i) -> source.indexOf("\"\"\"", i + 3).let { if (it < 0) source.length else it + 3 }
            source[i] == '"' || source[i] == '\'' -> quotedEnd(source, i)
            else -> -1
        }
        if (end < 0) {
            i++
        } else {
            blank(i, end)
            i = end
        }
    }
    return out.toString()
}

/**
 * The index after the closing quote of the string or char literal opening at [start], skipping escapes and a string's
 * `${…}` templates, whose own strings would otherwise end it early.
 */
private fun quotedEnd(source: String, start: Int): Int {
    val quote = source[start]
    var i = start + 1
    while (i < source.length && source[i] != quote && source[i] != '\n') {
        i = when {
            source[i] == '\\' -> i + 2
            quote == '"' && source.startsWith("\${", i) -> templateEnd(source, i + 2)
            else -> i + 1
        }
    }
    return minOf(i + 1, source.length)
}

/** The index after the `}` closing a template whose expression starts at [start], skipping the strings in it. */
private fun templateEnd(source: String, start: Int): Int {
    var depth = 1
    var i = start
    while (i < source.length && source[i] != '\n') {
        when (source[i]) {
            '"', '\'' -> {
                i = quotedEnd(source, i)
                continue
            }
            '{' -> depth++
            '}' -> if (--depth == 0) return i + 1
        }
        i++
    }
    return i
}
