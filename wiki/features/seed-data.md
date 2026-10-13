# Demo data (debug builds only)

When a **debug** build first creates `loyscan.db`, it inserts 5 demo stores and 11 demo cards. Release builds
insert none: they start with only the catalogue stores ([[store-catalogue]]) and no cards. Until the add-card flow
(#6) exists, this is the only way cards get into a debug build.

**Entry point:** `DemoSeedCallback.onCreate`
(`app/src/debug/java/com/bdaniel/loyscan/data/local/db/DemoSeedCallback.kt`). It is registered through
`buildTypeDatabaseCallbacks()`, a function that exists in both `app/src/debug/java/com/bdaniel/loyscan/di/` and
`app/src/release/java/com/bdaniel/loyscan/di/`. The release version returns an empty list.

**Guarded by** `DemoSeedCallbackTest` (`app/src/testDebug/…`), which seeds an in-memory database next to the real
catalogue loader and checks the row counts and one full row.

## The data

- The rows are Kotlin `StoreEntity` / `LoyaltyCardEntity` values. They are written in one transaction through
  `toContentValues()` ([[store-catalogue]] → Raw rows). They used to be a SQL script run line by line
  ([[line-based-sql-seed-parser]], removed in #18).
- The stores are `demo-a` … `demo-e` ("Brand A" …). They are not catalogue stores, and the catalogue loader leaves them
  alone. Their logos point at `picsum.photos` placeholder images ([[coil-image-loading]]).
- Card ids are UUIDs `00000000-0000-4000-8000-0000000000NN`. Each card has a `scannedFormat`, so the demo set covers
  many formats. These are **not real scans**: several numbers can't be encoded in their format (e.g. 9 digits as
  `EAN_13`), so they exercise the renderer's fallback (#4).

## Behavior to know

- It runs **once**, when the database file is created, synchronously inside Room's open. Changing the demo data does
  nothing on an existing install. See [[reset-local-database]].
- If you change the counts or card `…000000000005`, update `DemoSeedCallbackTest`.
