# Changes land through pull requests, rebase-merged by the agent after green CI

**Status:** Active · **Date:** 2026-11-09 · **PR:** #117 (#116)

## Context

From 2026-10-08 every change was committed straight to `master`, reviewed locally before the push
([[code-review]]). On 2026-11-09 Dependabot started opening update PRs, and CI got a `pull_request` trigger for
them ([[ci]]). The same day the user decided that pull requests should be the normal way every change lands
(#116). The repo is private on GitHub's free plan, so branch protection isn't available: the API answers
`403 Upgrade to GitHub Pro or make this repository public`, checked 2026-11-09. (Since 2026-11-23 the repo is public
and `master` is protected: [[2026-11-23-public-repository]].)

## Options considered

- **Merge method.** Rebase merge keeps each focused commit with its wiki update and `Fixes`/`Refs` line, and
  keeps history linear. Squash merge makes one commit per PR and loses the per-commit tie between code and wiki
  update. A merge commit keeps the commits but makes history non-linear.
- **Who merges an agent's PR.** The agent, once its gates are green, or the user, with the agent stopping at an
  open, reviewed, green PR.
- **Who handles Dependabot PRs.** The user alone; the agent reviews and the user merges; or the agent reviews and
  merges.

## Decision

- Every change goes through a branch off `master` and a PR that closes its issue. Nothing is committed to `master`
  directly.
- PRs are **rebase-merged** (`gh pr merge --rebase --delete-branch`), so each commit must build and pass the gate
  on its own.
- `/next-task` **merges its own PR** when every gate is green: the independent reviewer reports no blocker or major,
  it really ran on Sonnet at high effort, the local gate passes, and CI is green on the PR's head commit.
- `/next-task` now confirms the plan with the user before any code is written: a read-only `plan-critic` subagent
  (`.claude/agents/plan-critic.md`) checks it first, and the plan names the seam each test goes through ([[tdd]]).
- Dependabot PRs are reviewed **and merged by the agent** with `/dependency-pr`; a bump that needs a joint upgrade
  waits for that upgrade's issue ([[ci]] → Dependabot). Since 2026-11-10 the command may also rebase a Dependabot
  branch onto `master` and force-push it, when Dependabot ignores a `recreate` or the PR conflicts with `master`:
  without it such a PR can't be finished (#114, [[ci]] → Dependabot).
- Without branch protection, "merge only after green CI and review" is a rule the commands follow, not something
  GitHub enforces. Superseded for CI on 2026-11-23: GitHub now requires the `test` check ([[code-review]]).

## Why

- **The user's call** (2026-11-09): Dependabot already delivers changes as PRs, and one way for every change is
  simpler than two.
- **Rebase merge, not squash:** the wiki rule that a wiki update lands in the same commit as the code that
  triggered it ([[CLAUDE]]) only survives if commits survive. The user picked it over squash and merge commits.
- **The agent merges:** the user picked it. It keeps `/next-task` as autonomous as it was when it pushed to
  `master`; the PR adds a CI run before the merge and a place to read the change afterwards.
- **A plan critic before the code:** the review used to be the first independent look, after all the code was
  written. A plan that tests the wrong seam or misses a criterion is cheaper to fix before implementation. The
  seams go to the user because a test's seam decides what the test can prove ([[tdd]], "Seams").

## Consequences

- The commit SHAs on a branch differ from the ones on `master` after the merge (GitHub rewrites them as it
  rebases). Issue comments and wiki pages cite the `master` SHAs.
- CI runs twice per change: on the PR (the merge commit) and on `master` after the merge.
- `Fixes #N` closes the issue when the PR merges, not on a push.
- Wiki pages written before 2026-11-09 say the repo has no PRs; that was true for their history.
