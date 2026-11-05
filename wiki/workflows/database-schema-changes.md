# Changing the database schema

The Room DB `AppDatabase` is at **`AppDatabase.VERSION = 2`**, and **every version from 2 on is frozen**
([[2026-11-05-room-schema-frozen-from-v2]], #12): a schema change is always a version bump with a migration. There is
no destructive fallback, so a missing migration crashes on open rather than deleting the user's cards
(`app/src/main/java/com/bdaniel/loyscan/data/local/db/AppDatabase.kt`, `companion object`;
`app/src/main/java/com/bdaniel/loyscan/di/DatabaseModule.kt`, `provideAppDatabase`).

Two tests enforce it. `FrozenSchemaTest` fails when a pinned schema JSON changes (KSP rewrites it from the entities on
every build) or a version has no pin. `DatabaseMigrationTest` opens a v2 database with a card through the app's builder
and fails on a missing, unwired or wrong migration, or a lost card.

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
   `FrozenSchemaTest` says a schema "changed in place", the entity change needs a bump, not a new hash.
4. Write the migration: a `Migration(old, new)` in `AppDatabase.MIGRATIONS`, or an `@AutoMigration(from, to)` in
   `@Database` when Room can work it out (it needs the exported schemas, which are committed). Room checks the
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
