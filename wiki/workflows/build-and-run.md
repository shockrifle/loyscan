# Build and run

A standard Gradle-wrapper project with no custom tasks or scripts; coverage tasks come from the Kover plugin.

**Gradle runs on JDK 17 or newer** (AGP 9's minimum). Since AGP 9 (#115) Kotlin takes its JVM target (21) from
`compileOptions`, so the JDK that runs Gradle no longer decides the bytecode: a terminal build on JDK 19 works
(checked 2026-11-10), and so does one on Android Studio's bundled JBR 25 (the gate and `assembleDebug`, checked
2026-11-18; until then the JBR was 21). CI uses JDK 21. Before #115 any JDK other than 21 failed the build —
[[cli-build-jvm-target-mismatch]].

```sh
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # optional: Android Studio's JDK (25; CI uses 21)
./gradlew assembleDebug        # build (verified passing 2026-10-08 with JAVA_HOME as above)
./gradlew installDebug         # install on the connected device/emulator
adb shell am start -n com.bdaniel.loyscan/.MainActivity
./gradlew testDebugUnitTest                                          # every test, on the JVM
./gradlew testDebugUnitTest --tests "com.bdaniel.loyscan.ListViewModelTest"   # one class (wildcards work)
./gradlew koverHtmlReportDebug                                       # coverage report (also koverXmlReportDebug)
./gradlew koverVerifyDebug                                           # fail if line coverage is below the minimum
./gradlew lintDebug                                                  # lint; errors fail it, as on CI (#126)
./gradlew check                                                      # tests + lint + coverage gate
./gradlew assembleRelease                                            # the minified (R8) release; signed only with the upload key set up (below)
./gradlew bundleRelease                                              # the AAB for Play, likewise
./gradlew -q :app:printVersion                                       # "<versionName> <versionCode>" as Gradle resolves them
.github/scripts/check-release-build.sh                               # the release checks CI runs (Git Bash): wiki/workflows/ci.md
./gradlew connectedSmokeAndroidTest                                  # on-device smoke test of the R8 build (below)
```

