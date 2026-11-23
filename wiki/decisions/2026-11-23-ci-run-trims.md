# CI runs only what a change needs

**Status:** Active · **Date:** 2026-11-23 · **Issue:** #199

The Tests workflow decides in its first step whether the run needs the gate ([[ci]] → What runs). A pull request that
changes only wiki pages gets a green `test` check without Gradle, and a push to `master` of a tree a green PR run
already tested skips the gate.

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

For knowing on `master` that a tree was already tested:
- **The PR head's tree** (find the merged PR through the API, compare its head's tree). A PR run tests GitHub's merge
  commit, not the head, so this is only the same tree when the branch was up to date at the run.
- **A record of the tested tree, made by the PR run itself.** The run knows the tree it tested (`HEAD^{tree}` of the
  merge commit) and whether it was green. An artifact is the record: the API finds it by name and gives its run.

## Decision

A first step that decides: `.github/scripts/ci-gate.sh` writes `run=true|false`, and every later step requires it.
`TestsWorkflowTest` fails on a step without the gate, so a new step can't be added ungated by mistake. And a record
made by the PR run: a green PR run uploads an artifact named `tested-tree-<tree>`, and a push to `master` skips when
one exists from a successful `pull_request` run of `tests.yml` whose head is in this repository.

## Why

The required check stays the same single check for every PR, and the rules live in one script that a bash check
(`check-ci-gate.sh`) runs case by case. A rebase-merge only rewrites commits, not the tree, and `master` requires a PR
to be up to date before it merges, so the push run would re-test exactly what the PR run tested: before #199, every
PR cost two full runs. Comparing trees asks nothing of the merge itself, so it holds whatever the merge method, and
any push whose tree no green run recorded still runs in full. The caches constraint in #199 shaped the marker: PR runs
restore the entries `master` runs save, so a PR that changes what a key is made of, or saves new Robolectric jars,
records nothing, and its push runs in full and saves them.

## What each trim gives up

- **A wiki-only PR merges unchecked by the tests that read the wiki.** `PublicSafeRepoTest` reads every tracked file,
  wiki pages included, so a wiki PR with a personal path or email address merges green and turns `master` red on its
  push run, which still runs the gate. `wiki/integrations/google-play.md` is exempt (a change to it runs the gate),
  since `StoreListingTest` and `PrivacyPolicyTest` check it names the tests behind Play's claims. A new test that reads
  a wiki page must add it to `tested_wiki` in `ci-gate.sh`.
- **`master` no longer checks the rebased commits one more time.** A skipped push trusts the PR run: the same tree on
  the same workflow. What it gives up: a check that depends on something besides the tree (a flaky test that would
  fail the second time, a dependency re-resolved from a moving version, the runner image changing in between), and
  the intermediate commits of a multi-commit PR, which no run ever checks on their own (they never were: a run checks
  only the pushed head).
- **A marker can be forged from a branch of this repo.** A PR's run uses the PR's own `tests.yml`, so a same-repo
  branch could upload `tested-tree-<tree>` without testing anything. Only maintainers push branches here; fork PRs'
  runs are ignored (their head repository differs) and wait for approval anyway ([[ci]]). The merged PR's own review
  would see such a workflow change.
- **A new cache key needs its inputs in `ci-gate.sh`.** The list of files the keys are made of is written out in the
  script (`cache_inputs`); a new cache, or a key over other files, must add them, or its `master` entry goes stale.
- **The Actions API is in the path of a `master` push.** An outage, a rate limit or a changed answer only makes the
  push run in full.
