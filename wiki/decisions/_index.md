# Decisions

One page per architectural or design decision: `YYYY-MM-DD-<slug>.md` (date = when the decision landed).
Skeleton: Context / Options considered / Decision / **Why** / Consequences — see [[CLAUDE]]. Write one only when
the why isn't obvious from the diff. Commit messages are terse and, until 2026-11-09, there were no PRs, so label
inferred whys as inferred.

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
- [[2026-11-09-ui-language]] — English default UI with a Hungarian translation (informal register); tests stay in English
- [[2026-11-09-pull-request-workflow]] — every change lands through a PR, rebase-merged by the agent after review and green CI; a plan critic and the user's confirmation before code; Dependabot PRs reviewed and merged by `/dependency-pr` (#116)
- [[2026-11-09-adapted-third-party-skills]] — four of Matt Pocock's MIT skills (`diagnosing-bugs`, `writing-for-agents`, `grilling`, `retro`; later `to-tickets` and, with #74, `wizard`) copied into `.claude/skills/` and adapted; no plugin install, nothing from unlicensed `ai-utils`
- [[2026-11-12-no-bundled-store-logos]] — no bundled or fetched retailer logos (Play's IP policy asks for permission; 23 retailers checked, none practical); names and brand colours stay; a non-affiliation note in the app and Play listing before release (#109)
- [[2026-11-13-user-supplied-store-logos]] — a store's logo is the user's own picture, a PNG with transparency in the card photos' directory named by the existing `logo` column; the catalogue sync never touches it (#151)
- [[2026-11-12-design-tokens-and-card-shape]] — a plain `Spacing` object shared with the widget, M3 default `Shapes`, one ID-1-proportional `CardShape` (6%) for every depiction of the card; a visual-only scanner viewfinder; card-ratio widget tiles in 1–2 columns, square corners on API 29–30 (#108)
- [[2026-11-17-release-versioning]] — semver `loyscan.versionName` in `gradle.properties`; `versionCode = X*10000 + Y*100 + Z`, derived and checked by the build (#74)
- [[2026-11-17-release-signing-on-ci]] — Play App Signing; the upload key (PKCS12, one password) is read from Gradle properties or `LOYSCAN_UPLOAD_*` variables, never a repo file; half set up stops the build; CI signs on a tag (#74)
- [[2026-11-20-barcode-type-per-card]] — (proposed) a typed number's barcode type is saved on every card and chosen on the card form; another type than the rule's turns the rule off; custom-store forms stop asking (#187)
- [[2026-11-23-public-repository]] — the repo goes public under GPL-3.0-or-later after a sweep; author email accepted, old Actions runs deleted (#200)
- [[2026-11-23-ci-run-trims]] — the Tests workflow's first step decides whether the run needs the gate; a wiki-only PR is green without Gradle (#199)
- [[2026-11-20-other-card-scan-first]] — (proposed) "Other card": scan → new-store form with a logo slot, scan and duplicate-name suggestions → card form, each step replacing the last (#188)
- [[2026-11-18-launcher-icon]] — launcher icon: two fanned cards, the front card's barcode a heart, on plum; chosen against 154 Play icons, away from Google Wallet's and the Hearts card games' look (#75)

## Superseded

- [[2025-12-09-seed-db-from-sql-asset]] — demo data from a `.sql` asset in `Callback.onCreate`; superseded by
  [[2026-10-13-curated-store-catalogue]]
- [[2026-11-03-share-into-the-sharing-apps-task]] — `MainActivity` was `singleTop` so a share could reach an open LoyScan; on a device chooser shares never did, and a `NEW_TASK` share dropped an open form (#77, #84); superseded by [[2026-11-03-share-in-standard-launch-mode]]
