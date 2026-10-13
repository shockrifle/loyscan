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
    ([[2026-10-09-card-number-rule-engine]]).
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
- **Leaves other stores alone:** custom stores, the debug demo stores ([[seed-data]]) and entries dropped from the
  catalogue are not touched. A dropped entry's cards keep working with the last version of its row.
- **A broken catalogue fails loudly:** a parse error is logged with Timber and rethrown, so the first query
  throws and nothing is written. `ShippedCatalogueTest` loads the real asset on
  every build, so a broken catalogue fails the build, not a device.
- The asset is read and parsed on every open. It is small (tens of entries). If it grows a lot, store a hash of
  the asset and skip unchanged loads.

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
