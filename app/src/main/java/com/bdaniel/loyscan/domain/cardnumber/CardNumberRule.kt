package com.bdaniel.loyscan.domain.cardnumber

import com.bdaniel.loyscan.domain.BarcodeFormat
import kotlinx.serialization.Serializable

/**
 * Declarative description of one store's card number, authored as JSON (see [CardNumberRules]).
 * Interpreted by [CardNumberEngine].
 *
 * Terms: the **core number** is what the user types / what is printed on the card, after normalisation;
 * the **barcode content** is what the barcode encodes:
 * `prefix + core left-padded to padLength + suffix [+ check digit if mode = APPEND]`.
 *
 * Invalid definitions fail at construction (and so at JSON parse time).
 */
@Serializable
data class CardNumberRule(
    val id: String,
    val format: BarcodeFormat,
    val charset: Charset = Charset.DIGITS,
    val minLength: Int = 1,
    val maxLength: Int = Int.MAX_VALUE,
    val case: LetterCase = LetterCase.UPPER,
    /** Characters removed from user input before validation (spaces are always removed). */
    val separators: String = "-",
    val prefix: String = "",
    val suffix: String = "",
    val padLength: Int = 0,
    val padChar: Char = '0',
    val checkDigit: CheckDigit? = null,
    val display: Display? = null,
) {
    @Serializable
    enum class Charset(val allows: (Char) -> Boolean) {
        DIGITS({ it in '0'..'9' }),
        ALPHANUMERIC({ it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }),
        ANY({ !it.isWhitespace() }),
    }

    @Serializable
    enum class LetterCase { UPPER, LOWER, NONE }

    @Serializable
    data class CheckDigit(val algorithm: CheckDigitAlgorithm, val mode: Mode) {
        @Serializable
        enum class Mode {
            /** Computed over `prefix + padded core + suffix` and appended to the barcode content. */
            APPEND,

            /** The core's last digit is a check digit over the core's other digits; validated, not added. */
            VERIFY,
        }
    }

    /** How to show the number to the user. `#` in [pattern] takes the next character; anything else is literal. */
    @Serializable
    data class Display(val source: Source = Source.CORE, val pattern: String) {
        @Serializable
        enum class Source { CORE, BARCODE }
    }

    init {
        require(id.isNotBlank()) { "Rule id must not be blank" }
        require(minLength in 1..maxLength) { "$id: need 1 <= minLength <= maxLength" }
        require(padLength >= 0) { "$id: padLength must be >= 0" }
        require((prefix + suffix).all(charset.allows)) { "$id: prefix/suffix outside charset $charset" }
        require(padLength == 0 || charset.allows(padChar)) { "$id: padChar outside charset $charset" }
        require(separators.none(charset.allows) || charset == Charset.ANY) {
            "$id: separators overlap charset $charset"
        }
        if (checkDigit != null) {
            require(charset == Charset.DIGITS) { "$id: check digits need charset DIGITS" }
            require(checkDigit.mode != CheckDigit.Mode.VERIFY || minLength >= 2) {
                "$id: VERIFY check digit needs minLength >= 2"
            }
        }
        if (display != null) require('#' in display.pattern) { "$id: display pattern has no '#'" }
    }
}
