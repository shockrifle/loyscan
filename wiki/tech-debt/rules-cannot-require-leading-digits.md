# Card-number rules can't require leading digits

**Status:** Resolved (`a76d6c3`) · **Issue:** [#32](https://github.com/shockrifle/loyscan/issues/32) · **Since:** 2026-10-13 (`ee569f1`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/domain/cardnumber/CardNumberRule.kt`, `app/src/main/assets/catalogue/stores.json`

**Resolved:** 2026-10-15 (`ae363bf`, `6c58575`, `a76d6c3`): rules can require leading digits (`coreStartsWith`), and
since 2026-10-19 (`e5be559`) the EAN-13 stores confirmed on real cards use it. Auchan's rule still accepts any valid
EAN-13 because its prefix isn't confirmed; that is a catch-all rule like the ones in
[#52](https://github.com/shockrifle/loyscan/issues/52), which tracks it since 2026-10-22
([[2026-10-22-store-suggestion-trigger]] → Consequences).

## What
Until 2026-10-19 the shipped EAN-13 entries didn't require their store's leading digits; now only Auchan doesn't. (Until 2026-10-15 the rule model couldn't
express it either; now it can, see below.) The EAN-13 catalogue stores (`hu-auchan`, `hu-dm`, `hu-penny`, `hu-rossmann`) print all 13 digits on the card, so
their core is the full number: 13 digits, `GTIN_MOD10` `VERIFY`, no prefix. Their store-specific leading digits
(`999…`, `994…`, …) were not checked anywhere.

## Why it exists
The core is the number as printed ([[2026-10-09-card-number-rule-engine]]). With the store prefix modelled as
`prefix` + a short core + `APPEND`, a user who types the full printed number gets `TOO_LONG`. The 13-digit
`VERIFY` form accepts what is printed and covers several card types of one store at once. Chosen when the first
entries shipped ([[store-catalogue]]).

## Risk / impact
- `CardNumberEngine.match` accepted any valid EAN-13 for all four stores, so the scan-to-store check in the add-card
  flow (#6) couldn't tell them apart.
- A typed number from a different store passed validation. Its barcode is still the typed number, so nothing at the
  till changes; only the "wrong store?" hint is lost.

## How to pay it down
1. Done 2026-10-15: `CardNumberRule.coreStartsWith`, a list of allowed starts of the core. `validate` reports
   `WRONG_START` when the core starts with none of them, so `match` rejects other stores' numbers too. A core that
   is only the beginning of an allowed start gets `TOO_SHORT` instead, so live typing isn't flagged early; a valid
   core always contains a start in full. Tests: `CardNumberEngineTest` ("required start"), `CardNumberRulesTest`.
2. Done 2026-10-19 for Decathlon, dm, Penny and Rossmann (and IKEA Family): they set `coreStartsWith` from real
   cards, and `ShippedCatalogueTest` checks that they reject each other's vectors. Auchan's prefix moved to #52
   (see Resolved above).
