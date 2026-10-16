# Android app (`:app`)

The only runtime surface: a single-Activity Compose app, package/applicationId `com.bdaniel.loyscan`,
versionName `1.0` (versionCode 1). minSdk 29, target/compileSdk 36, Java/JVM 21 (`app/build.gradle.kts`).

## Entry points

- `LoyScanApplication` (`@HiltAndroidApp`) — plants Timber `DebugTree` in debug builds only
  (`app/src/main/java/com/bdaniel/loyscan/LoyScanApplication.kt:7-14`).
- `MainActivity` (`@AndroidEntryPoint`, launcher) — `enableEdgeToEdge()`, then
  `LoyScanTheme { Surface(background) { SharedTransitionLayout { AppNavigation() } } }` — the `Surface` is the
  only opaque app background; screens are transparent ([[card-transition]])
  (`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:22-43`).
- `AppNavigation` — start destination [[card-list]]; [[card-detail]], [[barcode-scanner]] and the interim scan-result
  dialog ([[scanner-result-dialog-until-add-card-flow]]).

## Manifest facts

- `android.permission.INTERNET` — added in `c4d6bdc` for brand logos ([[coil-image-loading]]).
- `android.permission.CAMERA` for the [[barcode-scanner]], asked for at runtime. `android.hardware.camera.any` is
  declared `required="false"`: cards can be entered by hand, so phones without a camera can install the app.
- Native code: zxing-cpp ships `.so` files for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64` ([[zxing-cpp]]).
- `allowBackup="true"` with the template `backup_rules.xml` / `data_extraction_rules.xml` (unchanged since
  `00933cd`), so `loyscan.db` may be included in Android auto-backup.

## Theme

`LoyScanTheme` is the Android Studio template: Material 3 with dynamic color on API 31+, purple fallback palette
on API 29–30 (`app/src/main/java/com/bdaniel/loyscan/ui/theme/Theme.kt:36-58`). Card colors come from brand
data, not the theme. Icons are hand-written `ImageVector`s — [[2025-12-08-hand-rolled-icons]].

## Build types

Only `debug` and `release`; release has `isMinifyEnabled = false`, so no R8/ProGuard rules are exercised yet.
`buildConfig = true` exists for `BuildConfig.DEBUG` (the Timber gate). See [[build-and-run]].
