# Release versioning: semver name, derived versionCode

**Status:** Active · **Date:** 2026-11-17 · **Commits:** see #74

## Context

Play needs a `versionCode` that grows with every upload, and shows `versionName` to users (the About screen shows it
too, [[about]]). Until #74 the app had the template's `versionCode 1` / `versionName "1.0"`, set in
`app/build.gradle.kts`, and nothing had been uploaded to Play.

## Options considered

1. **Semver name by hand, code derived from it.** One value to bump.
2. **Both by hand.** Two values to keep in step, and a forgotten code bump is rejected only at upload.
3. **Code from the CI run number.** Only CI could make a release-worthy build; local builds get a dummy code, and the
   code says nothing about the version.

## Decision

Option 1, chosen by the owner when #74 was planned. `loyscan.versionName=X.Y.Z` in `gradle.properties` is the one
place a version is set. `app/build.gradle.kts` derives `versionCode = X*10000 + Y*100 + Z` and fails the build on
anything that isn't `X.Y.Z` with no leading zeros and `Y`, `Z` below 100 (`1.0`, `v1.0.0`, `1.100.0`, `01.0.0`).
`./gradlew -q :app:printVersion` prints the pair Gradle resolved. A release is tagged `vX.Y.Z` on `master`.

## Why

- One source of truth: the name is what people read, and the code follows from it, so the two can't drift.
- The code grows with the version as long as `Y` and `Z` stay below 100: 1.2.3 → 10203, 1.3.0 → 10300, 2.0.0 → 20000.
  Without that limit 1.100.0 (20000) would collide with 2.0.0.
- `gradle.properties` rather than the build script, so a shell step can read it, and a `-P` can try other values
  without editing a file: `.github/scripts/check-release-build.sh` checks the scheme that way, with hand-worked
  values, on every CI run ([[ci]]).
- `versionCode` 1 was never uploaded, so jumping to 10000 for 1.0.0 costs nothing.

## Consequences

- A release PR bumps `loyscan.versionName` only.
- The highest code Play allows (2100000000) leaves room for majors up to 209999.
- No pre-release suffixes (`1.0.0-beta1`): test tracks on Play carry the same `X.Y.Z` scheme.
