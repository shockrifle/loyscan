# Test backup and device-to-device transfer on one phone

Checks what Android's backup actually carries for LoyScan, on a single phone, without touching the user's own install
or cards. It uses the `smoke` build (`com.bdaniel.loyscan.smoke`, same backup rules, separate data; [[build-and-run]])
and the platform's local backup transport, which can pretend to be a device-to-device (D2D) transfer. First run
2026-11-12 for #71/#104; the result is in [[2026-11-05-no-cloud-backup-in-v1]] → Consequences.

## Steps

```sh
P=com.bdaniel.loyscan.smoke
./gradlew installSmoke                      # then add a card with a photo and change the sort order in the app
adb shell bmgr transport                    # note the selected transport (the one marked *) to put back later
adb shell settings put secure backup_local_transport_parameters is_device_transfer=true   # omit for a cloud-mode run
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr backupnow --non-incremental $P
adb shell bmgr backupnow @pm@               # the package metadata a restore needs
adb shell am force-stop $P && adb shell pm clear $P
adb shell bmgr restore 1 $P                 # 1 = the local transport's only set (`bmgr list sets`: "Local disk image")
# open the app: the card, its photo and the sort order should be back
```

`adb logcat -d | grep "file_backup_helper:    Name:"` lists every file the backup took. Afterwards:
`adb shell settings delete secure backup_local_transport_parameters`, `adb shell bmgr transport <the noted one>`,
`adb uninstall $P`. The local transport keeps its set after that; the next run's backup overwrites it, so always
back up before restoring.

## Traps

- **"Backup is not allowed"** for the package: it is in the *stopped* state (just installed, or `am force-stop`ped), and
  the platform doesn't back up a stopped app. Open the app once, then back up.
- **Restore reports success but the app is empty**, with `No metadata for <package>` in logcat: the restore set has no
  `@pm@` record for it. Back up `@pm@` (above) and restore again; the app's data from the earlier backup is still there.
- **A card shows no photo after the restore** but `card_photos/` was in the backup: check the photo was saved on the
  card before the backup (its tile shows the picture). A picked photo that was never saved stays in `card_photos/`
  until the orphan sweep ([[card-photos]], #95), so it is backed up without any card naming it.
- Without `is_device_transfer=true` the run is a cloud backup: the transport rejects the package (`Error -1002`) and
  nothing is backed up, which is what the rules intend.
- `run-as` doesn't work on the smoke build (not debuggable), so check the result in the app, not on disk.
