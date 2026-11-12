package com.bdaniel.loyscan.testing

/**
 * The lines of [source], a Kotlin file, that use a design value directly instead of a shared one (#108): a non-zero
 * `.dp` literal that isn't the value of a named constant (`val UPPER_CASE = 3.dp`), or a `RoundedCornerShape`, whose
 * place is the theme's `Shapes` and `CardShape`. Comments and strings are skipped. See `wiki/design-system.md`.
 */
fun designValueLiterals(source: String): List<Int> =
    blankCommentsAndStrings(source).lines().mapIndexedNotNull { index, line ->
        val literal = DP_LITERAL.containsMatchIn(line) && !NAMED_CONSTANT.containsMatchIn(line)
        if (literal || SHAPE in line) index + 1 else null
    }

/** A `.dp` literal other than zero: `16.dp`, `0.5.dp`, not `0.dp`. */
private val DP_LITERAL = Regex("""\b(?!0\.dp\b)(?!0\.0\.dp\b)\d+(\.\d+)?\.dp\b""")

/** A line declaring a named constant: `private val SELECTION_BORDER = 3.dp`. */
private val NAMED_CONSTANT = Regex("""^\s*((private|internal|public)\s+)?(const\s+)?val\s+[A-Z][A-Z0-9_]*\s*=""")

private const val SHAPE = "RoundedCornerShape("
