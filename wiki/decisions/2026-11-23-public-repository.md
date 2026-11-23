# The repository goes public, under the GPL

**Status:** Active · **Date:** 2026-11-23 · **Issue:** #200 · **Commits:** c265b04

The repository is public since 2026-11-23, licensed GPL-3.0-or-later. Before the switch the history, the issues and
PRs and the tracked files were swept for anything the public-safe rule ([[CLAUDE]], "Public-safe") forbids, and every
old Actions run was deleted. `PublicSafeRepoTest` keeps the tracked files that way. How to sweep again:
[[public-safe-sweep]].

## Context

The repo was private but written as if public from the start. A private repo's Actions minutes are capped per month,
and the cap ran out in November 2026: no run could start, so PRs couldn't meet their merge gate and `master` went
unchecked (#199). Public repos get Actions for free and can use branch protection, which the private plan didn't
offer ([[2026-11-09-pull-request-workflow]]).

## Options considered

- **Stay private and spend fewer minutes** (#199). Still useful, but it only stretches the quota.
- **Go public.** Costs a sweep, and decisions on what becomes public.

## Decision

Go public, with these owner decisions (2026-11-23):

- **Author email: accepted as it is.** Most commits carry the owner's personal address. Rewriting history again
  would change every SHA the wiki, issues and PRs cite. The 23 commits from before the 2026-10-08 rewrite ([[_log]])
  are unreachable but still served by SHA; they carry an earlier address and were accepted too (their content was
  swept and is clean).
- **Licence: GPL-3.0-or-later.** `LICENSE` is gnu.org's text byte for byte; the notice, with the copyright holder,
  is in `README.md`.
- **Old Actions runs: deleted, not scanned**, as the last step before the switch. Their logs and artifacts went with
  them, so the run IDs cited in [[ci]], [[tdd]] and old issues no longer open; they stay as recorded measurements.

## Why

Free CI, and branch protection to enforce the `Tests` gate, for a project that never held secrets in the repo. The
GPL keeps forks of the app open. Deleting the runs is complete in one step, where scanning 264 runs' logs would
still leave doubt.

## The sweep (2026-11-23)

| What | How | Result |
|---|---|---|
| Secrets in history | gitleaks over every commit of every branch, plus the 23 pre-rewrite commits; pattern greps for keys, tokens and keystores | No leaks: gitleaks 8.30.1 over 399 commits on 19 branches and the 23 pre-rewrite commits, and over the issue and PR dump |
| Personal details in history | Diffs and messages of all commits grepped for local user paths, email addresses and long digit runs; deleted files listed | Only placeholder paths (`C:/Users/…`), example or bot addresses; the deleted seed SQL holds demo numbers |
| Issues, PRs, comments, reviews | Dumped through `gh api` (558 texts) and grepped the same way, plus private IPs and secret-like assignments | Clean: the long numbers are CI run IDs and made-up card numbers; nothing was edited |
| Commit comments, releases, repo metadata, branch names | `gh api` | None or clean |
| Tracked files | `PublicSafeRepoTest`, and a read of `play/`, the catalogue and `.claude/` | Clean. Third-party files: the adapted skills (MIT, `.claude/skills/LICENSE-mattpocock-skills`, now also credited in the wizard template and the wizard it generated), the Gradle wrapper jar (Apache-2.0, Gradle's) |
| Actions runs | `gh api -X DELETE …/actions/runs/{id}` for each | All 266 runs (two more from this PR's pushes) and their artifacts deleted; 0 left. The Actions caches stay: only workflows read them |

## What the switch changed (2026-11-23)

- Actions minutes are free; runs start again (#199 is no longer blocking).
- `master` is protected: the `test` check is required on a branch up to date with `master`, for admins too; no
  force-push or deletion; no required review ([[code-review]], [[ci]]).
- Secret scanning with push protection, and Dependabot alerts, are on.
- Workflows on PRs from outside contributors wait for a maintainer's approval ([[ci]]).
- New commits use the GitHub noreply address as the author email.

## Consequences

- `PublicSafeRepoTest` fails on a tracked file with a local path naming a user or an email address outside
  `example.*`, GitHub noreply and `git@github.com`; on a file under `.claude/skills/` (or a generated wizard) that
  names no existing `LICENSE-*` file and isn't listed as own work; and on a `LICENSE` that isn't the GPL text.
  It can't see third-party prose pasted in or a name written out: those stay a review item.
- The `com.bdaniel.loyscan` package id and the author name in commits are public.
