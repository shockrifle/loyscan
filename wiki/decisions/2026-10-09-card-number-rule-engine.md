# Card numbers are interpreted by our own declarative rule engine, with rules in JSON

**Status:** Active · **Date:** 2026-10-09 · **Issue:** [#2](https://github.com/shockrifle/loyscan/issues/2)

A store's **card-number rule** says how the number printed on the card (the *core number*) becomes barcode
content and a display string, and how scanned content maps back to a core number. Rules are data (JSON), read
by a small pure-Kotlin engine in `app/src/main/java/com/bdaniel/loyscan/domain/cardnumber/`.

## Context
Some stores' barcodes don't encode the printed number verbatim: they add a fixed prefix, zero padding or a check
digit, and the number is printed in groups. The add-card flow ([#6](https://github.com/shockrifle/loyscan/issues/6))
needs live validation, and also needs to check whether a scanned barcode belongs to the store the user picked.
The store catalogue ([#3](https://github.com/shockrifle/loyscan/issues/3)) has to be an asset in our own format.

## Options considered
- **Rules as Kotlin code** (one class or lambda per store): type-safe and needs no parser, but the catalogue
  would then be split between an asset and code, and every new store would need code changes and a release review.
- **Rules as data, read by a Kotlin engine** (chosen).
- **Bundle an existing third-party rule database**: ruled out because of licensing and verification. Every rule
  is checked against a real card or the retailer's own material ([#3](https://github.com/shockrifle/loyscan/issues/3)).

## Decision
- `CardNumberRule` (`CardNumberRule.kt`) is a `@Serializable` data class:
  charset, min/max length, letter case, separators to strip, prefix, suffix, left padding, an optional check digit
  (`GTIN_MOD10` or `LUHN`, mode `APPEND` or `VERIFY`), the primary `BarcodeFormat`, an optional display pattern,
  and (since 2026-10-15, #32) `coreStartsWith`: allowed starts of the core, so a store whose full printed number is
  the core can still require its fixed leading digits. A core that starts with none of them is `WRONG_START`, unless
  it is still `TOO_SHORT` and agrees with a start so far (then only `TOO_SHORT`, so live typing isn't flagged early). It is a separate field from `prefix`
  because `prefix` is added to the barcode, while `coreStartsWith` only checks what the user typed or scanned.
- Barcode content = `prefix + core.padStart(padLength, padChar) + suffix`, plus a check digit computed over all
  of that when the mode is `APPEND`. `VERIFY` means the core's last digit is already a check digit over the rest
  of the core, so it is validated but nothing is added.
- Defaults never rewrite content silently. `case` defaults to `NONE` because barcodes are case-sensitive, so a rule
  opts in to `UPPER` or `LOWER`. `separators` defaults to `"-"`, or to `""` for charset `ANY`, where a hyphen may be
  part of the number. The default is an expression on `charset`, and kotlinx-serialization handles it in both
  directions (round-trip test).
- `CardNumberEngine` provides `normalize`, `validate`, `barcodeContent`, `displayNumber` and `match`.
  A `null` rule means "no rule": the barcode content is the trimmed raw number.
- `barcodeContent` falls back to the normalised input when validation fails, so "save anyway" cards still get
  content. That content may not be encodable in `rule.format` (5 digits for EAN-13), so the renderer must fall back
  to another format ([#4](https://github.com/shockrifle/loyscan/issues/4)). Wrapping an invalid number in prefix,
  padding and check digit was rejected: it would produce a well-formed barcode for a wrong number.
- `match` (scanned content → core) is exact: it doesn't change the scanned content's case or strip separators,
  and it accepts a core only if that core validates unchanged and rebuilds into exactly the scanned content.
  A case-insensitive match would save a core whose barcode differs from the one on the card, and Code 128 and QR
  are case-sensitive at the till. When padding is ambiguous (the core may itself start with `padChar`), it tries
  each candidate from shortest to longest, so a core that starts with the pad character still matches when only
  the longer reading passes the check digit. It reports `formatMatches` separately instead of rejecting on a
  format mismatch, so #6 can rank stores.
- **Authoring rule:** if a store's numbers have a fixed length, set `minLength == maxLength`. Otherwise a scanned
  `000123456` padded to 9 matches as core `123456`, and the card shows a number that differs from what's printed.
  The barcode is the same either way.
- `CardNumberRules.parse` is strict: an unknown key, an unknown enum value or a duplicate id throws, and so does an
  invalid definition (via `require` in `init`). A typo in the catalogue fails at load time, not at the checkout.
- "Invalid" includes content that can't fit the rule's barcode format (`CardNumberRule.requireFitsFormat`).
  EAN-8, EAN-13, UPC-A and UPC-E need digits only, at exactly 8, 13, 12 and 8 characters for every allowed core
  length. EAN-8, EAN-13 and UPC-A also need a `GTIN_MOD10` check digit over the whole content: `APPEND`, or
  `VERIFY` with no prefix, suffix or padding. ITF needs one fixed, even length. Known gaps: UPC-E's check digit
  (computed on the expanded UPC-A number) isn't modelled, and the character sets of CODE_39 and CODABAR aren't
  checked. The renderer's fallback in [#4](https://github.com/shockrifle/loyscan/issues/4) covers both.

## Why
Data rules keep each store's facts in one place (the catalogue entry), in a format we own, and the engine stays
testable on the JVM: it has no Android imports, and its only dependency is kotlinx-serialization (added in this
change as `libs.kotlinx.serialization.json`). This was the user's choice (2026-10-09), made to match #3's
"catalogue in our own format".

## Consequences
- `domain/BarcodeFormat.kt` is a new enum (ZXing names, plus `DATABAR*` for GS1 DataBar). Since #1 the card model
  uses it, and a card's barcode content and display number are derived through this engine
  ([[2026-10-12-data-model-v2]]).
- The display pattern uses `#` per character and copies anything else literally. With short input, trailing
  literals are dropped; with long input, the extra characters are appended unformatted.
- Adding an algorithm means adding a `CheckDigitAlgorithm` entry and its test vectors.
- No real store rules exist yet. They arrive with the catalogue (#3), and each one gets its own unit test there.
- Tests: `app/src/test/java/com/bdaniel/loyscan/domain/cardnumber/` — the first real JVM tests in the repo
  ([[no-automated-tests]]).
