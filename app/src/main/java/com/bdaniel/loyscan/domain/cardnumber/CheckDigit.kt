package com.bdaniel.loyscan.domain.cardnumber

/** Check-digit algorithms. All operate on decimal digit strings and return a single digit. */
enum class CheckDigitAlgorithm {
    /** EAN-8/EAN-13/UPC-A/GTIN: weights 3,1,3,… from the rightmost payload digit, mod 10. */
    GTIN_MOD10,

    /** Luhn (ISO/IEC 7812-1): double every second digit from the rightmost payload digit, mod 10. */
    LUHN;

    fun compute(payload: String): Char {
        require(payload.isNotEmpty() && payload.all { it in '0'..'9' }) {
            "Check digit payload must be non-empty digits: '$payload'"
        }
        val sum = payload.reversed().foldIndexed(0) { i, acc, c ->
            val d = c - '0'
            acc + when (this) {
                GTIN_MOD10 -> if (i % 2 == 0) d * 3 else d
                LUHN -> if (i % 2 == 0) (d * 2).let { if (it > 9) it - 9 else it } else d
            }
        }
        return '0' + (10 - sum % 10) % 10
    }

    /** True if the last character of [number] is the correct check digit for the rest. */
    fun verify(number: String): Boolean =
        number.length >= 2 && number.all { it in '0'..'9' } && compute(number.dropLast(1)) == number.last()
}
