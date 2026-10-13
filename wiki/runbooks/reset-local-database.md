# Reset the local database

**Use when:**
- the app crashes on launch after an entity change, with Room's `IllegalStateException` ("Room cannot verify
  the data integrity…") or a missing-migration error ([[database-schema-changes]]);
- you changed the debug demo cards and want them seeded again ([[seed-data]]).

Catalogue changes don't need a reset: they apply on the next open ([[store-catalogue]]).

## Steps

```sh
# 1. Wipe app data (DB, prefs, cache). The next launch recreates loyscan.db (and, in debug, the demo cards).
adb shell pm clear com.bdaniel.loyscan

# 2. Relaunch
adb shell am start -n com.bdaniel.loyscan/.MainActivity

# 3. If the app crashes on open, look for the catalogue load failure ("Loading the store catalogue failed")
adb logcat -d | grep -iE "sqlite|room|loyscan|catalogue" | tail -50
```

Alternative: uninstall (`adb uninstall com.bdaniel.loyscan`), then `./gradlew installDebug`.

**Caution:** `allowBackup="true"` is set, so after a reinstall Android may restore an old `loyscan.db` from
auto-backup. `pm clear` avoids that.

## If the catalogue itself is broken

A catalogue that doesn't parse fails `ShippedCatalogueTest`, so it should never reach a device. If it does, fix the
entry ([[add-a-catalogue-store]]). A reset won't help, because the catalogue loads on every open.
