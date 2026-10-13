# Line-based SQL seed "parser"

**Status:** Removed (2026-10-13, #18) · **Issue:** [#18](https://github.com/shockrifle/loyscan/issues/18) · **Since:** 2025-12-09 (`01e3480`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/data/local/db/SeedDatabaseCallback.kt` (`onCreate`)

## What
The seed loader reads `loyscan.sql` line by line, trims each line, skips empty lines and lines starting with `--`,
and calls `db.execSQL(line)` on everything else. It runs on a fresh `Executors.newSingleThreadExecutor()` that is
never shut down.

## Why it exists
It's the minimum needed to run a hand-written INSERT script ([[2025-12-09-seed-db-from-sql-asset]]).

## Risk / impact
- A statement split across lines → each fragment is executed separately → `SQLiteException` on the executor
  thread → the seed silently stops partway (no logging, nothing awaits it).
- A trailing comment (`INSERT …; -- note`) is passed to `execSQL`. SQLite generally tolerates a trailing comment,
  but anything after a second `;` is not executed.
- The executor thread object leaks (a one-off; harmless in practice).

Since #30, `SeedDatabaseCallbackTest` runs the shipped script through this loader on every build, so a
multi-line statement in `loyscan.sql` fails the build instead of a device. The loader's own flaws remain.

## How to pay it down
Run the inserts inside `db.beginTransaction()` / `setTransactionSuccessful()` and log failures with Timber. Or
replace the script with a `createFromAsset` prepackaged DB or a Kotlin seeder once data is no longer
hand-edited. Until then: **one statement per line** — [[seed-data]].

## Removed
2026-10-13 (#18): `loyscan.sql` and `SeedDatabaseCallback` were deleted. Catalogue stores are loaded from JSON in one
transaction, with logging, on every open ([[store-catalogue]]). The demo cards became debug-only Kotlin, written in a
transaction on creation ([[seed-data]]). Both run synchronously inside Room's open, so the executor is gone too.
