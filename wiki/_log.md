# Wiki changelog

Append-only, newest first, one self-contained line per entry. Format:
`- YYYY-MM-DD · <action> · <pages touched> · <trigger>`

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
