# Use KSP (not kapt) for Hilt and Room

**Status:** Active · **Date:** 2025-12-08 · **Commits:** `ae2118e`, `d0d171a`

## Context
Hilt and Room were first wired with kapt (`b8b93c0`, `4969a2b`). The same day, kapt was replaced by KSP.

## Options considered
- Keep kapt.
- Switch both processors to KSP (Hilt has supported KSP since 2.48; Room since 2.5).

## Decision
`com.google.devtools.ksp` plugin; `ksp(libs.hilt.compiler)` and `ksp(libs.androidx.room.compiler)`; Room's
`room.schemaLocation` moved from the `kapt { arguments }` block to `android { ksp { arg(...) } }`
(`app/build.gradle.kts:27-29`).

## Why
Not recorded in the commit message (inferred): kapt is in maintenance mode, is slower (it generates Java stubs),
and doesn't follow Kotlin 2.x/K2 well. Both processors in use support KSP, so there was no reason to keep kapt.

## Consequences
- **The KSP version is coupled to Kotlin.** `ae2118e` pinned `ksp = "2.0.21-1.0.27"` (Kotlin 2.0.21). The
  follow-up "updated libraries fixed build errors" (`d0d171a`) moved Kotlin to 2.2.21 and KSP to `2.3.3`
  together. When bumping Kotlin, bump KSP in the same change — see [[build-and-run]].
- Don't add a kapt-only processor. Check for KSP support first.
