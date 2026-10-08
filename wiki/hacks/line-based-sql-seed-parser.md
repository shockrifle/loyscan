# Line-based SQL seed "parser"

**Status:** Active · **Since:** 2025-12-09 (`01e3480`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/di/DatabaseModule.kt:29-37`

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

## How to pay it down
Run the inserts inside `db.beginTransaction()` / `setTransactionSuccessful()` and log failures with Timber. Or
replace the script with a `createFromAsset` prepackaged DB or a Kotlin seeder once data is no longer
hand-edited. Until then: **one statement per line** — [[seed-data]].
