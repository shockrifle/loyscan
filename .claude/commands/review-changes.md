---
description: Review the unpushed changes (or a commit, range, PR) against LoyScan's issues, tests, architecture and wiki rules
argument-hint: "[commit | range | PR #n | issue #n] [focus notes]"
---

Review this LoyScan change and report verified findings. Target / notes: `$ARGUMENTS`

This is a read-only review: **do not edit files, commit, push, or post anything to GitHub.** The only writes
are the outputs of the test and build runs (see "Build side effects" below). The output is a report I can act
on, or feed into `/review-fix`.

## 1. Establish the scope

- Run `git fetch origin` first so `origin/master` is current; if it fails (offline), say so and continue with
  the last-known `origin/master`.
- Default (no target given): the local work not yet on GitHub — unpushed commits
  (`git log origin/master..HEAD`, `git diff origin/master...HEAD`) plus uncommitted changes (`git status`,
  `git diff HEAD`).
- A commit or range → `git show` / `git diff` it. A PR → `gh pr view <n>` and `gh pr diff <n>`. An issue on its
  own → review the commits that reference it (`git log -E --grep '#<n>([^0-9]|$)'`).
- Find the linked issue(s): `Fixes #N` / `#N` in the commit messages, or the issue given as argument. Read each
  with `gh issue view <n> --comments` (read-only). If a feature change has no issue, that is a finding.

## 2. Load the project context

Before judging the code, read `wiki/CLAUDE.md` (the update checklist), `wiki/architecture.md` (layering and the
new-feature checklist) and the `wiki/` pages for the touched area (from `wiki/index.md`): the feature page,
related decisions, and the Open items in `wiki/hacks/_index.md` and `wiki/tech-debt/_index.md`. Read every
touched file in full, not only the diff hunks, plus its callers.

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
- If the change pays down an Open item of `wiki/hacks/_index.md` or `wiki/tech-debt/_index.md`, the page must be
  marked Resolved and moved in its `_index.md`, and the commit should close the issue the page links.

### Tests
LoyScan is test-first (`wiki/decisions/2026-10-12-tdd-and-coverage.md`, how-to in `wiki/workflows/tdd.md`).
- Run `./gradlew testDebugUnitTest koverVerify` (set `JAVA_HOME` first — see root `CLAUDE.md`) and report both
  results. Failing or newly skipped (`@Ignore`) tests, and a failing coverage gate, are **blockers**.
- **New logic without tests is a major finding**, in every layer: domain, mapper, DAO, repository, ViewModel,
  screen and navigation each have a harness (the per-layer table in `wiki/workflows/tdd.md`). Logic means a branch,
  a calculation, a query, a mapping or a state change. Pure wiring (DI bindings, theme values) is exempt.
- **A bug fix without a regression test is a major finding.** The test must fail without the fix: reason it
  through, or revert the fix locally to check if in doubt, then restore it. It should be named after the
  behaviour.
- Check the tests actually exercise the change: assertions on the new branches and edge cases (null, empty, the
  no-row case, errors), not only the happy path. Per the rule-engine decision, every `CheckDigitAlgorithm` and
  every bundled card-number rule needs its own test vectors.
- Test doubles are fakes from `app/src/test/java/com/bdaniel/loyscan/testing/`, not a mocking library. If the
  change alters an interface's contract (nullability, ordering, errors), the fake must change with it.
- Coverage ratchet: if coverage rose by a whole percent or more, `minBound` in `app/build.gradle.kts` should be
  raised in the same change. If `minBound` was lowered, that is a blocker.

### Architecture and patterns
Check the change against the "Layers" table and the "Checklist for a new screen/feature" in
`wiki/architecture.md`, and against the `wiki/decisions/` pages for the touched area. Cite the rule's wiki page
in the finding.

### Data layer
- Entity or DAO change → every step of `wiki/workflows/database-schema-changes.md` is done (the missing
  migration strategy, `wiki/tech-debt/no-room-migrations.md`, is a known gap, not a finding by itself).
- Seed SQL still matches the schema and follows `wiki/hacks/line-based-sql-seed-parser.md`.
- Card-number rule JSON follows `wiki/decisions/2026-10-09-card-number-rule-engine.md` (strict parsing, rules
  authored by us).

### Known traps
For every Open item in `wiki/hacks/_index.md` and `wiki/tech-debt/_index.md`: does the change run into it (e.g.
an unqualified `List<…>` in the root package) or copy the pattern into new code?

### Build
Run `./gradlew assembleDebug` and report the result; new compiler warnings in touched files are worth a mention.
No formatter or linter is configured, so style is judged against the surrounding code, not general taste.

### Build side effects
Room exports its schema at build time (`room.schemaLocation` in `app/build.gradle.kts`), so a build can rewrite
the committed `app/schemas/…/<version>.json` when an entity changed without a version bump, or create a new
`<version>.json` when the version was bumped but the export wasn't committed. Take `git status --porcelain
--untracked-files=all` before the test and build runs and again after:
- a tracked file the build modified, or a new untracked file under `app/schemas/`, is a finding (version not
  bumped, or schema export not committed); include its diff or content in the report
- then undo it: `git restore <path>` for a modified tracked file, delete a new untracked file — but only files
  that were clean (or absent) before the run; never touch a file that already had uncommitted changes

### Wiki
Run the "When to update" checklist in `wiki/CLAUDE.md` against the change and list what is missing:
- a feature, decision, bug, hack or tech-debt page the change calls for, or an existing page it made inaccurate
  (stale `path:line` links, a behaviour that changed)
- the `wiki/_log.md` line (newest first, one line, never rewriting earlier entries)
- every `[[link]]` resolves to an existing page; dates are absolute; paths are repo-relative
- wiki updates are in the same commit as the code that triggered them

### Public-safety and commit hygiene
- The diff, the commit messages and the wiki follow the "Public-safe" rule in `wiki/CLAUDE.md` (and contain no
  credentials or keystores).
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
4. **Checks** — unit test, coverage gate and build results (pass/fail with the failing output), and the wiki checklist items
   still open.
5. **Follow-ups** — true but out-of-scope problems, phrased as suggested new issues (title + label). Don't
   create them.

End with one line: whether the change is ready to push, and if not, the blockers.
