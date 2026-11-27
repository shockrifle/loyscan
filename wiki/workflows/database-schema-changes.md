# Changing the database schema

The Room DB `AppDatabase` is at **`AppDatabase.VERSION = 6`**, and **every version from 2 on is frozen**
([[2026-11-05-room-schema-frozen-from-v2]], #12): a schema change is always a version bump with a migration. There is
no destructive fallback, so a missing migration crashes on open rather than deleting the user's cards
(`app/src/main/java/com/bdaniel/loyscan/data/local/db/AppDatabase.kt`, `companion object`;
`app/src/main/java/com/bdaniel/loyscan/di/DatabaseModule.kt`, `provideAppDatabase`).

Two tests enforce it. `FrozenSchemaTest` fails when a pinned schema JSON changes (KSP rewrites it from the entities on
every build) or a version has no pin. `DatabaseMigrationTest` opens a v2 database (the first release) with a card through the app's builder
and fails on a missing, unwired or wrong migration, or a lost card; a database with no migration path must fail to
open with its cards kept, so a destructive fallback fails too. Both read `app/schemas/`, which is declared an input of
the unit-test task (`app/build.gradle.kts`, `testOptions`), so editing a schema JSON reruns them.

## Steps

1. *Test first:* write the migration's test. Build the old database with
   `createDatabaseFromExportedSchema(context, "loyscan.db", <old version>)` (`testing/ExportedSchemas.kt`), insert rows
   with that version's columns written out (not the current entity's mappers), open it with
   `DatabaseModule.provideAppDatabase`, and assert the rows read back with the new column's value. See
   `DatabaseMigrationTest`.
2. Change the entity in `data/local/db/entity/` and the DAO queries. A new column type that Room can't store
   needs a `@TypeConverter` in `data/local/db/Converters.kt`. The card's store is loaded with `@Relation`, so no
   query aliases store columns by hand.
