# Seed the database from a SQL asset in `RoomDatabase.Callback.onCreate`

**Status:** Active · **Date:** 2025-12-09 · **Commits:** `01e3480` (seed), `4cbc71d` (repositories replaced the
in-ViewModel dummy lists)

## Context
Until `4cbc71d`, the ViewModels held hard-coded `dummyCards` lists. Once the screens read from Room, the DB
needed demo content, and there is no add-card UI.

## Options considered
- Room `createFromAsset("database/x.db")` with a prepackaged SQLite file.
- Insert through DAOs from Kotlin (e.g. an `init` in a repository).
- Run a plain `.sql` text file in `Callback.onCreate`.

## Decision
A plain-text `app/src/main/assets/database/loyscan.sql`, executed line by line inside `Callback.onCreate` on a
background executor (`app/src/main/java/com/bdaniel/loyscan/data/local/db/SeedDatabaseCallback.kt`; it was inline in
`DatabaseModule` until #30). Details:
[[seed-data]].

## Why
Not recorded (inferred): a `.sql` file is human-editable and diffable, unlike a binary prepackaged DB, and
`createFromAsset` must match the Room schema exactly (including `room_master_table` identity), which is brittle
while the schema is still changing.

## Consequences
- The seed runs only on DB creation, and the SQL must track column renames by hand — [[database-schema-changes]].
- The ad-hoc line parser imposes formatting rules — [[line-based-sql-seed-parser]].
- The seed also ships in release builds. Revisit once real user data exists.
