# Changing the database schema

The Room DB `AppDatabase` is at **`version = 1`**
(`app/src/main/java/com/bdaniel/loyscan/data/local/db/AppDatabase.kt:10`) with **no migrations and no
`fallbackToDestructiveMigration`** (`app/src/main/java/com/bdaniel/loyscan/di/DatabaseModule.kt:22-40`). Any
entity change on a device that already has `loyscan.db` crashes on open until the version is bumped and a
migration (or a wipe) exists.

## Steps

1. Change the entity in `data/local/db/entity/` and the DAO queries. The `JOIN` queries alias the brand columns
   by hand (`LoyaltyCardDao.kt:31-35`), so update them if `brands` columns change.
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

- `c67011e` (2025-12-08) created schema v1. `f9d8195` later renamed `cardNumber`→`code` and
  `barcodeType`→`barcodeFormat` and rewrote `1.json` **without** bumping the version. That was fine only
  because nothing had been released.
