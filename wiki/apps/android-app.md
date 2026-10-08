# Android app (`:app`)

The only runtime surface: a single-Activity Compose app, package/applicationId `com.bdaniel.loyscan`,
versionName `1.0` (versionCode 1). minSdk 29, target/compileSdk 36, Java/JVM 21 (`app/build.gradle.kts`).

## Entry points

- `LoyScanApplication` (`@HiltAndroidApp`) — plants Timber `DebugTree` in debug builds only
  (`app/src/main/java/com/bdaniel/loyscan/LoyScanApplication.kt:7-14`).
- `MainActivity` (`@AndroidEntryPoint`, launcher) — `enableEdgeToEdge()`, then
  `LoyScanTheme { SharedTransitionLayout { AppNavigation() } }`
  (`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:20-33`).
- `AppNavigation` — start destination [[card-list]], second destination [[card-detail]].

## Manifest facts

- `android.permission.INTERNET` — added in `0f9ea48` for brand logos ([[coil-image-loading]]).
- `allowBackup="true"` with the template `backup_rules.xml` / `data_extraction_rules.xml` (unchanged since
  `d5affac`), so `loyscan.db` may be included in Android auto-backup.

## Theme

`LoyScanTheme` is the Android Studio template: Material 3 with dynamic color on API 31+, purple fallback palette
on API 29–30 (`app/src/main/java/com/bdaniel/loyscan/ui/theme/Theme.kt:36-58`). Card colors come from brand
data, not the theme. Icons are hand-written `ImageVector`s — [[2025-12-08-hand-rolled-icons]].

## Build types

Only `debug` and `release`; release has `isMinifyEnabled = false`, so no R8/ProGuard rules are exercised yet.
`buildConfig = true` exists for `BuildConfig.DEBUG` (the Timber gate). See [[build-and-run]].
