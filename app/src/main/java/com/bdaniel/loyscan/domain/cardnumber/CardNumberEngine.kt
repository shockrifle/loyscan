package com.bdaniel.loyscan.domain.cardnumber

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.CheckDigit.Mode
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.Display.Source

sealed interface Validation {
    data class Valid(val core: String) : Validation
    data class Invalid(val core: String, val errors: Set<Error>) : Validation

    enum class Error { EMPTY, INVALID_CHARACTERS, TOO_SHORT, TOO_LONG, WRONG_START, BAD_CHECK_DIGIT }
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
            // A too-short core that can still grow into an allowed start is only TOO_SHORT, so live input isn't
            // flagged early. A core of allowed length must start with one in full.
            val stillTyping = core.length < rule.minLength
            if (rule.coreStartsWith.isNotEmpty() &&
                rule.coreStartsWith.none { core.startsWith(it) || (stillTyping && it.startsWith(core)) }
            ) add(Validation.Error.WRONG_START)
        }
        if (errors.isNotEmpty()) return Validation.Invalid(core, errors)
        // The check digit is only meaningful once the number is otherwise well-formed.
        val check = rule.checkDigit
        if (check?.mode == Mode.VERIFY && !check.algorithm.verify(core)) {
            return Validation.Invalid(core, setOf(Validation.Error.BAD_CHECK_DIGIT))
        }
        return Validation.Valid(core)
    }

    /**
     * What the barcode must encode. If [raw] doesn't validate against [rule] (the user chose "save anyway"),
     * falls back to the normalised number, so a card can always be shown.
     *
     * Only valid input is guaranteed to fit `rule.format` (checked when the rule is created). The fallback may not
     * be encodable in it, e.g. 5 digits for EAN-13; whoever draws the barcode must then fall back to another
     * format (CODE_128, then QR — issue #4). Building prefix/padding/check digit around an invalid number is
     * deliberately not done: it would produce a well-formed barcode for a wrong number.
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
        // Scanned content is exact: no case or separator normalisation, it must rebuild to the same string.
        var rest = content
        if (rule.checkDigit?.mode == Mode.APPEND) {
            if (!rule.checkDigit.algorithm.verify(rest)) return null
            rest = rest.dropLast(1)
        }
        if (rest.length < rule.prefix.length + rule.suffix.length) return null
        if (!rest.startsWith(rule.prefix) || !rest.endsWith(rule.suffix)) return null
        rest = rest.substring(rule.prefix.length, rest.length - rule.suffix.length)
        // Padding is ambiguous when the core itself may start with padChar: try the shortest candidate first.
        // Rules for fixed-length numbers avoid the ambiguity with minLength == maxLength.
        val leadingPad = if (rule.padLength > 0 && rest.length == rule.padLength) {
            rest.takeWhile { it == rule.padChar }.length
        } else 0
        val core = (leadingPad downTo 0).asSequence()
            .map { rest.substring(it) }
            .firstOrNull { candidate -> validate(candidate, rule) == Validation.Valid(candidate) && build(candidate, rule) == content }
            ?: return null
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
}
