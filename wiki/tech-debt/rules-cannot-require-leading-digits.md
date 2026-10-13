# Card-number rules can't require leading digits

**Status:** Open · **Issue:** [#32](https://github.com/shockrifle/loyscan/issues/32) · **Since:** 2026-10-13 (`ee569f1`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/domain/cardnumber/CardNumberRule.kt`, `app/src/main/assets/catalogue/stores.json`

## What
A `CardNumberRule` can add a `prefix` to the core, but it can't require the core itself to start with given digits.
The EAN-13 catalogue stores (`hu-auchan`, `hu-dm`, `hu-penny`, `hu-rossmann`) print all 13 digits on the card, so
their core is the full number: 13 digits, `GTIN_MOD10` `VERIFY`, no prefix. Their store-specific leading digits
(`999…`, `994…`, …) are not checked anywhere.

## Why it exists
The core is the number as printed ([[2026-10-09-card-number-rule-engine]]). With the store prefix modelled as
`prefix` + a short core + `APPEND`, a user who types the full printed number gets `TOO_LONG`. The 13-digit
`VERIFY` form accepts what is printed and covers several card types of one store at once. Chosen when the first
entries shipped ([[store-catalogue]]).

## Risk / impact
- `CardNumberEngine.match` accepts any valid EAN-13 for all four stores, so the scan-to-store check in the add-card
  flow (#6) can't tell them apart.
- A typed number from a different store passes validation. Its barcode is still the typed number, so nothing at the
  till changes; only the "wrong store?" hint is lost.

## How to pay it down
Add an optional list of allowed leading digits to the rule (validated against the core, so `validate` and `match`
both reject other prefixes), then set it on the EAN-13 entries once their prefixes are confirmed
([#31](https://github.com/shockrifle/loyscan/issues/31)).
