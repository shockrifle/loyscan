# Data model v2: stores and cards with string ids, derived barcodes

**Status:** Active · **Date:** 2026-10-12 · **Issue:** [#1](https://github.com/shockrifle/loyscan/issues/1)

Schema v2 replaces the demo `brands` / `loyalty_cards` model with `stores` and `loyalty_cards` that carry what the
v1 features need. Ids are strings, barcode content and display number are derived on read, and lists and rules
are stored as JSON text.

## Context
#1 lists the fields: store name, colour, optional logo, aliases, country codes, default barcode format, optional
card-number rule ([[2026-10-09-card-number-rule-engine]]), `isCustom`; card label, number, scanned format,
timestamps, use count, note, photo paths. It also asks for ids that let restores, imports (#19) and shared cards
(#25) merge into an existing set without clashes, and it says barcode content and display number must be
*derived*, not stored.

## Options considered
- **Ids:** auto-increment `Int` (as before) vs. string ids. Integers clash as soon as two databases are merged.
- **Store/card join:** the hand-aliased `JOIN` with `@Embedded(prefix = "brand_")` (as before) vs. `@Relation`.
  The store now has 10 columns, and each would need an alias kept in sync by hand.
- **Lists (aliases, country codes):** a JSON text column vs. child tables. Child tables allow SQL search, but the
  lists are short and nothing queries them in SQL yet.
- **The rule:** a JSON copy in the store row vs. a rule id resolved against the catalogue asset at read time.
- **Naming:** keep `Brand` vs. rename to `Store`, the word the issues and the UI flow use.

## Decision
- `Store` / `StoreEntity` (table `stores`) and `LoyaltyCard` / `LoyaltyCardEntity` (table `loyalty_cards`,
  `storeId` FK, `ON DELETE CASCADE`). `Brand` is gone from code and schema; "brand colour" still names
  `Store.color`.
- **Ids are `String`.** Cards get a UUID. Custom stores get a UUID; catalogue stores get a stable slug, so a
  catalogue update can find its rows again (#3 chooses the slug format). Every row has `updatedAt` for merging.
- **`LoyaltyCardWithStore` uses `@Relation`** (Room runs a second query inside `@Transaction`), so no column is
  aliased by hand.
- **`Converters`** (`app/src/main/java/com/bdaniel/loyscan/data/local/db/Converters.kt`): `List<String>` and
  `CardNumberRule?` as JSON text (strict decoding, so a broken stored rule throws instead of silently becoming
  "no rule"), `Instant` as epoch milliseconds. Enums (`BarcodeFormat`) use Room's built-in name mapping.
- **The rule is copied into the store row.** The database stays self-contained, and a custom store could carry a
  rule later.
- **`number` is the raw input**, as the issue asks: the number as typed, or the barcode content exactly as
  scanned. `scannedFormat != null` marks it as a scan.
- **Derived on read** (`app/src/main/java/com/bdaniel/loyscan/domain/LoyaltyCard.kt`):
  - Typed card: `barcodeContent` and `displayNumber` run `CardNumberEngine` over `number` and the store's rule.
  - Scanned card: `barcodeContent` is `number` verbatim. `displayNumber` is the core that `CardNumberEngine.match`
    recovers from the scan, formatted by the rule, or the scanned content when the rule doesn't match.
  - `barcodeFormat` is the scanned format, else the rule's format, else the store's `defaultBarcodeFormat`.
  A scan is the ground truth of what is on the card: rebuilding it from the store's *current* rule would change the
  barcode when a catalogue update changes the rule, or produce a barcode that isn't on the card when the scan
  doesn't match the rule (found in review of #1).
- Schema version 2; `1.json` is kept as the frozen record of v1. No migration from v1: nothing has been released
  ([[no-room-migrations]]).

## Why
String ids are the only option that meets the merge requirement. `@Relation` removes a class of silent mapping
bugs. JSON columns and the inline rule keep the schema small until a feature needs SQL over them. The rename
makes the code use the same word as the issues. This was decided while implementing #1 (inferred trade-offs, not
separately discussed with the user).

## Consequences
- Searching by alias (#6, #7) matches against the JSON text or filters in memory. If that gets slow, move aliases
  to a child table or an FTS table.
- Changing a rule in the catalogue doesn't reach existing store rows by itself. The catalogue loader (#3, implemented
  2026-10-13 in [[store-catalogue]]) has to
  update them by slug.
- Renaming or removing a `BarcodeFormat` constant breaks stored rows, which hold the enum name.
- A scanned card is drawn exactly as scanned, in its scanned format, whatever its store's rule says. Editing a
  scanned card's number by hand (#8) must clear `scannedFormat`, or the typed number would be drawn verbatim.
- Stored rules are decoded strictly, so a rule row that no longer parses (a new `require` in `CardNumberRule`, a
  renamed enum constant, a removed field) throws on every read of its store's cards, and the list screen crashes
  on open. Hence: the catalogue loader (#3) must rewrite stored catalogue rules before they are read, and a change
  to the rule format that old rows fail needs a DB version bump whose migration rewrites them. Decoding to "no
  rule" instead was rejected: it would silently change typed cards' barcodes.
- The demo seed still ships in every build. The catalogue (#3) replaces it ([[seed-data]]).
