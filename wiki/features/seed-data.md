# Demo data (removed)

**Removed 2026-10-22** ([#55](https://github.com/shockrifle/loyscan/issues/55)). A new database, in debug and release
builds alike, holds the catalogue stores ([[store-catalogue]]) and no cards. Cards come only from the add-card flow
([[add-card]]); they are edited and deleted from the card's detail screen ([[edit-card]], [[card-detail]]). Test:
`DatabaseModuleTest.aNewDatabase_holdsTheCatalogueStores_andNoCards` (`app/src/test/java/com/bdaniel/loyscan/di/`),
which builds the database as the app does.

## What it was

Until #55, a debug build that created `loyscan.db` inserted 5 demo stores (`demo-a` … `demo-e`, "Brand A" … with
`picsum.photos` placeholder logos) and 11 demo cards, through `DemoSeedCallback.onCreate` in the debug source set,
registered by a `buildTypeDatabaseCallbacks()` function that the release source set defined as empty. It was the only
way to get cards into a debug build before #6. Each demo card had a `scannedFormat`, several of them numbers their
format can't hold, so the set showed the barcode renderer's fallbacks (#4); `BarcodeEncoderTest` and
`DetailScreenTest` cover those cases instead. Before #18 the demo data was a SQL script
([[line-based-sql-seed-parser]], [[2025-12-09-seed-db-from-sql-asset]]).

## On an existing debug install

The seed ran only when the database was created, so an install made before #55 keeps the demo stores and cards. The
cards can be deleted in the app, the stores can't: clear the app's data once ([[reset-local-database]]).
