# CI runs only what a change needs

**Status:** Active · **Date:** 2026-11-23 · **Issue:** #199

The Tests workflow decides in its first step whether the run needs the gate ([[ci]] → What runs). A pull request that
changes only wiki pages gets a green `test` check without Gradle.

## Context

One Tests run takes about 13–17 minutes ([[ci]] → Runtime). #199 was opened when the repo was private and the monthly
Actions minutes had run out. The repo went public the same day and its minutes are free
([[2026-11-23-public-repository]]); the owner chose to make the trims anyway, for faster checks and fewer runs.

`master` requires the `test` check ([[code-review]]), so a PR must get that check whatever it changes.

## Options considered

- **`paths-ignore: wiki/**` on the `pull_request` trigger.** No run starts, so the required check never reports and a
  wiki-only PR can't merge.
- **A job-level `if:`.** A skipped job counts for branch protection but shows as "skipped", not green, and needs a
  separate job to look at the diff first.
- **A first step that decides, and an `if:` on every later step.** The job runs and goes green in about half a minute.

## Decision

The third: `.github/scripts/ci-gate.sh` writes `run=true|false`; every later step requires it. `TestsWorkflowTest` fails
on a step without the gate, so a new step can't be added ungated by mistake.

## Why

The required check stays the same single check for every PR, and the rules live in one script that a bash check
(`check-ci-gate.sh`) runs case by case.

## What each trim gives up

- **A wiki-only PR merges unchecked by the tests that read the wiki.** `PublicSafeRepoTest` reads every tracked file,
  wiki pages included, so a wiki PR with a personal path or email address merges green and turns `master` red on its
  push run, which still runs the gate. `wiki/integrations/google-play.md` is exempt (a change to it runs the gate),
  since `StoreListingTest` and `PrivacyPolicyTest` check it names the tests behind Play's claims. A new test that reads
  a wiki page must add it to `tested_wiki` in `ci-gate.sh`.
