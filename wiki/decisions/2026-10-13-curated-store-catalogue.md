# A small curated store catalogue, synced into Room on every open

**Status:** Active · **Date:** 2026-10-13 · **Issue:** [#3](https://github.com/shockrifle/loyscan/issues/3),
[#18](https://github.com/shockrifle/loyscan/issues/18) · Supersedes [[2025-12-09-seed-db-from-sql-asset]]

The app ships its own small list of stores (Hungary first) in its own JSON format. Stores are matched by a
country-prefixed slug id and written into the `stores` table each time the database opens. The catalogue has no
logos. The SQL demo seed was removed, and the demo cards were left in debug builds only, then removed in #55 ([[seed-data]]).

## Context
- A card needs a store with a barcode format and, where the barcode isn't the printed number, a card-number rule
  ([[2026-10-09-card-number-rule-engine]]). Picking the store comes first in the add-card flow (#6).
- Since data model v2 the rule is copied into the store row, and stored rules are decoded strictly. So when the
  catalogue changes a rule, or the rule format changes, stored rows must be rewritten before they are read
  ([[2026-10-12-data-model-v2]]).
- The previous loader ran `loyscan.sql` line by line on an unawaited executor, with no transaction and no logging
  ([[line-based-sql-seed-parser]]). It ran only when the database was created.

## Options considered
- **Source of the entries:** bundle an existing app's catalogue, or curate our own. Existing catalogues are
  third-party compilations (protected as databases, often under restrictive terms), and their logos are retailer
  trademarks.
- **Logos:** bundle retailer logos, or show the store name on its brand colour (later, the user's own card photo,
  #10).
- **Ids:** plain slugs (`tesco`), or country-prefixed slugs (`hu-tesco`).
- **Loading:** keep the SQL seed and add an update script; use Room `createFromAsset` with a prebuilt database; or
  parse a JSON asset in a Room callback and write rows from Kotlin.
- **When:** only in `onCreate`, on a background thread, or synchronously in `onOpen`.

## Decision
- **Curated, in our own format** (`assets/catalogue/stores.json`, parsed by `StoreCatalogue`, [[store-catalogue]]).
  Each fact (format, length, prefix, check digit) is checked against a real card or the retailer's own public
  material. Other apps' catalogues may be used as a checklist of what to check, never copied in bulk. How to add a
  store: [[add-a-catalogue-store]].
- **Exception for the first 16 entries (2026-10-13):** the retailers' own pages confirm each programme exists and
  most colours, but the number facts (the 10 rules' format, length, leading digits and check digit, and the other 6
  entries' default barcode format) come only from the checklist catalogue. The user accepted shipping them as provisional facts, to be reviewed later. They stay
  provisional until confirmed on real cards ([#31](https://github.com/shockrifle/loyscan/issues/31)). A rule was
  kept even where unconfirmed, because no rule is just as unconfirmed: it asserts that the barcode is the typed number.
- **No bundled logos.** `logo` is `null` for catalogue stores, and tiles show the name on the brand colour.
  (Amended 2026-11-12: re-examined after research into trademark law, Play policy and the retailers' press kits, and
  kept, with a non-affiliation note required before release: [[2026-11-12-no-bundled-store-logos]].)
- **Country-prefixed slug ids** (`hu-ikea-family`): the same chain can issue a different card in each country, and
  each needs its own entry and rule. The id never changes once shipped, because cards reference it.
- **Synced in `onOpen`, synchronously, in one transaction.** Rows are inserted or updated by id, and `updatedAt`
  moves only on a real change.
- **Demo cards are Kotlin code in the debug source set** (`DemoSeedCallback`, `onCreate` only). A release build
  registers no demo callback (`src/release/.../di/BuildTypeDatabaseCallbacks.kt`). (Update 2026-10-22: the demo
  cards were removed in #55, once cards could be added and deleted; [[seed-data]].)

## Why
- Curating keeps the catalogue small enough to check by hand and avoids redistributing someone else's database or
  trademarks (the issue's sourcing rules, set by the user).
- `onOpen` is the only callback that runs before every first read. That is what lets a changed or unparseable stored
  rule be fixed before it crashes the list. Running synchronously also removes the old race between the seed and the
  first query.
- A prebuilt database was rejected because it only applies when the database is created and must match Room's schema
  identity exactly ([[2025-12-09-seed-db-from-sql-asset]]). An update script would mean hand-written SQL with the
  JSON-encoded rules inlined.
- The loading design was decided while implementing #3 (my trade-offs, not separately discussed). The user chose the
  id format, curation and the debug-only demo cards on 2026-10-13.

## Consequences
- The asset is parsed on every database open (milliseconds for tens of entries).
- Catalogue rows can't be edited by the user; any edit would be overwritten on the next open. Users customise
  through custom stores.
- Removing an entry from the catalogue doesn't remove its row, so cards keep their store.
- A new release with a changed rule changes typed cards' barcodes for that store, which is intended. Scanned cards
  keep their scanned content ([[2026-10-12-data-model-v2]]).
- The release build has no cards and no way to add one until #6. (Update 2026-11-25, wiki sweep: cards can be added since #6, [[add-card]].)
