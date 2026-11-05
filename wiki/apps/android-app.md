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
- `android.permission.CAMERA` for the [[barcode-scanner]], asked for at runtime. Declaring it also makes Android refuse
  `ACTION_IMAGE_CAPTURE` until it is granted, so taking a card photo with the camera app asks for it too ([[card-photos]]).
  A `<queries>` entry for `android.media.action.IMAGE_CAPTURE` lets the app see a camera app on Android 11+ (#10). `android.hardware.camera.any` is
  declared `required="false"`: cards can be entered by hand, so phones without a camera can install the app.
- A `FileProvider` (`androidx.core.content.FileProvider`, authority `${applicationId}.photos`, not exported, per-Uri
  grants) hands the camera app a file in `files/card_photos/` to write a card photo into (`res/xml/card_photo_paths.xml`,
  [[card-photos]], #10, `CardPhotosModuleTest`).
- A non-exported `<receiver>` `.widget.glance.CardWidgetReceiver` with the `APPWIDGET_UPDATE` filter and an
  `android.appwidget.provider` meta-data (`res/xml/card_widget_info.xml`) is the home-screen widget
  ([[card-widget]], #11); its `onUpdate` starts the live-update observer. `MainActivity` also reads an open-card
  intent (`openCardIntent` / `Intent.openCard()`) the widget fires.
- Native code: zxing-cpp ships `.so` files for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64` ([[zxing-cpp]]).
- `allowBackup="true"`, but **cloud backup carries no app data** ([[2026-11-05-no-cloud-backup-in-v1]], #71,
  `BackupRulesTest`). `data_extraction_rules.xml` (Android 12+) excludes every domain (`root`/`file`/`database`/
  `sharedpref`/`external` and their `device_*` counterparts) from `<cloud-backup>` and has no `<device-transfer>`
  section, so device-to-device transfer still carries everything. `backup_rules.xml` (10–11, one rule set for both transfers) is include-only with every
  include gated by `requireFlags="deviceToDeviceTransfer"`, so the cloud gets nothing and a new phone gets the db,
  DataStore prefs and card photos ([[card-photos]], #10) — which the old bare exclude had kept out of D2D on 10–11.
  `allowBackup` stays `true` because `="false"` also drops D2D on some devices. Opt-in cloud backup is #24. This closed
  the 2026-11-03 install-crash path: a fresh `installDebug` had got a `loyscan.db` restored from the Google account's
  backup at install (`BackupManagerService: restoreAtInstall`), written by an older debug build with the same
  `version = 2` but another schema, and the app crashed on launch with Room's identity-hash `IllegalStateException` until
  `pm clear` ([[reset-local-database]], [[no-room-migrations]], #12).

## Theme

`LoyScanTheme` is the Android Studio template: Material 3 with dynamic color on API 31+, purple fallback palette
on API 29–30 (`app/src/main/java/com/bdaniel/loyscan/ui/theme/Theme.kt:36-58`). Card colors come from brand
data, not the theme. Icons are hand-written `ImageVector`s — [[2025-12-08-hand-rolled-icons]].

## Build types

- `debug`: the everyday build; the JVM tests run on it.
- `release`: R8 on (`isMinifyEnabled`, `isShrinkResources`, #73). It's unsigned until #74. Libraries ship their own
  keep rules. `app/proguard-rules.pro` adds only `-keepnames enum com.bdaniel.loyscan.domain.BarcodeFormat`, since
  Navigation looks up an enum route argument's class by name. `RouteKeepRulesTest` checks every route's enum
  arguments against that file.
- `smoke`: `initWith(release)`, signed with the debug key, applicationId `com.bdaniel.loyscan.smoke`, so it installs
  beside a real install. It's `testBuildType`, for the on-device `MinifiedAppSmokeTest`. It also keeps `kotlin.**`
  and `androidx.tracing.**`, which the test runner takes from the app (`app/proguard-smoke-rules.pro`). Not shipped.

Why it's set up this way: [[2026-11-05-r8-smoke-test-on-a-device]].
`buildConfig = true` exists for `BuildConfig.DEBUG` (the Timber gate). See [[build-and-run]].
