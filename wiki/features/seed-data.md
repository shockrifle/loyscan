# Seed (demo) data

When `loyscan.db` is first created, the app inserts 5 demo stores and 11 loyalty cards from
`app/src/main/assets/database/loyscan.sql`. This is the only way data enters the app today — there is no
add-card UI.

**Entry point:** `SeedDatabaseCallback.onCreate`
(`app/src/main/java/com/bdaniel/loyscan/data/local/db/SeedDatabaseCallback.kt`), registered in `DatabaseModule`.
Added in `01e3480`; moved out of `DatabaseModule` into its own class in #30 so a test can run it.

**Guarded by** `SeedDatabaseCallbackTest`: it runs the real script through the real loader into an in-memory
database and checks the row counts and one full row. A statement the loader can't execute fails the build.

## Contract of `loyscan.sql`

- **One complete statement per line.** The loader reads line by line, trims, skips blank lines and lines
  starting with `--`, and `execSQL`s each remaining line (`SeedDatabaseCallback.onCreate`). Multi-line statements or
  trailing `-- comments` break it — [[line-based-sql-seed-parser]].
- If you change the script's row counts or card `…000000000005`, update `SeedDatabaseCallbackTest`.
- It must use the **current** schema (v2, `app/schemas/…/2.json`): every column of `stores` and `loyalty_cards`
  is listed in each `INSERT`. The script is not versioned with the schema.
- Values are written the way `Converters` stores them: lists as JSON text (`'["HU"]'`), timestamps as epoch
  milliseconds, `BarcodeFormat` as the enum name, no rule as `NULL` ([[2026-10-12-data-model-v2]]).
- Explicit ids line up the `storeId` foreign keys: stores are `demo-a` … `demo-e`, cards are UUIDs
  `00000000-0000-4000-8000-0000000000NN`. The card's old per-card barcode format is kept as `scannedFormat`, so
  the demo set still covers many formats.
- Store logos point at `picsum.photos` placeholder images (since `c4d6bdc`) — [[coil-image-loading]].
- The demo seed ships in every build until the curated catalogue ([#3](https://github.com/shockrifle/loyscan/issues/3))
  replaces it, which is the last open criterion of [#1](https://github.com/shockrifle/loyscan/issues/1).

## Behavior to know

- It runs **once**, only when the DB file is created. Editing the SQL does nothing on an existing install —
  [[reset-local-database]].
- It runs on a new single-thread executor and is not awaited, so the first query can race the seed. I haven't
  verified whether Room's invalidation tracker notices inserts made through the callback's raw
  `SupportSQLiteDatabase`. If the list ever comes up empty on first launch, start there.
- It ships in release builds too (there's no build-type split).
