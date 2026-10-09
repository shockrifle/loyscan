# Wiki changelog

Append-only, newest first, one self-contained line per entry. Format:
`- YYYY-MM-DD · <action> · <pages touched> · <trigger>`

- 2026-10-09 · add coverage-gate decision; Kover commands, debug-only unit tests · decisions/2026-10-09-coverage-gate-kover, decisions/_index, workflows/build-and-run, tech-debt/no-automated-tests, architecture · #27 Kover coverage gate
- 2026-10-09 · documented JVM test setup (Robolectric, fakes, repository interface seam) · workflows/build-and-run, architecture, glossary, features/card-detail, tech-debt/no-automated-tests · #26 test infrastructure
- 2026-10-09 · review build check also covers new untracked schema exports · workflows/code-review · follow-up review of the review commands
- 2026-10-09 · added the local code-review loop (/review-changes, /review-fix) · workflows/code-review, workflows/_index, index · review of the review commands
- 2026-10-09 · documented that fallback barcode content may not fit the rule format · decisions/2026-10-09-card-number-rule-engine · review fix on the #2 engine
- 2026-10-09 · rules rejected at load when their content cannot fit the barcode format · decisions/2026-10-09-card-number-rule-engine · review fix on the #2 engine
- 2026-10-09 · rule defaults no longer rewrite content (case NONE, no separators for ANY) · decisions/2026-10-09-card-number-rule-engine · review fix on the #2 engine
- 2026-10-09 · rule matching made exact (case-sensitive, tries every padding length) + fixed-length authoring rule · decisions/2026-10-09-card-number-rule-engine · review fix on the #2 engine
- 2026-10-09 · card-number rule engine (#2) · decisions/2026-10-09-card-number-rule-engine (new), decisions/_index, index, architecture (domain row), glossary (barcode content, card-number rule, core number, barcode format), tech-debt/no-automated-tests (partial progress) · commit implementing #2
- 2026-10-08 · moved backlog to GitHub Issues (#1–#23, 3 milestones) · CLAUDE (new "GitHub Issues vs this wiki" section + checklist items), /CLAUDE.md, tech-debt/* (5) and hacks/* (2) Status lines link their issues · user decision to track work in GitHub
- 2026-10-08 · history rewritten (commit email → personal), cited SHAs remapped · 24 pages with short SHAs · force-push before going public; old SHAs no longer exist
- 2026-10-08 · GitHub remote added · CLAUDE (schema sources section), /CLAUDE.md quick facts · `origin` = shockrifle/loyscan, master pushed
- 2026-10-08 · transparent Scaffolds + app-level Surface background · features/card-transition, apps/android-app, architecture; MainActivity line refs updated in glossary, decisions/2025-12-12-brand-color-in-route, tech-debt/shared-element-key-from-async-state · commit with the ListScreen/DetailScreen/MainActivity changes
- 2026-10-08 · new runbook from bootstrap verification · runbooks/cli-build-jvm-target-mismatch, workflows/build-and-run, runbooks/_index, index · terminal `./gradlew assembleDebug` failed on system JDK 19; passes with Android Studio JBR 21
- 2026-10-08 · wired root instruction file · /CLAUDE.md (new), /.gitattributes (new: union merge for wiki/_log.md) · wiki bootstrap
- 2026-10-08 · bootstrap: hacks + tech debt · hacks/list-route-shadows-kotlin-list, hacks/line-based-sql-seed-parser, tech-debt/* (5 pages) · code read at 45a33e1
- 2026-10-08 · bootstrap: decisions + bugs from history · decisions/* (6 pages), bugs/2025-12-12-detail-color-flash-on-transition · git log 00933cd..45a33e1 (20 commits; no remote, no PRs — whys are inferred and labelled)
- 2026-10-08 · bootstrap: features, workflows, runbooks, integrations · features/* (4), workflows/* (2), runbooks/reset-local-database, integrations/coil-image-loading, apps/android-app · code read at 45a33e1
- 2026-10-08 · bootstrap: architecture + glossary + faq · architecture, glossary, faq · code read at 45a33e1
- 2026-10-08 · scaffolded wiki · CLAUDE (schema), index, _log, all folder _index pages · wiki bootstrap; no prior transcripts existed to mine
