# Reset the local database

**Use when:**
- the app crashes on launch after an entity change, with Room's `IllegalStateException` ("Room cannot verify
  the data integrity…") or a missing-migration error ([[database-schema-changes]]);
- you edited `loyscan.sql` and want the new seed data ([[seed-data]]);
- the card list is empty or stale and you suspect the seed failed (e.g. a bad SQL line).

## Steps

```sh
# 1. Wipe app data (DB, prefs, cache). The next launch recreates loyscan.db and re-runs the seed.
adb shell pm clear com.bdaniel.loyscan

# 2. Relaunch
adb shell am start -n com.bdaniel.loyscan/.MainActivity

# 3. If the list is still empty, look for the seed failure (an exception from execSQL on the executor thread)
adb logcat -d | grep -iE "sqlite|room|loyscan" | tail -50
```

Alternative: uninstall (`adb uninstall com.bdaniel.loyscan`), then `./gradlew installDebug`.

**Caution:** `allowBackup="true"` is set, so after a reinstall Android may restore an old `loyscan.db` from
auto-backup. `pm clear` avoids that.

## If the seed itself is broken

Check `loyscan.sql` against the one-statement-per-line rule — [[line-based-sql-seed-parser]].
