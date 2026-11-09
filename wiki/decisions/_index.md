# Decisions

One page per architectural or design decision: `YYYY-MM-DD-<slug>.md` (date = when the decision landed).
Skeleton: Context / Options considered / Decision / **Why** / Consequences — see [[CLAUDE]]. Write one only when
the why isn't obvious from the diff. This repo has no PRs and terse commit messages, so label inferred whys as
inferred.

## Active

- [[2025-12-08-ksp-instead-of-kapt]] — KSP for Hilt + Room; the KSP version is coupled to Kotlin
- [[2025-12-08-hand-rolled-icons]] — own `ImageVector` icons after Material icons dropped out of the BOM
- [[2025-12-09-type-safe-navigation-directions]] — `@Serializable` routes + Directions objects
- [[2025-12-12-singleton-image-loader]] — one Hilt-provided Coil loader via an EntryPoint
- [[2025-12-12-brand-color-in-route]] — first-frame data goes in the route
- [[2026-10-09-card-number-rule-engine]] — own card-number rule engine; rules are JSON data
- [[2026-10-09-coverage-gate-kover]] — Kover line-coverage gate starting at the baseline, only ever raised
- [[2026-10-12-tdd-and-coverage]] — test-first by default, JVM-first, fakes over mocks, regression test before a fix
- [[2026-10-12-data-model-v2]] — stores and cards with string ids; barcode content and display number derived; lists and rules as JSON
- [[2026-10-13-curated-store-catalogue]] — own curated store list (no logos, slug ids), synced into Room on every open; demo cards debug-only (removed in #55)
- [[2026-10-16-zxing-cpp-for-scanning]] — zxing-cpp 3.1.1 + CameraX for scanning: reads all DataBar, no Play Services; not ML Kit
- [[2026-10-21-gs1-scans-shown-as-hri]] — a GS1 scan no rule matches is shown as `(01)…(10)…`; other control characters as a space
- [[2026-10-21-gs1-mark-as-barcode-format]] — a GS1 Data Matrix or QR scan is stored as `GS1_DATA_MATRIX` / `GS1_QR_CODE`, not a flag; GS1 data falls back to GS1-128, then GS1 QR
- [[2026-10-22-custom-stores]] — "Other card" saves a custom store on its own screen, with the barcode format for its typed numbers; the picker offers the 13 formats ZXing core writes as themselves, no GS1 or DataBar
- [[2026-10-28-datastore-for-user-preferences]] — user preferences (the card list's sort) in one DataStore Preferences file behind `UserPreferencesRepository`; unknown values read as the default
- [[2026-10-28-portrait-on-phones]] — on a phone every screen but the card is held in portrait; screens 600dp or wider rotate freely (#63)
- [[2026-10-30-frequently-added-stores]] — "frequently added" is a curated catalogue flag; the country is the SIM's, then the network's, then the locale's; added to schema v2 in place
- [[2026-11-03-share-in-standard-launch-mode]] — `MainActivity` is `standard`; a share that reaches an open LoyScan (`onNewIntent`) is passed on to a new task, so a chooser, `NEW_TASK` or `SINGLE_TOP` share doesn't replace an open card form (#83)
- [[2026-11-03-card-photos-as-private-files]] — card photos are files in `files/card_photos/`, named (not pathed) in the row, copied on pick; the repositories delete them on every write that drops them, the custom store's cascade included (#10)
- [[2026-11-03-card-photos-from-the-system-camera-app]] — "Take photo" opens the system camera app, not a CameraX screen of our own (#10)
- [[2026-11-04-card-photos-interactive-crop]] — a picked or taken photo is framed to the card's ratio in an interactive crop screen (pan/zoom, or keep the whole picture), staged so it survives process death (#10)
- [[2026-11-04-home-screen-widget]] — a Glance widget; it opens a card via an intent extra `MainActivity` reads (not a URI deep link); brand-colour tiles, no photo, in the first slice (#11)
- [[2026-10-22-store-suggestion-trigger]] — the card form offers other stores for a scan the picked store rejects, reads only by symbology, or has no rule for; exact format ranks first
- [[2026-11-05-no-cloud-backup-in-v1]] — cloud backup carries no app data; device-to-device transfer carries all of it; `allowBackup` stays `true` so D2D survives; opt-in backup is #24 (#71)
- [[2026-11-05-r8-smoke-test-on-a-device]] — release runs R8; a debug-signed `smoke` copy is tested on a device by a UiAutomator test, run by hand (not CI); one app keep rule, for the enum route argument, pinned by `RouteKeepRulesTest` (#73)
- [[2026-11-05-room-schema-frozen-from-v2]] — v2 is the first released schema; every change is a version bump with a migration, no destructive fallback; pinned hashes and a v2-database open test enforce it (#12)
- [[2026-11-09-adapted-third-party-skills]] — four of Matt Pocock's MIT skills (`diagnosing-bugs`, `writing-for-agents`, `grilling`, `retro`) copied into `.claude/skills/` and adapted; no plugin install, nothing from unlicensed `ai-utils`

## Superseded

- [[2025-12-09-seed-db-from-sql-asset]] — demo data from a `.sql` asset in `Callback.onCreate`; superseded by
  [[2026-10-13-curated-store-catalogue]]
- [[2026-11-03-share-into-the-sharing-apps-task]] — `MainActivity` was `singleTop` so a share could reach an open LoyScan; on a device chooser shares never did, and a `NEW_TASK` share dropped an open form (#77, #84); superseded by [[2026-11-03-share-in-standard-launch-mode]]
