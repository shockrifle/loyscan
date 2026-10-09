---
description: Review the unpushed changes (or a commit, range, PR) against LoyScan's issues, tests, architecture and wiki rules
argument-hint: "[commit | range | PR #n | issue #n] [focus notes]"
---

Review this LoyScan change and report verified findings. Target / notes: `$ARGUMENTS`

This is a read-only review: **do not edit files, commit, push, or post anything to GitHub.** The output is a
report I can act on, or feed into `/review-fix`.

## 1. Establish the scope

- Default (no target given): the local work not yet on GitHub — unpushed commits
  (`git log origin/master..HEAD`, `git diff origin/master...HEAD`) plus uncommitted changes (`git status`,
  `git diff HEAD`). This repo has no feature branches; everything lands on `master`.
- A commit or range → `git show` / `git diff` it. A PR → `gh pr view <n>` and `gh pr diff <n>`. An issue on its
  own → review the commits that reference it (`git log --grep "#<n>\b"`).
- Find the linked issue(s): `Fixes #N` / `#N` in the commit messages, or the issue given as argument. Read each
  with `gh issue view <n> --comments` (read-only). If a feature change has no issue, that is a finding.
- If `gh` isn't on the PATH, it is installed at `C:\Program Files\GitHub CLI\gh.exe`.

## 2. Load the project context

Before judging the code, read `wiki/CLAUDE.md` (the update checklist), `wiki/architecture.md` (layering and the
new-feature checklist) and the `wiki/` pages for the touched area (from `wiki/index.md`): the feature page,
related decisions, and the open `wiki/hacks/` and `wiki/tech-debt/` items. Read every touched file in full, not
only the diff hunks, plus its callers.

## 3. Review

Go through each section below. Every finding needs a concrete failure scenario (input/state → wrong result,
crash, build break, or rule violated) and a `path:line`.

### Correctness
Trace the changed code paths end to end: Room query → mapper → repository → ViewModel → screen. Watch for:
null / empty values, an empty list, a missing or failing image URL, an unknown barcode format or card-number
rule, a `Flow` that never emits or emits `EMPTY` first, configuration change and process death (route args via
`savedStateHandle.toRoute<…>()`), and work on the main thread.

### Issue fit
- Check every acceptance criterion of the linked issue: met / partly / not met, with the evidence. Wiki items in
  the criteria (e.g. "decision record") count too.
- `Fixes #N` closes the issue on push. It is a finding if it is used while criteria are still open (suggest
  `Refs #N` or finishing the work), or missing when everything is done.
- Scope creep: changes that belong to a different open issue (`gh issue list`), or user-visible behaviour no
  issue asks for.
- If the change pays down a `tech-debt` / `hack` item (issues #12–#18 today), the matching wiki page must be
  marked Resolved and moved in its `_index.md`, and the commit should close the issue.

### Tests
- Run `./gradlew testDebugUnitTest` (set `JAVA_HOME` to Android Studio's JBR first — see root `CLAUDE.md`) and
  report the result. Failing or newly skipped tests are findings.
- Pure-Kotlin logic (`domain/`, mappers, the card-number engine) must have JVM tests in `app/src/test/`, in the
  mirrored package. Per the rule-engine decision, every `CheckDigitAlgorithm` and every bundled card-number rule
  needs its own test vectors.
- Check the tests actually exercise the change: assertions on the new branches and edge cases, not only the
  happy path. The template `ExampleUnitTest` / `ExampleInstrumentedTest` don't count as coverage.
- Room, ViewModel and Compose code has no test harness yet (`wiki/tech-debt/no-automated-tests.md`, #13). Note a missing test
  there as a gap, not a blocker, unless the change sets up a harness.

### Architecture and patterns
Check against the layer table and the new-feature checklist in `wiki/architecture.md`, especially:
- entities never leave `data/`; reads return domain models via a mapper; `domain/` has no Android/Room imports
- ViewModels are `@HiltViewModel` and expose `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), …)`
- screens get a `*Directions` object, never a `NavController`; new routes are `@Serializable` and registered in
  `AppNavigation`; anything the first frame needs (e.g. a transition colour) is in the route
- screen `Scaffold`s use `containerColor = Color.Transparent`
- `AsyncImage` gets `imageLoader = hiltImageLoader()`
- new dependencies go through `gradle/libs.versions.toml`; annotation processors use KSP, not kapt

### Data layer
- Entity or DAO change → `version` bumped in `AppDatabase`, the new `app/schemas/…/<version>.json` committed, and
  the hand-aliased `brand_` columns in the `JOIN` queries updated. Follow `wiki/workflows/database-schema-changes.md`;
  the missing migration strategy (`wiki/tech-debt/no-room-migrations.md`, #12) is a known gap, so flag a destructive change only
  if the issue or wiki says real installs exist.
- Seed SQL (`app/src/main/assets/database/loyscan.sql`) still matches the schema and keeps **one statement per
  line** — the loader runs each line separately and fails silently (`wiki/hacks/line-based-sql-seed-parser.md`).
- Card-number rule JSON stays valid for the strict parser (unknown keys, enum values and duplicate ids throw),
  and no rule is copied from a third-party rule database.

### Known traps in this codebase
- an unqualified `List<…>` in the root package `com.bdaniel.loyscan` resolves to the `List` route object
  (`wiki/hacks/list-route-shadows-kotlin-list.md`)
- `toColorInt()` on a brand colour that may be empty or invalid (`wiki/tech-debt/empty-brand-color-crash.md`)
- shared-element keys built from state that is still `EMPTY` on the first frame
  (`wiki/tech-debt/shared-element-key-from-async-state.md`)
- new code that copies one of the open hacks or tech-debt patterns instead of avoiding it

### Build
Run `./gradlew assembleDebug` and report the result; new compiler warnings in touched files are worth a mention.
No formatter or linter is configured, so style is judged against the surrounding code, not general taste.

### Wiki
Run the "When to update" checklist in `wiki/CLAUDE.md` against the change and list what is missing:
- a feature, decision, bug, hack or tech-debt page the change calls for, or an existing page it made inaccurate
  (stale `path:line` links, a behaviour that changed)
- the `wiki/_log.md` line (newest first, one line, never rewriting earlier entries)
- every `[[link]]` resolves to an existing page; dates are absolute; paths are repo-relative
- wiki updates are in the same commit as the code that triggered them

### Public-safety and commit hygiene
- No local absolute paths, personal data, e-mail addresses, credentials/keystores, or third-party proprietary
  material in the diff, the commit messages or the wiki.
- One logical change per commit; message style matches the repo (short imperative, capitalized subject; body
  for the why; `Fixes #N` / `Refs #N`).

## 4. Verify the findings

For each candidate finding, go back to the code and try to refute it: is the path reachable, is it already
handled elsewhere, is it a known and recorded trade-off (wiki decision, hack or tech-debt page)? Drop what
doesn't survive. Mark what survives **Confirmed** (traced or reproduced) or **Plausible** (likely but not proven),
and don't pad the list with taste-level nits.

## 5. Report

1. **Scope** — what was reviewed (commits, files) and the linked issue(s).
2. **Findings** — a table ranked most severe first: severity (blocker / major / minor) · Confirmed/Plausible ·
   `path:line` · the defect · the failure scenario · suggested fix (one line).
3. **Issue acceptance criteria** — each criterion with met / partly / not met and the evidence.
4. **Checks** — unit test and build results (pass/fail with the failing output), and the wiki checklist items
   still open.
5. **Follow-ups** — true but out-of-scope problems, phrased as suggested new issues (title + label). Don't
   create them.

End with one line: whether the change is ready to push, and if not, the blockers.
