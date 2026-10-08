# Seed (demo) data

When `loyscan.db` is first created, the app inserts 5 brands and 11 loyalty cards from
`app/src/main/assets/database/loyscan.sql`. This is the only way data enters the app today — there is no
add-card UI.

**Entry point:** `RoomDatabase.Callback.onCreate` in
`app/src/main/java/com/bdaniel/loyscan/di/DatabaseModule.kt:26-39` (added in `01e3480`).

## Contract of `loyscan.sql`

- **One complete statement per line.** The loader reads line by line, trims, skips blank lines and lines
  starting with `--`, and `execSQL`s each remaining line (`DatabaseModule.kt:30-35`). Multi-line statements or
  trailing `-- comments` break it — [[line-based-sql-seed-parser]].
- It must use the **current** table and column names (`brands(id, name, color, logo)`,
  `loyalty_cards(id, name, brandId, code, barcodeFormat)`). The script is not versioned with the schema.
- Explicit ids are used so the `brandId` foreign keys line up.
- Brand logos point at `picsum.photos` placeholder images (since `c4d6bdc`) — [[coil-image-loading]].

## Behavior to know

- It runs **once**, only when the DB file is created. Editing the SQL does nothing on an existing install —
  [[reset-local-database]].
- It runs on a new single-thread executor and is not awaited, so the first query can race the seed. I haven't
  verified whether Room's invalidation tracker notices inserts made through the callback's raw
  `SupportSQLiteDatabase`. If the list ever comes up empty on first launch, start there.
- It ships in release builds too (there's no build-type split).
