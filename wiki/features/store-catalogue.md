# Store catalogue

The app ships a curated list of stores in `app/src/main/assets/catalogue/stores.json`. Each time the database is
opened, the catalogue stores are inserted or brought up to date in the `stores` table, before anything reads it.
Users pick these stores when they add a card (#6). How to add an entry: [[add-a-catalogue-store]]. Why it is curated,
why it has no logos, and why it is loaded this way: [[2026-10-13-curated-store-catalogue]].

## Entry points

- **Format and validation:** `CatalogueEntry` and `StoreCatalogue.parse`
  (`app/src/main/java/com/bdaniel/loyscan/domain/catalogue/StoreCatalogue.kt`). The file is a JSON array, and
  parsing is strict:
  - unknown keys, unknown enum values and duplicate ids throw;
  - the id must be a country-prefixed slug (`hu-ikea-family`) whose country is in `countryCodes`;
  - `color` must be `#RRGGBB`;
  - a `cardNumberRule` must have the store's id, and it is validated like any rule
    ([[2026-10-09-card-number-rule-engine]]);
  - `frequentlyAdded` is optional (`false` by default): `true` offers the store first in the store picker to a user
    in one of its `countryCodes` ([[2026-10-30-frequently-added-stores]], [[add-card]]).
- **Loading:** `CatalogueDatabaseCallback.onOpen`
  (`app/src/main/java/com/bdaniel/loyscan/data/local/db/CatalogueDatabaseCallback.kt`), registered first in
  `DatabaseModule.provideAppDatabase`. `CatalogueEntry.toStoreEntity` (`data/mapper/CatalogueMapper.kt`) builds the
  row (`logo = null`, `isCustom = false`).
- **Raw rows:** callbacks get a `SupportSQLiteDatabase`, not DAOs, so they write `StoreEntity.toContentValues()` /
  `LoyaltyCardEntity.toContentValues()` (`data/local/db/ContentValues.kt`). These convert each column the way Room
  does, and `ContentValuesTest` reads the rows back through the DAOs to prove it.

## Behaviour

- **Runs on every open, synchronously, in one transaction.** Room calls `onOpen` while opening the database, before
  the first query returns. A catalogue store's row therefore always has the shipped name, colour, aliases, formats
  and card-number rule when it is read. This includes a row whose old stored rule no longer parses (strict decoding
  would throw, [[2026-10-12-data-model-v2]]): it is overwritten before anything decodes it.
- **Matched by slug id:** a missing row is inserted, and a row whose columns differ is updated in place. `updatedAt`
  is set to the current time only when a row really changes, so unchanged rows keep theirs for merging (#19, #24).
- **Never `REPLACE`:** `INSERT OR REPLACE` deletes the old row first, and `ON DELETE CASCADE` would then delete its
  cards. SQLite's `ON CONFLICT … DO UPDATE` needs SQLite 3.24 (API 30, minSdk is 29), so the code reads the row,
  then runs `insert` or `update`.
- **Leaves other stores alone:** custom stores, the former debug demo stores ([[seed-data]]) and entries dropped from the
  catalogue are not touched. A dropped entry's cards keep working with the last version of its row.
- **A broken catalogue fails loudly:** a parse error is logged with Timber and rethrown, so the first query
  throws and nothing is written. `ShippedCatalogueTest` loads the real asset on
  every build, so a broken catalogue fails the build, not a device.
- The asset is read and parsed on every open. It is small (tens of entries). If it grows a lot, store a hash of
  the asset and skip unchanged loads.

## Contents and known gaps

- **Frequently added in Hungary** (`frequentlyAdded`, editorial pick by the project owner, 2026-10-30: the groceries
  and drugstores): Auchan, Coop, dm, Lidl Plus (added once it shipped with #44), Penny, Rossmann, Tesco. Pinned by
  `ShippedCatalogueTest.theFrequentlyAddedHungarianStores_areTheEditorialPick`
  ([[2026-10-30-frequently-added-stores]]).
- **23 Hungarian stores, 21 with a card-number rule. Some number facts are still provisional.** 16 shipped on
  2026-10-13; seven found on real cards were added on 2026-10-30 (#44): Brendon, KiK (myKiK), Lidl Plus, Líra,
  Möbelix, Ofotért and Unix Autó. The
  retailers' public pages confirm that each programme exists, and most of the colours. The first barcode formats and
  rules came from an existing app's catalogue, which the user accepted for the first entries
  ([[2026-10-13-curated-store-catalogue]]). On 2026-10-19, decoding photos of real cards confirmed Decathlon, dm,
  IKEA Family, mömax, Penny and Rossmann, and added Decathlon's rule. On 2026-10-30 more cards and the retailers'
  own apps (a card's barcode screen counts as the retailer's material) confirmed Auchan, Coop, Douglas, Libri, MOL
  Move, mömax, Praktiker, Shell ClubSmart, SuperShop, Szerencsejáték and a second Tesco card, and added rules for
  Douglas, Libri and MOL Move. Szerencsejáték has no rule: its virtual card is Code 128, 15 digits starting `400`,
  but 10-digit plastic cards may also exist. Per-store status, with what each card showed:
  [#31](https://github.com/shockrifle/loyscan/issues/31).
- **The core is the full printed number.** EAN-13 stores use 13 digits with a `GTIN_MOD10` `VERIFY` check and no
  prefix, because the card prints all 13 digits. Since 2026-10-19 the stores whose leading digits were seen on a real
  card require them (`coreStartsWith`): dm `241`, Penny `994`, Rossmann `999`, IKEA Family `627598042`, since
  2026-10-20 Tesco `634009`, and since 2026-10-30 Auchan `2097`, Decathlon `2090`/`2091` (it was `209`, which
  also read Auchan's `2097…`), Douglas `6008`, Libri `202`/`204` (`202` on a card, `204` on a number stored in another
  app), MOL Move `6369`, mömax `312`, Praktiker `360`, Shell ClubSmart `7004` (a card showed `700434`, the first
  catalogue said `700442`) and SuperShop `20000101` (from the SuperShop FAQ,
  [supershop.hu/ugyfelszolgalat](https://supershop.hu/ugyfelszolgalat); the first catalogue's `5540` was wrong). One
  card per store, so a rule requires the first three or four digits seen, or the issuer prefix where the number looks
  like an ISO card number. Every rule except Coop's (whose barcode starts with `90`) now requires a start, so no
  rule reads another store's scan (`ShippedCatalogueTest.storesWithConfirmedLeadingDigits_rejectOtherStoresCards`,
  [#52](https://github.com/shockrifle/loyscan/issues/52)). Other starts that the first catalogue listed (Penny
  `9980`/`9981`, dm `90`, Rossmann `2`) were left out until a card shows them. A card from a number range not seen
  yet is flagged when typed; "Save anyway" still saves it.
- **ISO card numbers carry a Luhn check digit.** Coop, Douglas, IKEA Family, MOL Move, Shell ClubSmart and Tesco print
  numbers that start with an issuer prefix (`6…`, `7…`) and pass Luhn, so their rules verify it. SuperShop's card
  passes Luhn too, but the FAQ doesn't say it has one, so its rule doesn't.
- **The stores added from real cards (#44, 2026-10-30)** each come from one card, and require the start seen on it:
  Brendon EAN-8 `167` (GTIN check), KiK QR 14 digits `985`, Lidl Plus QR 17 digits `7736`, Möbelix ITF 10 digits
  `112` (apart from mömax's `312`), Unix Autó Code 39 `H` + 6 digits. Líra prints a 7-digit serial and its EAN-13
  barcode is `00000` + the serial + a GTIN check digit, so its rule has `prefix` and an `APPEND` check digit.
  **Ofotért has no rule:** its card prints 16 digits (`1001` + `0000` + 8 digits) but the EAN-13 barcode holds `1001` +
  the last 8 + a check digit; a rule can only drop leading digits, not the zeros in the middle. A scanned Ofotért card
  works; a typed one is the barcode's 13 digits. Colours are from each retailer's site CSS (Brendon's pink, KiK,
  Lidl, Möbelix's `theme-color`, Ofotért), or from the logo where the site has no brand colour of its own (Unix Autó's
  SVG, Líra's PNG). **Unix Autó's programme (Autókártya) isn't mentioned on unixauto.hu or in its app**; it was added
  on the project owner's decision because the card exists.
- **SuperShop is drawn as a QR code** (since 2026-10-30): SuperShop's own app shows the 16-digit number as a QR code,
  and its old chip cards have no barcode.
- **Tesco's barcode leaves out the first six printed digits.** The Clubcard prints 18 digits starting `634009`, with a
  Luhn check digit, and its barcode is GS1-128 holding `9799` + the last 12 of them (one card seen, 2026-10-19, #31).
  `hu-tesco`'s rule (since 2026-10-20, #43) builds that from the typed number with `dropLeading`, and a scan reads back
  as the printed number ([[2026-10-09-card-number-rule-engine]]). Unconfirmed, both from that one card: whether `99`
  after AI `97` and the start `634009` are the same on every card. With a different `99` pair, a scan doesn't match
  `hu-tesco`, and a typed card validates but is drawn with `9799`, which isn't its barcode. With a different start,
  the barcode doesn't hold it, so a scan gets `634009` put back, and when the difference is two digits or more the
  Luhn check can still pass (about 1 in 10). The card then shows a printed number that isn't the one on it, though its
  barcode is still right.
- **Coop is catalogued as plain Code 128.** Its barcode starts with Application Identifier `90`, and a till that parses
  GS1 may expect the leading FNC1 of GS1-128 (`BarcodeFormat.GS1_128`), which a typed Coop card is drawn without. A
  scan marked GS1 is stored and drawn as `GS1_128` whatever the catalogue says. Coop's own app draws the same plain
  Code 128 (2026-10-30), so it stays plain Code 128; no till test was made: [[gs1-128-drawn-as-code-128]].

## Tests

- `domain/catalogue/StoreCatalogueTest`: format and validation.
- `data/local/db/CatalogueDatabaseCallbackTest`: an on-disk database is closed and reopened with a changed
  catalogue. It checks that a first open inserts every entry, that a changed entry is rewritten before it is read
  and keeps its cards, that a stored rule that no longer parses is rewritten, that `updatedAt` is unchanged when
  nothing changed, that other stores are left alone, and that an invalid catalogue writes nothing.
- `data/local/db/ShippedCatalogueTest`: the real asset loads, and each store's rule has a test vector (printed number
  → barcode content and display number, and the scan matches back).
- `data/local/db/ContentValuesTest`, `data/mapper/CatalogueMapperTest`.

Room opens a database lazily. In a reopen test, run a query on the first instance, or the "first" open never
happens (this cost a debugging round when `reopen_unchangedEntry_keepsItsUpdatedAt` was written).
