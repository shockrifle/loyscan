---
description: Validate the claims of a code review, plan the fixes and discuss them before implementing
argument-hint: <review text>
---

Work through this code review:

<review>
$ARGUMENTS
</review>

The review is input to check, not a list of orders. Reviewers (human or AI) misread code, review a stale revision, or miss the context a decision was made in — every claim gets verified before anything is changed.

## 1. Establish the scope

- Run `git fetch origin` first so `origin/master` is current; if it fails (offline), say so and continue with the last-known `origin/master`.
- Work lands through pull requests (root `CLAUDE.md`). By default the review is about the checked-out branch's work that is not on `master` yet: its commits (`git log origin/master..HEAD`, `git diff origin/master...HEAD`) plus uncommitted changes (`git status`, `git diff HEAD`).
- If the review names a commit, a commit range, a PR (`gh pr view <n>`, `gh pr diff <n>`; work on its branch, `gh pr checkout <n>`) or an issue, use that instead; if it is unclear which revision was reviewed, ask.
- Split the review into individual, numbered claims. A comment that makes several points becomes several claims.

## 2. Validate every claim

For each claim, read the code it refers to — the current version, not only the diff hunk — and whatever it depends on: callers, the other layers of the same flow (entity/DAO → mapper → repository → ViewModel → screen, see `wiki/architecture.md`), tests, and the relevant `wiki/` page (start from `wiki/index.md`).

- A claim about the data layer (a column, a type, a nullability, a default) → check the Room entity, the exported schema in `app/schemas/` and the seed SQL `app/src/main/assets/database/loyscan.sql`, not from memory. See `wiki/workflows/database-schema-changes.md` and `wiki/features/seed-data.md`.
- A claim about an external library's behaviour (Coil, Navigation, Hilt, Room) → check the version in `gradle/libs.versions.toml` and `wiki/integrations/`; don't assume the behaviour of a different version.
- A claim about requirements or scope → check the GitHub issue (`gh issue view <n>`, read-only) and the `wiki/features/` / `wiki/decisions/` page for the area. A known shortcut may already be recorded in `wiki/hacks/` or `wiki/tech-debt/`.
- A claim about behaviour → trace it through the code; run the relevant unit tests if that settles it.
- A claim about style → check it against the root `CLAUDE.md`, `wiki/architecture.md` and the surrounding code's idiom, not general taste. No formatter or linter is configured in this repo, so there is no tool to defer to.

Give each claim a verdict:

- **Valid** — the problem is real; say what the concrete failure is.
- **Partly valid** — the point stands, but the reasoning or the suggested fix is off; say which part.
- **Invalid** — the code is correct as it is; say why, with the file/line or wiki page that shows it.
- **Unclear** — needs a decision or information I have (product intent, a trade-off not recorded anywhere); say what the question is.
- **Out of scope** — true, but not caused by the reviewed change; note it as a possible follow-up issue.

Don't soften an invalid claim into a fix just to satisfy the reviewer.

If the claim comes with a proposed fix (a code suggestion, a "use X instead", a described approach), verify the fix separately from the claim — a valid problem often comes with a wrong fix:

- does it actually remove the failure the claim describes, in every path that leads to it?
- does it break anything else — other callers, the Room schema (a version bump needs a migration and a new exported schema), the seed data, Hilt bindings, type-safe navigation routes, shared-element transitions, the layering in `wiki/architecture.md`?
- does it hold for the edge cases: null / missing values, empty lists, a missing or failing image URL, an unknown card format, configuration change and process death?
- does it fit the codebase's existing patterns (repository + mapper, `@HiltViewModel` exposing flows to the screen, the card-number rule engine), or is there an existing mechanism it should use instead?

Give the fix its own verdict (sound / sound with changes / flawed) with the concrete counter-example when it is flawed.

## 3. Plan the fixes

For the valid and partly valid claims, write a fix plan:

- what changes, in which files and layers
- if the reviewer suggested a fix: whether to follow it or do something else, and why
- related places with the same problem that the review did not mention
- the test for each fix, written first (`wiki/workflows/tdd.md`): a valid bug claim gets a regression test that
  fails before the fix; a claim about missing behaviour gets a test for that behaviour
- which wiki pages the fix makes inaccurate or calls for (run the "When to update" checklist in `wiki/CLAUDE.md`)
- how the fixes map to commits — one focused commit per logical fix, message in the repo's existing style (short imperative sentence, capitalized, no prefix; `Fixes #N` when it closes an issue)

## 4. Discuss — stop here

Present the claim table (claim → verdict → proposed-fix verdict, if any → one-line reason) and the fix plan, plus the questions for the unclear ones. Suggest a short answer I can give the reviewer for each invalid claim.

**Do not edit any file before I approve the plan.** I may drop, change or add items.

## 5. Implement

After my go-ahead, implement exactly the approved plan. If something turns out differently while implementing, stop and tell me instead of improvising.

Implement test-first: for each fix, write its test, run it and see it fail for the claimed reason, then fix
it. If the test passes before the fix, the claim was wrong: stop and tell me.

Then:

- set `JAVA_HOME` to Android Studio's JBR first (see root `CLAUDE.md`), then run `./gradlew testDebugUnitTest koverVerify assembleDebug` and make sure it passes; raise the Kover
  `minBound` if coverage rose (`wiki/decisions/2026-10-09-coverage-gate-kover.md`)
- update the wiki pages from the plan in the same commit as the code, and append a line to `wiki/_log.md`
- commit locally on the change's branch, never on `master`; do not push, do not comment on the PR or issue, and do not create, edit or close GitHub issues — report back and I will answer the reviewer myself. Out-of-scope findings go in the report as suggested follow-up issues.
- keep commits within the "Public-safe" rule in `wiki/CLAUDE.md`

## 6. Draft the reply to the reviewer

Finish with a short reply I can paste under the review, in the review's language (Hungarian review → Hungarian reply):

- per claim, one or two sentences: fixed (with the commit hash and what changed), fixed differently than suggested (and why), or not changed (and why — the file/line or wiki page that shows it)
- the decisions made while discussing, if they change behaviour the reviewer should know about (e.g. an edge case that now behaves differently)
- plain and factual, no thanks-for-the-review filler, no restating the review

Print it as a quoted block, separate from the rest of the report. Do not post it anywhere.
