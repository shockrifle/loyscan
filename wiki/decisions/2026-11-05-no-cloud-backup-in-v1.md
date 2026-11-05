# No cloud backup in v1; device-to-device transfer only

**Status:** Active · **Date:** 2026-11-05 · **Commits:** (this change) · **Issue:** [#71](https://github.com/shockrifle/loyscan/issues/71)

## Context
The manifest shipped the Android Studio template: `allowBackup="true"` with backup rules that excluded only
`files/card_photos/` ([[card-photos]], #10). So everything else — the `loyscan.db`, the DataStore preferences — went
to the user's Google account unasked, and was restored at install. Seen on a device on 2026-11-03: a fresh
`installDebug` got a `loyscan.db` restored from the Google backup (`BackupManagerService: restoreAtInstall`), written
by an older build with the same `version = 2` but a different schema, and the app crashed on launch with Room's
identity-hash `IllegalStateException` until `pm clear` ([[reset-local-database]], [[no-room-migrations]], #12). Sending
a user's loyalty-card data to their cloud account silently also contradicts the opt-in design of #24.

## Options considered
1. **`allowBackup="false"`.** Turns off cloud backup, but on Android 12+ it also disables device-to-device transfer on
   some manufacturers' devices (the docs only *guarantee* D2D survives it on "some device manufacturers"), so a new
   phone could arrive with no cards. Rejected: we want to keep D2D.
2. **Keep cloud backup, add a `BackupAgent` to version the data.** Much more code, and still sends card data to the
   cloud unasked. Out of scope for v1; that is the opt-in backup of #24.
3. **Keep `allowBackup="true"`, exclude all app data from cloud backup via the rules, keep D2D.** Chosen.

## Decision
Keep `allowBackup="true"` and express "nothing to the cloud, everything to a new phone" in the two rules files. The
app targets API 36, so Android 12+ reads `data_extraction_rules.xml` and Android 10–11 (minSdk 29) reads
`backup_rules.xml`, and the two formats say it differently:

- **12+ (`data_extraction_rules.xml`):** `<cloud-backup>` and `<device-transfer>` are independent sections. Exclude
  every data domain (`file`, `database`, `sharedpref`, `external` at `path="."`) from `<cloud-backup>` → the cloud
  backup is empty. Omit `<device-transfer>` entirely → D2D is "fully enabled for all content", so it carries the db,
  the DataStore prefs (in the `file` domain) and the photos.
- **10–11 (`backup_rules.xml`):** one rule set serves both cloud and D2D. Adding any `<include>` makes the file
  include-only (the default "back up everything" is dropped). Gate every include with
  `requireFlags="deviceToDeviceTransfer"` → the cloud transfer, which doesn't set that flag, gets nothing; a new phone
  (flag set) gets `file` + `database` + `sharedpref` + `external`. This also *fixes* photo transfer on 10–11, which the
  old bare `<exclude>` blocked.

`BackupRulesTest` (Robolectric) parses both resources and asserts: cloud-backup excludes every data domain and
includes nothing; the legacy file is include-only with every include D2D-gated; and D2D carries `file` + `database`.

## Why
Loyalty-card data is the user's; it shouldn't leave the device to a third party (Google) without the user choosing it
(opt-in backup is #24). Keeping D2D means moving to a new phone over the cable/wireless setup transfer still brings the
cards. Keeping `allowBackup="true"` rather than `false` is the only reliable way to keep D2D while cloud backup is off,
because `allowBackup="false"` is documented to also drop D2D on some devices.

## Consequences
- A `loyscan.db` restored from an old cloud backup can no longer crash a fresh install on a mismatched schema; that was
  the 2026-11-03 incident. The Room migration strategy (#12) is still owed for *in-place upgrades*, but cloud restore
  is no longer a way to hit it.
- Nothing in the app is in cloud backup now. When opt-in backup (#24) lands, it will need to re-include data
  deliberately (and handle the 25 MB quota and schema versioning).
- On-device confirmation is still owed by #71's field criteria: `adb shell bmgr` shows an empty cloud backup, and a
  real device-to-device transfer carries the cards and photos. The rules' correctness is pinned by `BackupRulesTest`;
  the device checks confirm the platform honours them.
