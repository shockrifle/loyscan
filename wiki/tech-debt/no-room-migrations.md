# No Room migration strategy

**Status:** Open · **Since:** 2025-12-08 (`4969a2b`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/di/DatabaseModule.kt:22-40`,
`app/src/main/java/com/bdaniel/loyscan/data/local/db/AppDatabase.kt:10`

## What
`version = 1`, with no `addMigrations`, no `@AutoMigration`, and no `fallbackToDestructiveMigration`. The schema
JSON for v1 was rewritten in place by `4cbc71d` (column renames) without a version bump.

## Why it exists
Pre-release development; nothing has shipped, so wiping the DB is free.

## Risk / impact
The first entity change after any real install crashes the app on launch for those users. During development
it's a recurring crash after schema edits — [[reset-local-database]].

## How to pay it down
Before the first release: freeze `1.json`, and from then on bump the version per change with `@AutoMigration` or
explicit `Migration`s (the schemas are already exported to `app/schemas/`). Procedure: [[database-schema-changes]].
