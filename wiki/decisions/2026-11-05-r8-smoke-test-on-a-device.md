# R8 on release, checked by an on-device smoke test of a debug-signed copy

**Status:** Active · **Date:** 2026-11-05 · **Commits:** cffe39e, eea7cf8

The release build runs R8 (`isMinifyEnabled`, `isShrinkResources`). It is checked in three ways:
- CI builds it on every push.
- A JVM test pins the one keep rule the app needs.
- `MinifiedAppSmokeTest`, a UiAutomator test, drives the minified app on a real device. It runs by hand before a
  release, not on CI (#73).

## Context

Until #73, release had `isMinifyEnabled = false`, so the app had never run after R8. Every library it uses ships its
own consumer rules: Room, Hilt, kotlinx.serialization, Coil, CameraX, Glance, and zxing-cpp (`-keep class zxingcpp.**`,
[[zxing-cpp]]). What those rules can't cover only shows up when the minified app runs.

## Options considered

- **Where the smoke test runs.**
  - A CI emulator job: fully automatic, but several times the current Actions minutes on a private repo ([[ci]]).
  - A documented manual tap-through: cheapest, but nothing is automated.
  - A local instrumented test, run on a device: **chosen** (decided with the user, 2026-11-05).
- **What the test drives.**
  - Compose's test APIs in `androidTest`: the test APK takes the libraries it shares with the app from the app.
    R8 has stripped the Compose internals those APIs call.
  - UiAutomator, by visible text and content descriptions: **chosen**. It touches none of the app's classes.
  - A separate self-instrumenting `com.android.test` module (the Macrobenchmark pattern): runs in its own process,
    so it needs no smoke-only keep rules. But it adds a second Gradle module to a single-module app.
- **What the test runs against.**
  - `release` itself: it was unsigned until #74, so it couldn't be installed. Since #74 it is signed with the upload
    key, which is for Play and only on the owner's machine and CI ([[2026-11-17-release-signing-on-ci]]).
  - A `smoke` build type (`initWith(release)`, debug-signed, `applicationIdSuffix ".smoke"`): **chosen**.

## Decision

- **`smoke` build type** (`app/build.gradle.kts`): release's R8 config, signed with the debug key, installed as
  `com.bdaniel.loyscan.smoke` beside a real install, so a phone's own cards are never touched. It is
  `testBuildType`, and it is never shipped.
- **Smoke test.** `app/src/androidTest/java/com/bdaniel/loyscan/MinifiedAppSmokeTest.kt` walks through the flow:
  - add a card by typing (a new custom store, then the scanner's camera runs for 3 s, then "Enter manually");
  - the card opens;
  - edit its label;
  - delete it.

  *(Update 2026-11-05, #105: a second test shares a picture of an EAN-13 to the app and checks the card form shows
  its number. The camera's empty frames never make zxing-cpp build a result, so its result classes went unchecked
  under R8. The picture is drawn with the platform's graphics and shared through the app's own FileProvider:
  [[zxing-cpp]] → Quirks, [[tdd]].)*

  Run it with `./gradlew connectedSmokeAndroidTest` ([[build-and-run]]). The run uninstalls the smoke app
  afterwards.
- **App rules** (`app/proguard-rules.pro`): one, `-keepnames enum com.bdaniel.loyscan.domain.BarcodeFormat`.
  - Navigation finds an enum route argument by its serial name (`Class.forName`).
  - Without the rule, opening the store picker crashed: *"Cannot find class with name
    "com.bdaniel.loyscan.domain.BarcodeFormat?""*. The smoke test's first run found this.
  - `RouteKeepRulesTest` reads every `@Serializable` route in `Navigation.kt` and fails on the JVM if an enum
    argument has no `-keep`/`-keepnames enum` rule.
- **Smoke-only rules.**
  - `app/proguard-smoke-rules.pro` keeps `androidx.tracing.**` and `kotlin.**` in the smoke app. The test runner
    loads them from the app, and R8 had removed them (`NoClassDefFoundError: androidx/tracing/Trace`, then
    `kotlin/LazyKt`). These rules only keep more, so release is unaffected.
  - `app/proguard-smoke-test-rules.pro` has `-dontwarn` for three optional classes AndroidX Test refers to.
- **CI** (`.github/workflows/tests.yml`) runs `assembleRelease compileSmokeAndroidTestKotlin` after the tests (step "Minified release build"; the release checks of #74 follow it, [[ci]]):
  R8 must succeed, and the smoke test must compile.
- The unused template `androidTest` dependencies (Espresso, Compose `ui-test-junit4`) were removed.

## Why

- R8 breakage is a runtime failure, so only running the minified app catches it. On its first run, the smoke test
  found a crash no JVM test could see.
- The user chose a device over CI to keep Actions minutes down. A physical phone is the usual dev setup here.
- UiAutomator over Compose test APIs: in-process tests of a minified app fail on whatever R8 stripped. Black-box
  driving reduces that to the test runner's own libraries.

## Consequences

- **The smoke test runs by hand, on a device, before a release.** CI only proves that the minified build and the
  test compile. A crash at runtime is caught only when someone runs `connectedSmokeAndroidTest`.
- **The smoke app keeps `kotlin.**` and `androidx.tracing.**`, which release doesn't.** A bug that only appears when
  R8 removes or renames stdlib classes would pass the smoke test.
- **A new enum route argument needs a `-keepnames enum` rule.** `RouteKeepRulesTest` flags it on the JVM.
- **R8's Kotlin-metadata warning.** AGP 8.13.1's R8 warns *"An error occurred when parsing kotlin metadata"* for
  Kotlin 2.3 classes. Only Kotlin reflection reads that metadata, and nothing on the release classpath depends on
  `kotlin-reflect` (checked 2026-11-05). So the warning is harmless today (inferred). Recheck it after an AGP bump,
  or if kotlin-reflect is added. *Rechecked 2026-11-10 (#115):* AGP 9.5.1's R8 doesn't print it for Kotlin 2.4.21
  (release and smoke minification rerun).
- **UiAutomator and Compose's accessibility tree.** After deleting a card, UiAutomator sometimes misses later
  screens' top-bar nodes (title, Back, Delete), even though they're drawn. This was seen with R8 on and off. The test
  avoids depending on those nodes after the delete. Whether TalkBack is affected hasn't been checked.
