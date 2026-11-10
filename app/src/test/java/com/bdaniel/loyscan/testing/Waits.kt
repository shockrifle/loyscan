package com.bdaniel.loyscan.testing

/**
 * The timeout every `waitUntil*` in the tests passes: their 1 s default timed out on a slow CI runner (#56).
 * `WaitTimeoutsTest` fails on a wait that doesn't pass it (#59).
 */
const val WAIT_TIMEOUT_MS = 10_000L

/**
 * The lines of [source], a Kotlin file, with a `waitUntil*` call that doesn't pass [WAIT_TIMEOUT_MS] in its arguments,
 * and so waits on the 1 s default. Comments, strings and imports are skipped.
 */
fun waitsOnTheDefaultTimeout(source: String): List<Int> {
    val code = blankCommentsAndStrings(source).lines()
        .joinToString("\n") { if (it.trimStart().startsWith("import ")) " ".repeat(it.length) else it }
    return WAIT_CALL.findAll(code).filterNot { call ->
        val open = call.range.last + 1
        code[open] == '(' && WAIT_TIMEOUT_NAME in code.substring(open, closingParen(code, open))
    }.map { call -> code.substring(0, call.range.first).count { it == '\n' } + 1 }.toList()
}

/** A call to `waitUntil` or one of its siblings, up to its `(` or trailing `{`; a `fun` declaring one isn't a call. */
private val WAIT_CALL = Regex("""(?<!fun )\bwaitUntil\w*\s*(?=[({])""")
private const val WAIT_TIMEOUT_NAME = "WAIT_TIMEOUT_MS"

/** The index of the `)` closing the `(` at [open], or the end of [code]. */
private fun closingParen(code: String, open: Int): Int {
    var depth = 0
    for (i in open until code.length) {
        when (code[i]) {
            '(' -> depth++
            ')' -> if (--depth == 0) return i
        }
    }
    return code.length
}

/** [source] with every comment, string and char literal replaced by spaces, keeping the line breaks. */
private fun blankCommentsAndStrings(source: String): String {
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

/** The index after the closing quote of the string or char literal opening at [start], skipping escapes. */
private fun quotedEnd(source: String, start: Int): Int {
    var i = start + 1
    while (i < source.length && source[i] != source[start] && source[i] != '\n') {
        i += if (source[i] == '\\') 2 else 1
    }
    return minOf(i + 1, source.length)
}