**Release signing** (#74, [[2026-11-17-release-signing-on-ci]]). `release` is signed when three values are set, each
as a Gradle property (in `~/.gradle/gradle.properties`, never in the project) or an environment variable:
`loyscan.upload.storeFile` / `LOYSCAN_UPLOAD_STORE_FILE` (a forward-slash path on Windows: `C:/Users/…`),
`loyscan.upload.storePassword` / `LOYSCAN_UPLOAD_STORE_PASSWORD` (also the key's: PKCS12), `loyscan.upload.keyAlias` /
`LOYSCAN_UPLOAD_KEY_ALIAS`. With none set it's unsigned; with some, every Gradle command fails with *"Release signing is
half set up. Missing: …"*.

On Windows, use `gradlew.bat` or Git Bash. `local.properties` (the SDK path) is git-ignored and must exist
locally.

## IDE settings

**Android Studio's `.idea/` folder is not tracked** (#174). Opening the project imports it from Gradle, which writes
`.idea/` afresh; the Gradle build (the wrapper, `gradle/libs.versions.toml`, `app/build.gradle.kts`) is the only
project setup. The files the template had committed held machine-local state (the project JDK's name, the selected
device, import bookkeeping) and nothing shared: the inspection profile was Android Studio's default. Every Android
Studio update rewrote some of them (moving the project JDK from `jbr-21` to `jbr-25` did, 2026-11-18). A clone that
pulls #174's commit across loses the old files from its working tree (git deletes what a commit untracks); a Gradle sync
in Android Studio writes them again.

**Adding a shared IDE file later** (a code style in `.idea/codeStyles/`, a customised inspection profile in
`.idea/inspectionProfiles/`, shared run configurations in `.idea/runConfigurations/`) takes three changes in the same
commit, or the file is ignored, or `GitIgnoreTest` fails:

1. A `!` exception in `.gitignore` after `/.idea/*`, e.g. `!/.idea/codeStyles/`. Keep the rule as `/.idea/*`: git can't
   re-include a file inside a directory that is itself ignored (`/.idea/`).
2. The file's path in `GitIgnoreTest`'s `SHARED_IDE_FILES`, and out of the ignored list in
   `androidStudiosProjectFiles_areIgnored`.
3. A line here saying what the file shares.

`GitIgnoreTest` asks git itself: every `.idea/` path is ignored, and git tracks no file under `.idea/` outside
`SHARED_IDE_FILES`, so a file added with `git add -f` fails the gate too.

## Tests

How to work test-first and which test goes with which layer: [[tdd]]. The same tests and gate run in CI on every
pull request and on `master`: [[ci]].

All tests but one are in `app/src/test/` and run on the JVM, with no emulator. Only the debug variant runs unit tests:
the other build types' test tasks exist but are switched off (`app/build.gradle.kts`, `tasks.withType<Test>`), because
the Compose UI tests need `ui-test-manifest`, a debug-only dependency. Why they exist at all: AGP 9's Compose Preview
tasks (Dependencies, below). HTML report:
`app/build/reports/tests/testDebugUnitTest/index.html`.

**Smoke test of the minified build** (#73, [[2026-11-05-r8-smoke-test-on-a-device]]). It's the one test class in
`app/src/androidTest/`: `MinifiedAppSmokeTest`.
- **What it does.** On a connected device it adds a card by typing (with a new custom store, keeping the scanner's camera
  running for 3 s on the way), opens it, edits it and deletes it. A second test shares a picture of an EAN-13 to the
  app and checks that the card form shows its number (#105). Both run on the `smoke` build: release's R8 config, signed
  with the debug key.
- **Needs an unlocked screen.** With the lock screen showing, the first wait fails with nothing listed on screen.
  `adb shell svc power stayon usb` keeps an unlocked phone awake on USB (`svc power stayon false` undoes it).
- **When to run it.** Before a release, and after a change to R8 rules, routes, or a library that ships keep rules.
  CI only builds it.
- **Last run.** 2026-11-12 on an ASUS Zenfone 10 (ASUS_AI2302), Android 15 (API 35): both tests passed with the
  phone set to Hungarian and again set to English (#139). Since #72 the test finds every text it taps by resource
  name, so the phone's language doesn't matter.
- **How.** `./gradlew connectedSmokeAndroidTest`. The smoke app installs as `com.bdaniel.loyscan.smoke`, so a
  phone's own LoyScan and its cards aren't touched, and it's uninstalled after the run.
- **On failure.** The message lists the texts that were on screen, and a screenshot is left on the device:
  `adb pull /data/local/tmp/smoke-failure.png`. From Git Bash, prefix the command with `MSYS_NO_PATHCONV=1`, or it
  rewrites the device path.
- **Reading a crash.** The R8 stack trace is obfuscated; retrace it with
  `app/build/outputs/mapping/smoke/mapping.txt`.

Coverage report: `app/build/reports/kover/htmlDebug/index.html`. The gate's minimum, its exclusions and the rule
for raising it: [[2026-10-09-coverage-gate-kover]].

- Plain JUnit for pure Kotlin (domain logic) and for ViewModels that don't read a route (`ListViewModelTest`).
- `@RunWith(AndroidJUnit4::class)` runs a class on **Robolectric**. Use it for Room (`LoyaltyCardDaoTest`),
  `SavedStateHandle(route = …)` (`DetailViewModelTest`) and Compose UI (`ListScreenTest`, `createComposeRule()`).
  The first Robolectric test in a run takes a few seconds to start; the rest are quick.
- Libraries: Truth for assertions, Turbine for `Flow`s, kotlinx-coroutines-test (`runTest`, `MainDispatcherRule`).
- Whole-app tests: `@HiltAndroidTest` + `@Config(application = HiltTestApplication::class)` with
  `createAndroidComposeRule<MainActivity>()` (`AppNavigationTest`). The Hilt rule must run first
  (`@get:Rule(order = 0)`). `InMemoryDatabaseModule` gives them an empty in-memory database to fill.
- Shared helpers (fixture builders, fakes, `MainDispatcherRule`, `InMemoryDatabaseModule`, `TemporaryPreferencesModule`) are in `app/src/test/java/com/bdaniel/loyscan/testing/`.
  How the code is made testable: [[architecture]] → Testing seams.

## Dependencies

All versions live in `gradle/libs.versions.toml`. Constraints that have bitten before:

- **Kotlin `2.4.21`, KSP `2.3.13`.** KSP 2.3.x is versioned independently of Kotlin; `2.3.3` built with Kotlin
  2.2.21 and 2.3.21 alike. After a Kotlin bump, run the full build and bump KSP only if it fails
  ([[2025-12-08-ksp-instead-of-kapt]]). `2.3.13` came with Dependabot's Kotlin group in #115 and is what was tested
  with AGP 9's built-in Kotlin; `2.3.3` wasn't tried there. Kotlin is at least 2.3.20 because zxing-cpp 3.1.x is
  built on that stdlib ([[zxing-cpp]]).
- **Kotlin, Hilt, the Android Gradle plugin, Gradle and compileSdk move together.** Upgraded as one set in #115
  (2026-11-10): Kotlin 2.4.21, Hilt 2.60.1, AGP 9.5.1, Gradle 9.8.2 (9.9.0 since #114, which AGP 9.5.1 builds on; 9.9.1 since PR #164),
  compileSdk 37 (targetSdk stays 36). Each failed alone on Dependabot's first PRs, 2026-11-09 ([[ci]] → Dependabot):
  - **A Kotlin minor needs a Hilt that knows it.** Kotlin 2.4.21 (PR #110) doesn't compile with Hilt 2.57.2:
    *"[Hilt] Provided Metadata instance has version 2.4.0, while maximum supported version is 2.3.0"*. Hilt's
    annotation processor reads Kotlin metadata.
  - **Hilt 2.60 needs AGP 9** (inferred). Hilt 2.60.1 alone (PR #112) fails to apply its Gradle plugin
    (`com.google.dagger.hilt.android`) with AGP 8.13.1 and Gradle 8.13; the log doesn't say why. It applies with
    AGP 9.5.1.
  - **AGP 8 runs on Gradle 9.5 at most.** Gradle 9.8.2 alone (PR #114) fails to apply AGP 8.13.1
    (`com.android.application`): AGP 8.x uses `org.gradle.api.problems.internal.InternalProblems`, a Gradle internal
    API removed in Gradle 9.6 (Gradle's upgrade guide, `upgrading_version_9.html#agp_8x_incompatible`).
  - **The androidx group needs AGP 9.1 and compileSdk 37.** Dependabot's androidx PR (#111): `hilt-navigation-compose`
    1.4.0, `navigation-compose` 2.10.2 and others fail `checkDebugAarMetadata`, requiring AGP 9.1.0 or newer and
    compileSdk 37 (AGP 8.13.1 recommends 36 at most). The AAR metadata check catches it, so CI does. Landed with the
    set in #115: core-ktx 1.19.1, Compose BOM 2026.09.00, navigation 2.10.2, hilt-navigation-compose 1.4.0, Room
    2.8.5, DataStore 1.3.0, UiAutomator 2.4.1.
- **AGP 9 changes that touched this build** (#115):
  - **Built-in Kotlin.** AGP 9 compiles Kotlin itself, and applying `org.jetbrains.kotlin.android` fails (*"Failed to
    apply plugin 'org.jetbrains.kotlin.android'"*), so that plugin is gone. The Compose compiler, serialization, KSP,
    Hilt and Kover plugins stay. The JVM target follows `compileOptions` (21), see the top of this page.
  - **Unit tests only for `testBuildType`.** AGP 9 creates unit tests only for the tested build type, here `smoke`
    (the instrumented smoke test runs on the minified build), so `testDebugUnitTest` didn't exist.
    `android.onlyEnableUnitTestForTheTestedBuildType=false` in `gradle.properties` turns them on for every variant.
  - **Compose Preview tasks need unit tests on every variant.** AGP 9 adds `generate<Variant>ComposePreviewRunfiles`.
    With the release and smoke unit tests disabled in `androidComponents.beforeVariants` (the setup before #115),
    creating those tasks failed (*"Compose Preview requires unit tests…"*), e.g. in `./gradlew tasks --all`. So the
    test tasks now exist on every variant, and the non-debug ones are switched off (`enabled = false`). A bare
    `./gradlew test` still compiles the release and smoke unit-test sources; the gate runs `testDebugUnitTest` only.
- Compose libraries come from the Compose BOM. The BOM bump in `d0d171a` removed the transitive Material
  icons — [[2025-12-08-hand-rolled-icons]].
- The `kotlinSerialization` plugin version is pinned to the Kotlin version (`2.4.21`). It's needed for the
  type-safe navigation routes.
- **kotlinx-serialization-json 1.12 wrapped constructor exceptions; 1.13 reverted it.** In 1.12 an exception thrown in
  a `@Serializable` class's constructor while decoding (a `require` in an `init` block, as in `CardNumberRule` and the
  catalogue entries) arrived as the cause of a `JsonException`, a `SerializationException` (its 1.12.0 release notes,
  "Exceptions rework"; seen in #115). 1.13.0 rolled that back ("Exceptions rework rollback"), so the `require`'s
  `IllegalArgumentException` comes out as it is again (PR #163). Either way the parsers throw
  `IllegalArgumentException`s (`SerializationException` is one), so callers didn't change. Tests that tell a rule check
  from a JSON error look at `rootCause` ([[tdd]] → Shared helpers), which works under both behaviours.
- `coroutinesTest` must match the `kotlinx-coroutines-core` version the app actually resolves (check with
  `./gradlew :app:dependencies --configuration debugRuntimeClasspath`); a newer one silently upgrades core.
- DataStore (`datastore`, [[2026-10-28-datastore-for-user-preferences]]) pulls `kotlinx-coroutines-core` 1.8.1, which
  resolves to the app's 1.9.0; recheck after a bump (still 1.9.0 with DataStore 1.3.0, #115). On a Windows host it
  can't replace an existing file, so JVM tests write each preferences file at most once.
- Robolectric on SDK 36 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` on the test JVM
  (`app/build.gradle.kts`, `testOptions`). Without it every Robolectric test fails with *"Failed to interact with
  raw FileDescriptor internals"*. Seen with Robolectric 4.17 on JDK 21, 2026-10-09.

## Logs

Debug builds only: `adb logcat`, via Timber's `DebugTree`. See [[faq]].
