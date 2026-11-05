# Reset the local database

**Use when:**
- the app crashes on launch after an entity change, with Room's `IllegalStateException` ("Room cannot verify
  the data integrity…") or a missing-migration error ([[database-schema-changes]]);
- a debug install from before #55 still has the demo stores and cards ([[seed-data]]).

Catalogue changes don't need a reset: they apply on the next open ([[store-catalogue]]).

## Steps

```sh
# 1. Wipe app data (DB, prefs, cache). The next launch recreates loyscan.db with the catalogue stores only.
adb shell pm clear com.bdaniel.loyscan

# 2. Relaunch
adb shell am start -n com.bdaniel.loyscan/.MainActivity

# 3. If the app crashes on open, look for the catalogue load failure ("Loading the store catalogue failed")
adb logcat -d | grep -iE "sqlite|room|loyscan|catalogue" | tail -50
```

Alternative: uninstall (`adb uninstall com.bdaniel.loyscan`), then `./gradlew installDebug`.

**Caution:** `allowBackup="true"` is set, but since #71 cloud backup carries no app data, so a *cloud* restore can no
longer bring back an old `loyscan.db` on reinstall or first install ([[2026-11-05-no-cloud-backup-in-v1]]; before #71
this crashed a first install from an earlier debug build's backup — seen 2026-11-03, [[android-app]] → Manifest facts).
A `loyscan.db` from another build can still arrive by **device-to-device transfer**, which still carries it (and an
upgrade across a schema change from a dev build that changed the schema without a bump; released schemas are frozen
and migrated since #12, [[2026-11-05-room-schema-frozen-from-v2]]). If a wiped-then-reinstalled app still opens an
unexpected database, `pm clear` avoids it; `adb logcat -d | grep restoreAtInstall` confirms no cloud restore ran.

## If the catalogue itself is broken

A catalogue that doesn't parse fails `ShippedCatalogueTest`, so it should never reach a device. If it does, fix the
entry ([[add-a-catalogue-store]]). A reset won't help, because the catalogue loads on every open.
