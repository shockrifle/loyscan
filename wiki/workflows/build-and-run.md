# Build and run

A standard Gradle-wrapper project with no custom tasks or scripts.

**From a terminal, Gradle must run on JDK 21** (Android Studio's bundled JBR), or the Kotlin compile fails with a
JVM-target mismatch — [[cli-build-jvm-target-mismatch]].

```sh
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # see the runbook above
./gradlew assembleDebug        # build (verified passing 2026-10-08 with JAVA_HOME as above)
./gradlew installDebug         # install on the connected device/emulator
adb shell am start -n com.bdaniel.loyscan/.MainActivity
./gradlew test                 # JVM unit tests (template only)
./gradlew connectedAndroidTest # instrumented tests (template only)
```

On Windows, use `gradlew.bat` or Git Bash. `local.properties` (the SDK path) is git-ignored and must exist
locally. There are no real tests yet — [[no-automated-tests]].

## Dependencies

All versions live in `gradle/libs.versions.toml`. Constraints that have bitten before:

- **The KSP version must match the Kotlin version line.** Kotlin `2.2.21` ↔ KSP `2.3.3` (changed together in
  `ad96b80`). See [[2025-12-08-ksp-instead-of-kapt]].
- Compose libraries come from the Compose BOM. The BOM bump in `ad96b80` removed the transitive Material
  icons — [[2025-12-08-hand-rolled-icons]].
- The `kotlinSerialization` plugin version is pinned to the Kotlin version (`2.2.21`). It's needed for the
  type-safe navigation routes.

## Logs

Debug builds only: `adb logcat`, via Timber's `DebugTree`. See [[faq]].
