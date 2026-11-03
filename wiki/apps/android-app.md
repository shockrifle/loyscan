# Android app (`:app`)

The only runtime surface: a single-Activity Compose app, package/applicationId `com.bdaniel.loyscan`,
versionName `1.0` (versionCode 1). minSdk 29, target/compileSdk 36, Java/JVM 21 (`app/build.gradle.kts`).

## Entry points

- `LoyScanApplication` (`@HiltAndroidApp`) — plants Timber `DebugTree` in debug builds only
  (`app/src/main/java/com/bdaniel/loyscan/LoyScanApplication.kt:7-14`).
- `MainActivity` (`@AndroidEntryPoint`, launcher) — `enableEdgeToEdge()`, then
  `LoyScanTheme { Surface(background) { SharedTransitionLayout { AppNavigation() } } }` — the `Surface` is the
  only opaque app background; screens are transparent ([[card-transition]])
  (`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:44-67`).
- `AppNavigation` — opens each picture shared to the activity (`sharedPictures`, #77); holds a phone in portrait on every screen but the card (`ScreenOrientation`,
  [[2026-10-28-portrait-on-phones]]); start destination [[card-list]]; [[card-detail]], [[barcode-scanner]] and the interim scan-result
  dialog ([[scanner-result-dialog-until-add-card-flow]]).

## Manifest facts

- `android.permission.INTERNET` — added in `c4d6bdc` for brand logos ([[coil-image-loading]]).
- `MainActivity` has the default `standard` launch mode and takes `ACTION_SEND` for `image/*`: Share → LoyScan
  ([[add-card]], [[2026-11-03-share-in-standard-launch-mode]], #77, #83).
- `android.permission.CAMERA` for the [[barcode-scanner]], asked for at runtime. `android.hardware.camera.any` is
  declared `required="false"`: cards can be entered by hand, so phones without a camera can install the app.
- Native code: zxing-cpp ships `.so` files for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64` ([[zxing-cpp]]).
- `allowBackup="true"`. `data_extraction_rules.xml` (Android 12+) and `backup_rules.xml` (10–11) exclude only
  `files/card_photos/` from cloud backup ([[card-photos]], #10, `BackupRulesTest`). On 10–11 the bare exclude also keeps
  the photos out of device-to-device transfer. Transferring them there needs `<include requireFlags="deviceToDeviceTransfer">`
  rules, which make the file include-only; that is #71's to write. Everything else, `loyscan.db` included, is
  still in Android auto-backup and restored on install (#71). Seen 2026-11-03: a fresh
  `installDebug` got a `loyscan.db` restored from the Google account's backup at install (`BackupManagerService:
  restoreAtInstall`), written by an older debug build with the same `version = 2` but another schema, and the app
  crashed on launch with Room's identity-hash `IllegalStateException` until `pm clear` ([[reset-local-database]],
  [[no-room-migrations]], #71, #12).

## Theme

`LoyScanTheme` is the Android Studio template: Material 3 with dynamic color on API 31+, purple fallback palette
on API 29–30 (`app/src/main/java/com/bdaniel/loyscan/ui/theme/Theme.kt:36-58`). Card colors come from brand
data, not the theme. Icons are hand-written `ImageVector`s — [[2025-12-08-hand-rolled-icons]].

## Build types

Only `debug` and `release`; release has `isMinifyEnabled = false`, so no R8/ProGuard rules are exercised yet.
`buildConfig = true` exists for `BuildConfig.DEBUG` (the Timber gate). See [[build-and-run]].
