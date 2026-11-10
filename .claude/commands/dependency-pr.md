---
description: Review Dependabot's open PRs (or one) and merge, hold for a joint upgrade, or rebase each
argument-hint: "[PR #n]"
---

Handle Dependabot's pull requests for LoyScan. Target: `$ARGUMENTS` (empty: every open Dependabot PR,
`gh pr list --author app/dependabot --state open`). The rules and why: `wiki/workflows/ci.md` → Dependabot. Running
this command authorizes, for those PRs only: merging, commenting, and pushing a wiki commit onto a Dependabot
branch. Anything else outward-facing (new issues, other PRs) needs my go-ahead.

## 0. Preflight

`git fetch origin`; the working tree must be clean. Note the branch you started on: return to it at the end.

## 1. For each PR, oldest first

1. **Read it.** `gh pr view <n> --comments` (the bump, the release notes and changelog Dependabot quotes) and
   `gh pr checks <n>`.
2. **Sort it into one case:**
   - **CI failed before the build ran** (the Gradle download, a runner or network problem; read
     `gh run view <id> --log-failed`) → comment `@dependabot rebase` and go to the next PR. A rerun from the
     Actions tab would reuse the old merge commit and the old workflow. A `@dependabot rebase` over an hour old with no
     newer commit (`gh pr view <n> --json comments,commits`) means Dependabot ignored it → comment
     `@dependabot recreate` instead. A `recreate` that got the same treatment → report the PR to me.
   - **CI failed because the bump needs other bumps at the same time** (the coupling rules in
     `wiki/workflows/build-and-run.md` → Dependencies, e.g. Kotlin / Hilt / AGP / Gradle) → find the open issue for
     that joint upgrade (`gh issue list --state open`; #115 is the Kotlin / Hilt / AGP / Gradle one) and comment
     `Waits for #<issue>: <one line from the failing log>.` Leave the PR open. No such issue → ask me whether to open one.
   - **CI failed in the build or the tests otherwise** → diagnose it (the `diagnosing-bugs` skill). The fix
     goes in its own PR through `/next-task`, so leave this PR open and report it to me.
   - **CI is green** → review it (step 3).
3. **Review** by `.claude/commands/review-changes.md` with the PR as target (it checks the PR out). A Dependabot PR
   has no issue: in place of the criteria, check
   - the release notes and changelog between the two versions, for breaking changes and behaviour changes that
     touch how LoyScan uses the library (search the code for its API)
   - the coupling rules in `wiki/workflows/build-and-run.md` → Dependencies (KSP and serialization with Kotlin,
     `coroutinesTest` with the resolved coroutines version, ...)
   - wiki pages that name the old version (`grep -rn "<old version>" wiki/`)
4. **Act on the review:**
   - A blocker or major (a breaking change that needs code, a coupling CI doesn't catch) → leave the PR open and
     report it to me with the evidence.
   - Wiki pages name the old version → commit their update on the PR's branch (`gh pr checkout <n>`), with the
     `wiki/_log.md` line, and push it. Dependabot stops rebasing a branch someone else pushed to, so finish this PR
     in this run: wait for CI on the new head (`gh pr checks <n> --watch`), and merge when it is green.
   - Then, or with nothing to update → merge: `gh pr merge <n> --rebase`.

## 2. Finish

Switch back to the branch you started on. After any merge, watch the CI run on `master` (`gh run watch`); a red
`master` comes first, so investigate and report it. Report each PR: merged (and the `master` SHA), waiting (for
which issue), rebased, or left open for me (and why).
