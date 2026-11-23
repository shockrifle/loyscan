# Continuous integration (GitHub Actions)

Every pull request into `master`, and every push to it (a merged PR), runs the unit tests, the Kover coverage
gate and lint on GitHub Actions, unless the run's first step finds it needs no gate: a PR that changes only wiki pages,
or a push to `master` of a tree a green PR run already tested (below, #199). A PR is merged only when its run is green ([[code-review]]). A red run on `master` means
`master` is broken, so fix it before merging anything else. Workflow: `.github/workflows/tests.yml` (added for #29).
GitHub enforces the gate: `master` is protected and requires the job's check, `test` (the job name, not the workflow
name `Tests`), on a branch up to date with `master`, for admins too ([[2026-11-23-public-repository]]).

## What runs

One job, `test`, on `ubuntu-latest`, with JDK 21 (Temurin). It has a 30-minute timeout and starts:
- on every push to `master`
- on every pull request into `master`, on the PR's merge commit (GitHub's merge of the PR's head into the
  current `master`), when the PR opens and on each push to its branch
- by hand from the Actions tab, or `gh workflow run tests.yml --ref master`

Which runs cancel which (#119):
- **A pull request's runs:** a newer push to the PR's branch cancels that PR's run that is still going; only its
  latest commit matters. Their concurrency group is `tests-refs/pull/<n>/merge`, one per PR.
- **Runs on `master` (a merged PR) and manual runs:** never cancelled. Each has a group of its own (keyed by its run
  id), so two merges in quick succession get one run each. These runs can overlap: when watching `master` after a
  merge, pick the run for the merged head's SHA, not just the latest run. A run that skipped the gate is green in
  under a minute; its Decide step's log names the PR run that tested the tree.

**Decide** comes first, right after the checkout (`fetch-depth: 2`): `.github/scripts/ci-gate.sh` writes `run=true` or
`run=false`, and every later step has `steps.gate.outputs.run == 'true'` in its `if:`. The job itself always runs, so
on `run=false` the required `test` check is green after about half a minute, not "skipped" ([[2026-11-23-ci-run-trims]]):
- **A pull request** whose changed files (`git diff --no-renames --name-only HEAD^1 HEAD`: the checkout is GitHub's merge
  commit and its first parent the base) are all under `wiki/` → `run=false`. Not when one of them is a page a test
  reads (`wiki/integrations/google-play.md`, read by `StoreListingTest` and `PrivacyPolicyTest`), nor when the list is
  empty or the base can't be read. A file moved into `wiki/` counts by its old path too.
  Every other PR runs the gate. It also writes `marker=true`, unless a changed file is one the cache keys are made of
  (`*.gradle*`, `gradle.properties`, anything under `gradle/` or `buildSrc/`, `versions.properties`, `tests.yml`,
  `ci-gate.sh`, `robolectric-jars.sh`).
- **A push to `master`** → `run=false` when a green PR run of this workflow already tested the same tree. The PR run's
  last checking step, **Record the tested tree**, uploads an artifact named `tested-tree-<git tree SHA>` (kept 14 days,
  `continue-on-error`) when the run is green, `marker=true` and **Save Robolectric jars** was skipped. The push looks
  that name up (`gh api repos/<repo>/actions/artifacts?name=tested-tree-<tree>`, with `jq`; the job's token has
  `actions: read` and `contents: read` only) and skips only for an unexpired artifact whose run is a completed,
  successful `pull_request` run of `.github/workflows/tests.yml` with its head in this repository (not a fork). A
  rebase-merge of a PR that was up to date with `master` lands exactly the tree its run tested, and `master` requires
  PRs to be up to date, so a merged PR's push normally skips. An API error or anything unexpected → `run=true`.
- **A manual run** (and any other event) → `run=true`.

Caches stay fresh across a skipped push: the PR changed nothing a key is made of and saved no new Robolectric jars, so
the entries `master` already has are the ones the next PR run asks for (Caches, below). A PR that changes a key's
inputs (every Dependabot Gradle PR) leaves no marker, so its push runs in full and saves the new entries.

`.github/scripts/check-ci-gate.sh` checks those rules case by case in throwaway repos (step **CI gate checks**), and
`TestsWorkflowTest` (in the gate) checks the wiring: the triggers, Decide right after the checkout, every later step
gated with no `||` around the gate, and no `if:` on the job.

The single build step:

```sh
./gradlew --continue testDebugUnitTest koverVerify koverHtmlReportDebug koverXmlReportDebug
```

`--continue` makes Gradle still write the coverage report when the gate fails (a failing test skips it).

A **Lint** step follows: `./gradlew lintDebug`. A lint error fails the run, warnings don't (#126). Then
**Minified release build**: `./gradlew assembleRelease compileSmokeAndroidTestKotlin`.
R8 must succeed on release, and the on-device smoke test must compile. The smoke test itself doesn't run on CI
([[2026-11-05-r8-smoke-test-on-a-device]], #73).

**Release build checks** runs `.github/scripts/check-release-build.sh` (#74): the version scheme through what Gradle
resolves (`./gradlew -q :app:printVersion -Ployscan.versionName=…`), hand-worked values (1.2.3 → 10203, 0.9.1 → 901)
and names the build must reject (`1.0`, `v1.0.0`, `1.100.0`) ([[2026-11-17-release-versioning]]). Then signing,
with a throwaway PKCS12 key made in a temp directory: with the key alias missing, the build must stop and name only
`LOYSCAN_UPLOAD_KEY_ALIAS`; with all three set, `bundleRelease` must make an AAB that `jarsigner -verify` accepts,
signed by exactly that key (SHA-256), and the password (a marker) must appear in neither log
([[2026-11-17-release-signing-on-ci]]). The signed `bundleRelease` reuses the R8 output of the step before; the
whole script took about 5 minutes locally (2026-11-17). It runs locally in
Git Bash too. **Release wizard checks** runs `.github/scripts/check-release-wizard.sh`: the release-signing wizard with its answers
piped in, in a sandbox `HOME`, with no browser or `gh` on its `PATH`. A first run must make a key the password opens
and write the three properties without printing the password; a keystore path inside the repo must be refused; a new
keystore path must ask for a new password rather than keep the saved one (a rotation, [[leaked-signing-secret]]).
**Shellcheck** then lints `.github/scripts/*.sh`, the wizards in `scripts/` and the `wizard` skill's template (shellcheck is preinstalled on `ubuntu-latest`; it isn't
part of the local gate).

`check-release-build.sh` builds the signed AAB through `.github/scripts/release-bundle.sh`, the script the Release
workflow runs, and first checks that it refuses a tag that isn't the build's version.

**Release workflow** (`.github/workflows/release.yml`, #74): a separate workflow, run by pushing a `vX.Y.Z` tag. It
checks the tagged commit is on `master` (a guard against mistakes, not a security boundary: [[release]] → Who can
reach the secrets), decodes the upload keystore from the Actions secrets into the runner's temp
directory, runs `release-bundle.sh X.Y.Z`, deletes the keystore, and creates a draft GitHub release with the AAB and
`mapping.txt`. No Gradle cache: a release builds from scratch. `ReleaseWorkflowTest` (in the gate) checks its rules:
secrets only as `env:` entries, nothing that echoes or traces, a `v*` trigger, a draft. How to cut a release:
[[release]].

Before the build, a **Gradle distribution** step runs `./gradlew --version`, up to three times, 30 seconds apart. The
wrapper downloads the distribution on a cache miss and tries only once.

Caches:
- Gradle user home, through `gradle/actions/setup-gradle` (which also validates the wrapper). Its key is a hash of
  every Gradle file, `gradle/libs.versions.toml` included, and it matches exactly. A PR that changes one of
  them (every Dependabot Gradle PR) restores nothing and starts cold. Only runs on `master` save an entry; a PR run
  reads it.
- The Gradle distribution (`~/.gradle/wrapper/dists`), keyed on `gradle/wrapper/gradle-wrapper.properties` only,
  so a PR that leaves the wrapper alone should restore it from `master`'s entry (a PR run can read caches saved
  on its base branch, by GitHub's cache scoping; not yet seen on a run here, #116). On 2026-11-09 all five Dependabot PR runs
  started cold and downloaded the distribution themselves. Two failed on that download, before any build: #113 on
  a read timeout (the wrapper's `networkTimeout` is 10 s, run 42583614640) and #111 on HTTP 504 from GitHub's
  release download (run 42583570631); recreated, #113 passed and #111 failed on the coupling too. The other three
  got Gradle and failed on the version coupling in #115. The `master` runs restored setup-gradle's cache and never
  downloaded it (#116). A wrapper bump (like #114) still misses this cache, and the retry step is all it has.
- Robolectric's downloaded `android-all` jars (`~/.m2/repository/org/robolectric`), one per SDK the tests run on:
  `targetSdk` and each SDK a test pins with `@Config(sdk = [N])`. The key is `robolectric-jars-<hash of
  gradle/libs.versions.toml>-<hash of the jar list>`, made by `.github/scripts/robolectric-jars.sh` (#94). **Restore
  Robolectric jars** takes the newest entry for this toml hash, and **Robolectric jars before the tests** prints what
  it restored. A test that needs a jar the entry lacks downloads it, **Robolectric jars after the tests** lists it, and
  **Save Robolectric jars** stores the new set under its own key; the next run restores it. When the two lists match,
  nothing was downloaded and the save step is skipped. A toml change (a Robolectric bump) starts this cache cold.

**Runtime** (the runs cited here and elsewhere in the wiki were deleted on 2026-11-23, before the repo went public;
their IDs remain as records. Job time, measured 2026-11-12; the 2026-10-12 figures of 2 and 5 minutes predate lint and the minified
build): about **10–14 minutes with a warm Gradle cache** (587–851 s over six runs on 2026-11-11 and 2026-11-12, e.g.
43191093845, 43189465936, 43051061277). The unit tests and coverage gate take 4–5½ minutes, lint 1½–2¼ and the minified
release build 3½–6. **Cold**, when setup-gradle's cache key changes (a dependency bump), no run with the lint step has
been measured yet. The last cold runs, on 2026-11-10 before lint was added, took 685–854 s (42783746385,
42785399324, 42785742119). With lint added, expect roughly 13–17 minutes (an estimate). Run 45176725551 (2026-11-23,
`master`, Gradle home not restored) took 12½ minutes.

**Minutes per change** (#199, estimates from the figures above; a skipped run is the checkout, the Decide step and
the job's setup, under a minute):
- A PR: one full run per push to its branch (a newer push cancels the older run), plus a skipped run on `master` after
  the merge. Before #199 the `master` run was a second full run.
- A wiki-only PR: a skipped run per push, plus one full run on `master` after the merge (nothing recorded its tree).
- A PR changing a cache key's inputs (a dependency bump): its runs, plus a full run on `master`, as before.

## Where the reports are

On the run's page (Actions → Tests → the run), under **Artifacts**, kept for 14 days:
- `coverage-report`: Kover HTML (`htmlDebug/index.html`) and XML. Uploaded on every run, including failed ones.
- `test-reports`: the Gradle test HTML report, the JUnit XML and lint's report (`lint-results-debug.html`, also
  `.txt`, `.xml`, `.sarif`). Uploaded only when the run fails.

With `gh`:
- `gh run list --limit 5`
- `gh run view <id> --log-failed`
- `gh run download <id> -n coverage-report`

## Reproduce a failure locally

Run the gate (root `CLAUDE.md`) with `JAVA_HOME` set to Android Studio's JBR (see [[build-and-run]]). It is JDK 25
since 2026-11-18 while CI uses 21; a failure that only CI shows can also come from that difference.

CI uses Linux and Temurin, while local builds use Windows and the JBR. A test that passes locally but fails only
in CI usually depends on the platform: path separators, line endings (`core.autocrlf` gives Windows checkouts
CRLF), the default time zone or locale, or timing in Robolectric/Compose tests. Read the `test-reports` artifact
for the failing assertion before changing the test.

It goes the other way too: a test that reads a checked-in file's text passes on CI and fails on Windows if the file
comes out with CRLF. `.gitattributes` pins LF for `*.sh` (bash breaks on CRLF) and for `.github/workflows/*.yml`,
whose text `ReleaseWorkflowTest` compares with `\n`-only strings. Until 2026-11-17 the workflows had no rule, and that
test failed only in the local gate, with *"line contents match, but line-break characters differ"* (#170). A new
test that reads a file's text needs the same rule for that file, or a test that normalises `\r\n`. After adding a rule,
delete the files and check them out again (`git checkout -- <path>`), since git doesn't rewrite a clean working copy.

## Choices and constraints

- **No emulator job.** Every test runs on the JVM via Robolectric ([[2026-10-12-tdd-and-coverage]]), except the
  minified build's smoke test, which runs by hand on a device ([[2026-11-05-r8-smoke-test-on-a-device]]). An emulator job would take several times this
  job's time. (Until 2026-11-23 the repo was private and Actions minutes were capped; they ran out in November 2026,
  #199. Public repos' minutes are free.) Add one only for a test that needs a real device (e.g. camera, #4), and
  run it only when that code changes.
- **Pull requests from forks wait for approval.** A workflow run on an outside contributor's PR starts only after a
  maintainer approves it (`approval_policy: all_external_contributors`, set 2026-11-23). Fork PRs get no secrets either way.
- **Pull requests into `master`, and pushes to it.** Every change lands through a PR
  ([[2026-11-09-pull-request-workflow]]), and the PR's green run is a merge gate. The run on `master` after a merge
  checks the rebased commits on the `master` they landed on, unless a green PR run already tested that same tree
  (#199, [[2026-11-23-ci-run-trims]]). Until 2026-11-09 work was pushed straight to `master`,
  and CI ran only after the push. The `pull_request` trigger was added that day for Dependabot's PRs (`2fb04af`).
- **`master` runs are never cancelled** (#119). Until 2026-11-10 every run shared the group `tests-<ref>` with
  `cancel-in-progress: true`, so a second merge cancelled the first merge's run on `master` (runs 41074550651 and
  41406854925), and that commit was never checked there. `cancel-in-progress: false` alone wouldn't do: a group holds
  one running and one pending run, and a newer run replaces the pending one, so a third quick merge would still
  cancel the second's run. Hence a unique group per non-PR run. The cost: overlapping `master` runs use more Actions
  minutes, and both may try to save the same cache key, which `actions/cache` only warns about.
- **Lint runs on CI, `lintDebug` only** (#126). Until 2026-11-11 CI ran no lint, and `./gradlew check` failed on
  `master` with a lint error nobody saw (`LocalContextGetResourceValueCall`, `DetailScreen.kt`). The step fails on
  errors only; the 23 warnings at the time (unused resources, newer dependency versions, …) don't. One variant:
  `lintRelease` and `lintSmoke` lint the same sources and reported the same single error, and each variant costs
  about a minute. It runs after the tests, so a test failure is reported first, and before the release build. It is
  also in the local gate ([[tdd]]). The cost: about a minute per run, and a dependency bump (AGP, androidx) can add
  an error-level check, so a Dependabot PR can turn red on lint and need a code fix before it merges.
- **The Robolectric cache is keyed on its jars** (#94). Until 2026-11-11 the key was the toml hash alone, and
  `actions/cache` saves nothing on a key hit, so a jar for a newly pinned SDK was cached only once the toml next
  changed; until then every run downloaded it (SDK 29's jar, #92, was cached only because that commit also changed the
  toml). Now the key follows the set of jars, with restore and save as separate steps. Saved only after green tests,
  so a jar cut short by a failing run is never stored; the next green run saves it. Restore takes the newest entry
  for the toml hash, not the largest, so a PR-scope entry can hide a newer one on `master` and the run downloads and
  saves again. No fallback to an older toml's entries, so jars of an old Robolectric version don't pile up. The cost:
  one entry (about 450 MB) per new set and scope, and a jar a test no longer pins stays in the set until the toml
  changes.
- **Basic Gradle caching** (`cache-provider: basic`). `setup-gradle` v6 defaults to a proprietary "enhanced"
  cache, which is a free preview for private repos with pricing announced for later. The basic provider is MIT and
  uses `actions/cache`.
- **`gradlew` is executable in git** (`git update-index --chmod=+x gradlew`). On Windows the bit isn't visible,
  but the Linux runner needs it.
- Action versions are pinned to major tags (`checkout@v7`, `setup-java@v6`, `setup-gradle@v6`, `cache@v6`,
  `cache/restore@v6`, `cache/save@v6`, `upload-artifact@v7`). Dependabot proposes new majors (below).

## Dependabot

`.github/dependabot.yml` opens update PRs once a week, at most 5 open at a time for Gradle:
- **Gradle** (`gradle/libs.versions.toml`). Kotlin, the serialization plugin and KSP come as one grouped PR, because
  their versions are coupled ([[build-and-run]] → Dependencies); all `androidx.*` libraries come as another. Hilt,
  AGP and the Gradle wrapper come one PR each, though a Kotlin minor needs a Hilt release that reads its metadata
  and new androidx releases can need a newer AGP and compileSdk; such a PR fails alone and waits (below).
  `kotlinx-coroutines-test` is ignored: it must match the coroutines version the app resolves transitively, which
  Dependabot can't see, so bump it by hand when that changes.
- **GitHub Actions** (the `uses:` versions in the workflows), all in one PR.

Each PR runs the Tests workflow. A Gradle bump changes setup-gradle's cache key and the toml part of the Robolectric one, so its run
starts cold (about 5 minutes); the Gradle distribution still comes from its own cache unless the wrapper itself is
bumped (Caches, above).

**Handling them** (decided 2026-11-09, [[2026-11-09-pull-request-workflow]]): the agent reviews and merges them with
`/dependency-pr [PR #n]` (`.claude/commands/dependency-pr.md`). Each PR ends up in one of these:
- **Merged** (rebase) when CI is green and the review finds no blocker or major. The review reads the release notes
  for changes that touch how LoyScan uses the library, checks the coupling rules in [[build-and-run]] →
  Dependencies, and greps the wiki for the old version. A page that names it is updated in a commit pushed onto the
  Dependabot branch before the merge.
- **Waiting** when the bump fails because it needs other bumps at the same time (Kotlin, Hilt, AGP, Gradle and
  compileSdk: #110, #111, #112 and #114 waited for #115, which landed their versions). It stays open with a comment
  naming the joint-upgrade issue. Once that upgrade lands, Dependabot closes a PR whose version is already reached on
  its next run (Dependabot's documented behaviour, not yet seen here).
- **Rebased** (`@dependabot rebase`) when CI failed before the build ran, e.g. on the Gradle download. Dependabot
  rebuilds the branch on the current `master`, so the run uses `master`'s workflow; a rerun from the Actions tab
  would reuse the old merge commit. If Dependabot ignores it, `@dependabot recreate` (rebuilds the branch from
  scratch, dropping any pushed commits): on 2026-11-09 a `rebase` on #111 and #113 started no update job in 3 hours,
  and `recreate` started one within minutes (the cause wasn't visible). On 2026-11-10 a `recreate` on #114 was
  ignored too. An accepted command gets a 👍 reaction from Dependabot and an update job within seconds; the ignored
  ones got neither, and no GitHub incident covered those times. So when a `recreate` is ignored as well,
  `/dependency-pr` rebases the branch onto `master` itself and force-pushes it (below).
- **Left for the user** when the review finds a breaking change that needs code, or the build fails for another
  reason. The fix goes through its own issue and PR. Dependabot labels its PRs
`dependencies`, a label outside the issue vocabulary in [[CLAUDE]]; it applies to PRs only.

**Rebasing a Dependabot branch ourselves** (2026-11-10). `/dependency-pr` may rebase a Dependabot branch onto
`master` and `git push --force-with-lease` it, in two cases: Dependabot ignored a `recreate` (above), or the PR
conflicts with `master`. The second comes from a wiki commit pushed onto the branch: GitHub's merge ignores the
`merge=union` driver for `wiki/_log.md` (`.gitattributes`), so a branch with a `_log` line conflicts as soon as
`master` adds one, and CI no longer runs on it (#114). Dependabot doesn't rebase a branch someone else pushed to, so
the command rebases it and finishes the PR in the same run, review included. Locally the union driver merges
`_log.md` without stopping but puts `master`'s new lines first, so the branch's newer line is moved back to the
top.
