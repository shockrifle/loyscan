# Room schema frozen from v2; every change is a version bump with a migration

**Status:** Active · **Date:** 2026-11-05 · **Commits:** see `git log --grep "#12"` · **Issue:** [#12](https://github.com/shockrifle/loyscan/issues/12)

Schema version 2 is the first released schema. From now on the schema is never changed in place: every entity change
bumps `AppDatabase.VERSION` and ships a migration, and there is no destructive fallback. Two tests hold the line:
`FrozenSchemaTest` pins each version's exported `identityHash`, and `DatabaseMigrationTest` opens a v2 database
through the app's own builder.

## Context
Before this, the schema had been rewritten in place twice without a version bump (`4cbc71d` on `1.json`, `40b4d8e` on
`2.json`), and `AppDatabase` had no migrations and no fallback ([[no-room-migrations]]). That was free while nothing had
shipped. After v1 ships it is not free. An installed app updated by Play, or a new phone that gets `loyscan.db` by
device-to-device transfer ([[2026-11-05-no-cloud-backup-in-v1]]), would crash on Room's identity-hash check after any
schema change. The 2026-11-03 install crash was this failure ([[android-app]] → Manifest facts).

## Options considered
1. **Freeze now, at v2.** A pre-release schema change after today costs a version bump and a migration.
2. **Freeze when the release is cut.** Keeps in-place edits free until then, but relies on someone remembering, and the
   first in-place edit after the cut fails silently: KSP just rewrites the JSON.
3. **`fallbackToDestructiveMigration`.** Never crashes, but deletes every card on a missing migration. For an app
   whose only data is the user's cards, that is worse than a crash. Rejected.

## Decision
- `AppDatabase.VERSION` (a `const`, used in `@Database`) and `AppDatabase.MIGRATIONS` (hand-written `Migration`s;
  `@AutoMigration`s go in `@Database` instead) are in
  `app/src/main/java/com/bdaniel/loyscan/data/local/db/AppDatabase.kt`. `DatabaseModule.provideAppDatabase` adds
  `MIGRATIONS` and no fallback.
- `FrozenSchemaTest` (`app/src/test/java/com/bdaniel/loyscan/data/local/db/FrozenSchemaTest.kt`) maps every version
  from `FIRST_RELEASED_VERSION = 2` up to `VERSION` to its `identityHash`, and checks the exported JSON still has that
  hash. KSP rewrites the JSON from the entities on every build, so an entity change without a bump fails here, locally
  and on CI. A bump fails until its hash is pinned.
- `DatabaseMigrationTest` builds a database from `2.json` (tables, indices, Room's identity row), puts a store and a card
  in it with the v2 columns written out, and opens it with `DatabaseModule.provideAppDatabase`. The card must still be
  there. An empty database at every frozen version must open too.
- `1.json` is the record of an unreleased schema; it is not frozen and no migration from it exists.

## Why
Option 1, because the failure it guards against is silent. The in-place rewrites happened twice, and each time the
only symptom was a crash on a device with an older database. A pinned hash turns the next one into a red test at the
moment of the edit. The cost before release is a few lines of migration per schema change. Since nothing has shipped,
a dev install can still be wiped instead of migrated ([[reset-local-database]]), but the migration is owed either way.

Tested through the app's builder rather than Room's `MigrationTestHelper.runMigrationsAndValidate`. After migrating,
Room's open itself checks the tables against the entities ("Migration didn't properly handle …"). It also exercises
`addMigrations` and catches a destructive fallback, which the helper would not. The old database is built by a test
helper (`testing/ExportedSchemas.kt`) rather than `MigrationTestHelper.createDatabase`, which fails on Windows: its
`SupportSQLiteDriver` compares the database path to its name with `substringAfterLast('/')` (androidx.sqlite 2.6.2,
checked 2026-11-05). Room's own `SchemaBundle` reader is `@RestrictTo(LIBRARY_GROUP)`, so the helper reads the JSON
with kotlinx.serialization.

Checked by mutation on 2026-11-05: an added column without a bump fails `FrozenSchemaTest`; a bump without a migration,
a migration not added to the builder, and a migration that creates the wrong column type fail `DatabaseMigrationTest`.
A destructive fallback fails it too, because the card is gone.

## Consequences
- Every schema change follows [[database-schema-changes]]: bump, pin, migrate, test the migration.
- A downgrade (an older build opening a newer database) still crashes. Play doesn't downgrade, and a destructive
  fallback on downgrade would delete cards, so none is set.
- Device-to-device transfer of `loyscan.db` to a newer build is covered by the same migrations
  ([[2026-11-05-no-cloud-backup-in-v1]]).
