# DataStore for user preferences

**Status:** Active · **Date:** 2026-10-28 · **Issue:** [#7](https://github.com/shockrifle/loyscan/issues/7) (sort) · **Commits:** a5cc709, 3a5c2c0

## Context

The card list's sort choice must survive a restart (#7). It is the app's first user preference: one small value,
not a card or a store, so it has no place in the Room database.

## Options considered

1. **Jetpack DataStore (Preferences)**, `androidx.datastore:datastore-preferences`: a key-value file read as a
   `Flow`, written with a suspending `edit`.
2. **`SharedPreferences`**: no new dependency, but synchronous reads on the main thread, no `Flow`, and writes
   that can be lost with `apply()`.
3. **A Room table**: a schema change (and, once [[no-room-migrations]] is paid down, a migration) for one value.

## Decision

Option 1. `UserPreferencesRepository` (`app/src/main/java/com/bdaniel/loyscan/data/repository/UserPreferencesRepository.kt`)
is the interface ViewModels use; `DataStoreUserPreferencesRepository` implements it over one file,
`files/datastore/user_preferences.preferences_pb`, provided by `di/PreferencesModule.kt`.

- Values are stored by enum name. A value this version doesn't know (a later version's, after a downgrade, or a
  renamed entry) reads as the default.
- A corrupt file is replaced with empty preferences (`ReplaceFileCorruptionHandler`), and a read `IOException`
  reads as the defaults (`catch` on `data`), so the start screen never fails on its preferences. The flow ends
  there: until the list re-subscribes (back after 5 s, or a new ViewModel), it stays on the defaults, even if a
  later write succeeds. Accepted for a rare disk error. Other exceptions are
  bugs and propagate (`DataStoreUserPreferencesRepositoryTest.anErrorThatIsNotAReadError_isNotHidden`).
- A failed write throws from `setCardSort`; the caller decides. `ListViewModel.onSortChange` logs it and the list
  keeps the stored sort (`ListViewModelTest.aSortThatFailsToSave_leavesTheSortAsItWas`).

## Why

It is the Jetpack-recommended store for small preferences and fits the app's Flow-based layering
([[architecture]]): the ViewModel combines it with the cards like any repository flow. The file name is pinned
by `PreferencesModuleTest`: renaming it loses every user's choices.

## Consequences

- One store per file per process: DataStore throws when a second instance opens a file another still has open.
  The module provides it as a `@Singleton`; `@HiltAndroidTest`s replace the module with
  `testing/TemporaryPreferencesModule`, a new file per test ([[tdd]]).
- On a Windows host, DataStore's file storage can't replace an existing file (`Unable to rename …`), so a JVM test
  writes each file at most once. Replacing a corrupt file is such a second write; that test runs on CI (Linux) and is
  skipped on Windows (`DataStoreUserPreferencesRepositoryTest.aCorruptFile_readsAsTheDefaults`). Android is not
  affected.
- Opt-in backup (#24) will have to decide whether this file is backed up with the cards.
