# Build and run

A standard Gradle-wrapper project with no custom tasks or scripts.

**From a terminal, Gradle must run on JDK 21** (Android Studio's bundled JBR), or the Kotlin compile fails with a
JVM-target mismatch — [[cli-build-jvm-target-mismatch]].

```sh
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # see the runbook above
./gradlew assembleDebug        # build (verified passing 2026-10-08 with JAVA_HOME as above)
./gradlew installDebug         # install on the connected device/emulator
adb shell am start -n com.bdaniel.loyscan/.MainActivity
./gradlew testDebugUnitTest                                          # every test, on the JVM
./gradlew testDebugUnitTest --tests "com.bdaniel.loyscan.ListViewModelTest"   # one class (wildcards work)
```

On Windows, use `gradlew.bat` or Git Bash. `local.properties` (the SDK path) is git-ignored and must exist
locally.

## Tests

All tests are in `app/src/test/` and run on the JVM; there is no `androidTest` source set, so no emulator is
needed. HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.

- Plain JUnit for pure Kotlin (domain logic) and for ViewModels that don't read a route (`ListViewModelTest`).
- `@RunWith(AndroidJUnit4::class)` runs a class on **Robolectric**. Use it for Room (`LoyaltyCardDaoTest`),
  `SavedStateHandle(route = …)` (`DetailViewModelTest`) and Compose UI (`ListScreenTest`, `createComposeRule()`).
  The first Robolectric test in a run takes a few seconds to start; the rest are quick.
- Libraries: Truth for assertions, Turbine for `Flow`s, kotlinx-coroutines-test (`runTest`, `MainDispatcherRule`).
- Shared helpers (fixture builders, fakes, `MainDispatcherRule`) are in `app/src/test/java/com/bdaniel/loyscan/testing/`.
  How the code is made testable: [[architecture]] → Testing seams.

## Dependencies

All versions live in `gradle/libs.versions.toml`. Constraints that have bitten before:

- **The KSP version must match the Kotlin version line.** Kotlin `2.2.21` ↔ KSP `2.3.3` (changed together in
  `d0d171a`). See [[2025-12-08-ksp-instead-of-kapt]].
- Compose libraries come from the Compose BOM. The BOM bump in `d0d171a` removed the transitive Material
  icons — [[2025-12-08-hand-rolled-icons]].
- The `kotlinSerialization` plugin version is pinned to the Kotlin version (`2.2.21`). It's needed for the
  type-safe navigation routes.
- `coroutinesTest` must match the `kotlinx-coroutines-core` version the app actually resolves (check with
  `./gradlew :app:dependencies --configuration debugRuntimeClasspath`); a newer one silently upgrades core.
- Robolectric on SDK 36 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` on the test JVM
  (`app/build.gradle.kts`, `testOptions`). Without it every Robolectric test fails with *"Failed to interact with
  raw FileDescriptor internals"*. Seen with Robolectric 4.17 on JDK 21, 2026-10-09.

## Logs

Debug builds only: `adb logcat`, via Timber's `DebugTree`. See [[faq]].
