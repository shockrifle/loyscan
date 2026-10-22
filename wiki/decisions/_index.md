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
- [[2026-10-13-curated-store-catalogue]] — own curated store list (no logos, slug ids), synced into Room on every open; demo cards debug-only
- [[2026-10-16-zxing-cpp-for-scanning]] — zxing-cpp 3.1.1 + CameraX for scanning: reads all DataBar, no Play Services; not ML Kit
- [[2026-10-21-gs1-scans-shown-as-hri]] — a GS1 scan no rule matches is shown as `(01)…(10)…`; other control characters as a space
- [[2026-10-21-gs1-mark-as-barcode-format]] — a GS1 Data Matrix or QR scan is stored as `GS1_DATA_MATRIX` / `GS1_QR_CODE`, not a flag; GS1 data falls back to GS1-128, then GS1 QR
- [[2026-10-22-custom-stores]] — "Other card" saves a custom store on its own screen, with the barcode format for its typed numbers; the picker offers the 13 formats ZXing core writes as themselves, no GS1 or DataBar
- [[2026-10-22-store-suggestion-trigger]] — the card form offers other stores for a scan the picked store rejects, reads only by symbology, or has no rule for; exact format ranks first

## Superseded

- [[2025-12-09-seed-db-from-sql-asset]] — demo data from a `.sql` asset in `Callback.onCreate`; superseded by
  [[2026-10-13-curated-store-catalogue]]