3. Bump `AppDatabase.VERSION`, then build. KSP writes
   `app/schemas/com.bdaniel.loyscan.data.local.db.AppDatabase/<version>.json`. **Commit the new schema JSON** with
   the change, and pin its `identityHash` in `FrozenSchemaTest.FROZEN_SCHEMAS`. Never edit an existing pin: if
   `FrozenSchemaTest` says a schema "changed in place", the entity change needs a bump, not a new hash. A build with
   the entity changed but the version not yet bumped rewrites the old version's JSON in place, and the migration test
   then builds its "old" database from that rewritten file and passes with no migration at all: `git checkout
   app/schemas/` before bumping (seen on #184; only `FrozenSchemaTest` was red).
4. Write the migration: a `Migration(old, new)` in `AppDatabase.MIGRATIONS`, or an `@AutoMigration(from, to)` in
   `@Database` when Room can work it out (it needs the exported schemas, which are committed). An added `NOT NULL`
   column needs `@ColumnInfo(defaultValue = …)` on the entity for an auto-migration to fill the existing rows. Room
   wires `autoMigrations` itself; `DatabaseModule` adds only `MIGRATIONS`. A column whose value must be worked out per
   row (from JSON, or from another table) needs a hand-written one: `CardBarcodeFormatMigration` (v5 → v6) adds the
   column with a placeholder default, then writes each row; an entity without `@ColumnInfo(defaultValue)` still passes
   Room's check over a column added with `DEFAULT`, since Room compares defaults only when the entity names one. Room checks the
   migrated tables against the entities when the database opens, so the test from step 1 fails on a wrong
   migration ("Migration didn't properly handle …").
5. Update the raw-row mappers in `data/local/db/ContentValues.kt` (`ContentValuesTest` fails until you do), and the
   catalogue entry format if a store column changed ([[store-catalogue]]).
6. Update the domain model and mapper if the field is user-visible.

Until the first release, a dev install can still be wiped instead of migrated ([[reset-local-database]]), but the
migration is owed either way.

## History

- `4969a2b` (2025-12-08) created schema v1. `4cbc71d` later renamed `cardNumber`→`code` and
  `barcodeType`→`barcodeFormat` and rewrote `1.json` **without** bumping the version. That was fine only
  because nothing had been released.
- 2026-10-12 (#1): v2 replaced `brands` with `stores` and rebuilt `loyalty_cards` (string ids, new fields;
  [[2026-10-12-data-model-v2]]). The version was bumped and `1.json` kept unchanged. No migration from v1:
  nothing had been released, so a dev install has to be wiped ([[reset-local-database]]).
- 2026-10-30 (#6): `stores.frequentlyAdded` was added and `2.json` rewritten **without** bumping the version (product
  choice: early development, no migrations yet; [[2026-10-30-frequently-added-stores]]). A dev install from before
  fails Room's identity check on open until its data is cleared, which deletes its cards ([[reset-local-database]]); the previous `2.json` is in git history only.
- 2026-11-05 (#12): v2 frozen as the first released schema; migrations wired into `DatabaseModule`, no destructive
  fallback; `FrozenSchemaTest` and `DatabaseMigrationTest` added ([[2026-11-05-room-schema-frozen-from-v2]]).
- 2026-11-26 (#184): v3, the first migration: `loyalty_cards.showPhotoOnList` (`NOT NULL DEFAULT 0`, so every existing
  card is off), by `@AutoMigration(from = 2, to = 3)`. Test:
  `DatabaseMigrationTest.aCardFromTheFirstRelease_withAFrontPhoto_doesNotShowItOnTheList_afterTheMigration`.
- 2026-11-26 (#185): v4, `loyalty_cards.showPhotoOnWidget` (`NOT NULL DEFAULT 0`), by `@AutoMigration(from = 3, to = 4)`;
  a v2 database migrates through v3 to v4. Tests:
  `DatabaseMigrationTest.aCardFromTheFirstRelease_withAFrontPhoto_doesNotShowItOnTheWidget_afterTheMigration` and
  `aCardShowingItsPhotoOnTheList_atVersion3_keepsIt_andDoesNotShowItOnTheWidget_afterTheMigration` (built from `3.json`).
- 2026-11-26 (#240): v5, `stores.logoStyle`, `logoTint` and `logoOutline`, all nullable (null = As is, so no default is
  needed), by `@AutoMigration(from = 4, to = 5)`; how a logo is drawn ([[store-logo]]). The catalogue sync leaves them alone
  ([[store-catalogue]]). The trap in step 3 bit again: a build before the bump rewrote `4.json` and the new test passed
  with no migration. Test: `DatabaseMigrationTest.aStoreWithALogo_atVersion4_drawsItAsIs_afterTheMigration` (built from `4.json`).
- 2026-11-27 (#187): v6, `loyalty_cards.barcodeFormat` (`NOT NULL`), the first hand-written migration,
  `CardBarcodeFormatMigration` in `app/src/main/java/com/bdaniel/loyscan/data/local/db/CardBarcodeFormatMigration.kt`
  (in `AppDatabase.MIGRATIONS`). Each card gets the type it was drawn in: a scan its `scannedFormat`, a typed number
  its store rule's `format`, else the store's `defaultBarcodeFormat` ([[2026-11-20-barcode-type-per-card]]). The rule
  is JSON, so only its `format` field is read, leniently: a rule that can't be read gives the store's default instead
  of failing the open. `5.json` was checked unchanged before the bump (the step 3 trap). Tests:
  `DatabaseMigrationTest.aTypedCardOfAStoreWithARule_atVersion5_getsTheRulesFormat_afterTheMigration`,
  `aTypedCardOfAStoreWithoutARule_atVersion5_getsTheStoresDefault_afterTheMigration`,
  `aScannedCard_atVersion5_keepsItsScannedFormat_afterTheMigration`,
  `aTypedCardWhoseStoresRuleCantBeRead_atVersion5_getsTheStoresDefault_afterTheMigration` (a catalogue store's id, so
  the catalogue sync after the migration rewrites the unreadable rule and the card reads back),
  `aTypedCardFromTheFirstRelease_getsItsStoresDefault_afterTheMigrations`.
