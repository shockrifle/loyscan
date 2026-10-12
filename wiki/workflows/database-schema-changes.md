# Changing the database schema

The Room DB `AppDatabase` is at **`version = 2`**
(`app/src/main/java/com/bdaniel/loyscan/data/local/db/AppDatabase.kt:10`) with **no migrations and no
`fallbackToDestructiveMigration`** (`app/src/main/java/com/bdaniel/loyscan/di/DatabaseModule.kt`, `provideAppDatabase`). Any
entity change on a device that already has `loyscan.db` crashes on open until the version is bumped and a
migration (or a wipe) exists.

## Steps

1. Change the entity in `data/local/db/entity/` and the DAO queries. A new column type that Room can't store
   needs a `@TypeConverter` in `data/local/db/Converters.kt`. The card's store is loaded with `@Relation`, so no
   query aliases store columns by hand.
2. Bump `version` in `@Database`, then build. KSP writes
   `app/schemas/com.bdaniel.loyscan.data.local.db.AppDatabase/<version>.json`. **Commit the new schema JSON**
   with the change.
3. Choose one:
   - *Pre-release / dev:* uninstall or clear data ([[reset-local-database]]).
   - *Real users:* add `.addMigrations(...)` or a Room `@AutoMigration(from, to)` (which needs the exported
     schemas). See [[no-room-migrations]].
4. Update `app/src/main/assets/database/loyscan.sql` to the new column names — [[seed-data]].
5. Update the domain model and mapper if the field is user-visible.

## History

- `4969a2b` (2025-12-08) created schema v1. `4cbc71d` later renamed `cardNumber`→`code` and
  `barcodeType`→`barcodeFormat` and rewrote `1.json` **without** bumping the version. That was fine only
  because nothing had been released.
- 2026-10-12 (#1): v2 replaced `brands` with `stores` and rebuilt `loyalty_cards` (string ids, new fields;
  [[2026-10-12-data-model-v2]]). The version was bumped and `1.json` kept unchanged. No migration from v1:
  nothing had been released, so a dev install has to be wiped ([[reset-local-database]]).
