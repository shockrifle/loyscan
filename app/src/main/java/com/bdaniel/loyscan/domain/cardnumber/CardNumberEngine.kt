package com.bdaniel.loyscan.domain.cardnumber

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.CheckDigit.Mode
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.Display.Source
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.LetterCase

sealed interface Validation {
    data class Valid(val core: String) : Validation
    data class Invalid(val core: String, val errors: Set<Error>) : Validation

    enum class Error { EMPTY, INVALID_CHARACTERS, TOO_SHORT, TOO_LONG, BAD_CHECK_DIGIT }
}

/** Result of reading scanned barcode content back through a rule. */
data class RuleMatch(val core: String, val formatMatches: Boolean)

/**
 * Interprets [CardNumberRule]s. Every function accepts a `null` rule, meaning "no rule": the barcode content
 * and display number are the raw number itself (whitespace trimmed).
 */
object CardNumberEngine {

    /** Removes whitespace and the rule's separators, then applies its letter case. */
    fun normalize(raw: String, rule: CardNumberRule?): String {
        if (rule == null) return raw.trim()
        val stripped = raw.filterNot { it.isWhitespace() || it in rule.separators }
        return rule.case.apply(stripped)
    }

    fun validate(raw: String, rule: CardNumberRule?): Validation {
        val core = normalize(raw, rule)
        if (core.isEmpty()) return Validation.Invalid(core, setOf(Validation.Error.EMPTY))
        if (rule == null) return Validation.Valid(core)
        val errors = buildSet {
            if (!core.all(rule.charset.allows)) add(Validation.Error.INVALID_CHARACTERS)
            if (core.length < rule.minLength) add(Validation.Error.TOO_SHORT)
            if (core.length > rule.maxLength) add(Validation.Error.TOO_LONG)
            val check = rule.checkDigit
            if (isEmpty() && check?.mode == Mode.VERIFY && !check.algorithm.verify(core)) {
                add(Validation.Error.BAD_CHECK_DIGIT)
            }
        }
        return if (errors.isEmpty()) Validation.Valid(core) else Validation.Invalid(core, errors)
    }

    /**
     * What the barcode must encode. If [raw] doesn't validate against [rule] (the user chose "save anyway"),
     * falls back to the normalised number, so a card can always be shown.
     */
    fun barcodeContent(raw: String, rule: CardNumberRule?): String =
        when (val v = validate(raw, rule)) {
            is Validation.Valid -> build(v.core, rule)
            is Validation.Invalid -> v.core
        }

    /** The number formatted for humans, per the rule's display pattern; unformatted if there is none. */
    fun displayNumber(raw: String, rule: CardNumberRule?): String {
        val display = rule?.display ?: return normalize(raw, rule)
        val source = when (display.source) {
            Source.CORE -> normalize(raw, rule)
            Source.BARCODE -> barcodeContent(raw, rule)
        }
        return applyPattern(source, display.pattern)
    }

    /**
     * Reverse direction: given scanned barcode [content] (and its [format], if known), recover the core number,
     * or `null` if the content can't have been produced by [rule]. Used to check the user picked the right store.
     */
    fun match(content: String, format: BarcodeFormat?, rule: CardNumberRule): RuleMatch? {
        var rest = rule.case.apply(content)
        if (rule.checkDigit?.mode == Mode.APPEND) {
            if (!rule.checkDigit.algorithm.verify(rest)) return null
            rest = rest.dropLast(1)
        }
        if (!rest.startsWith(rule.prefix) || !rest.endsWith(rule.suffix)) return null
        if (rest.length < rule.prefix.length + rule.suffix.length) return null
        rest = rest.substring(rule.prefix.length, rest.length - rule.suffix.length)
        if (rule.padLength > 0 && rest.length == rule.padLength) {
            // Padding is ambiguous when the core itself may start with padChar; take the shortest valid core.
            val keep = maxOf(rule.minLength, 1)
            while (rest.length > keep && rest.first() == rule.padChar) rest = rest.drop(1)
        }
        val core = (validate(rest, rule) as? Validation.Valid)?.core ?: return null
        if (build(core, rule) != rule.case.apply(content)) return null
        return RuleMatch(core, formatMatches = format == null || format == rule.format)
    }

    private fun build(core: String, rule: CardNumberRule?): String {
        if (rule == null) return core
        val body = rule.prefix + core.padStart(rule.padLength, rule.padChar) + rule.suffix
        val check = rule.checkDigit
        return if (check?.mode == Mode.APPEND) body + check.algorithm.compute(body) else body
    }

    private fun applyPattern(value: String, pattern: String): String {
        val out = StringBuilder()
        var i = 0
        for (p in pattern) {
            if (i == value.length) break
            if (p == '#') out.append(value[i++]) else out.append(p)
        }
        if (i < value.length) out.append(value, i, value.length)
        return out.toString()
    }

    private fun LetterCase.apply(s: String) = when (this) {
        LetterCase.UPPER -> s.uppercase()
        LetterCase.LOWER -> s.lowercase()
        LetterCase.NONE -> s
    }
}
