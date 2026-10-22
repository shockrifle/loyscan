# No Room migration strategy

**Status:** Open · **Issue:** [#12](https://github.com/shockrifle/loyscan/issues/12) · **Since:** 2025-12-08 (`4969a2b`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/di/DatabaseModule.kt`, `provideAppDatabase`,
`app/src/main/java/com/bdaniel/loyscan/data/local/db/AppDatabase.kt:11`

## What
`version = 2`, with no `addMigrations`, no `@AutoMigration`, and no `fallbackToDestructiveMigration`. The schema
JSON for v1 was rewritten in place by `4cbc71d` (column renames) without a version bump. v2 (#1, 2026-10-12)
rebuilt both tables with no v1 → v2 migration, so a dev install from before #1 crashes on open until it is wiped.

## Why it exists
Pre-release development; nothing has shipped, so wiping the DB is free.

## Risk / impact
The first entity change after any real install crashes the app on launch for those users. During development
it's a recurring crash after schema edits — [[reset-local-database]].

## How to pay it down
Before the first release: freeze the current schema export (`2.json` as of 2026-10-22; `1.json` is the record of
the unreleased v1, [[2026-10-12-data-model-v2]]), and from then on bump the version per change with `@AutoMigration` or
explicit `Migration`s (the schemas are already exported to `app/schemas/`). Procedure: [[database-schema-changes]]. (Corrected 2026-10-22: this
said to freeze `1.json`, which predates the v2 bump.)
