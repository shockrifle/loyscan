# Continuous integration (GitHub Actions)

Every pull request into `master`, and every push to it (a merged PR), runs the unit tests and the Kover coverage
gate on GitHub Actions. A PR is merged only when its run is green ([[code-review]]). A red run on `master` means
`master` is broken, so fix it before merging anything else. Workflow: `.github/workflows/tests.yml` (added for #29).

## What runs

One job, `test`, on `ubuntu-latest`, with JDK 21 (Temurin). It has a 30-minute timeout and starts:
- on every push to `master`
- on every pull request into `master`, on the PR's merge commit (GitHub's merge of the PR's head into the
  current `master`), when the PR opens and on each push to its branch
- by hand from the Actions tab, or `gh workflow run tests.yml --ref master`

A newer push to the same ref cancels a run that is still going.

The single build step:

```sh
./gradlew --continue testDebugUnitTest koverVerify koverHtmlReportDebug koverXmlReportDebug
```

`--continue` makes Gradle still write the coverage report when the gate fails (a failing test skips it).

A second step, **Minified release build**, runs after it: `./gradlew assembleRelease compileSmokeAndroidTestKotlin`.
R8 must succeed on release, and the on-device smoke test must compile. The smoke test itself doesn't run on CI
([[2026-11-05-r8-smoke-test-on-a-device]], #73).

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
  release download (run 42583570631). The other three got Gradle and failed on the version coupling in #115. The
  `master` runs restored setup-gradle's cache and never downloaded it (#116). A wrapper bump (like #114) still misses this
  cache, and the retry step is all it has.
- Robolectric's downloaded `android-all` jars (`~/.m2/repository/org/robolectric`, keyed on
  `gradle/libs.versions.toml`). A cache hit saves nothing, so a jar for an SDK a test newly pins (`@Config(sdk = [N])`)
  is kept only once that file next changes; until then every run downloads it (#94)

**Runtime** (measured 2026-10-12): about 2 minutes with a warm Gradle cache (2m4s), and about 5 minutes cold
(4m57s–5m32s, the first run, or after the cache key changes, e.g. a dependency bump).

## Where the reports are

On the run's page (Actions → Tests → the run), under **Artifacts**, kept for 14 days:
- `coverage-report`: Kover HTML (`htmlDebug/index.html`) and XML. Uploaded on every run, including failed ones.
- `test-reports`: the Gradle test HTML report and the JUnit XML. Uploaded only when the run fails.

With `gh`:
- `gh run list --limit 5`
- `gh run view <id> --log-failed`
- `gh run download <id> -n coverage-report`

## Reproduce a failure locally

Run the same command with `JAVA_HOME` set to Android Studio's JBR 21 (see [[build-and-run]]):

```sh
./gradlew testDebugUnitTest koverVerify
```

CI uses Linux and Temurin, while local builds use Windows and the JBR. A test that passes locally but fails only
in CI usually depends on the platform: path separators, line endings (`core.autocrlf` gives Windows checkouts
CRLF), the default time zone or locale, or timing in Robolectric/Compose tests. Read the `test-reports` artifact
for the failing assertion before changing the test.

## Choices and constraints

- **No emulator job.** Every test runs on the JVM via Robolectric ([[2026-10-12-tdd-and-coverage]]), except the
  minified build's smoke test, which runs by hand on a device ([[2026-11-05-r8-smoke-test-on-a-device]]). The repo is private, so Actions minutes count against a quota, and an emulator
  job would cost several times this job. Add one only for a test that needs a real device (e.g. camera, #4), and
  run it only when that code changes.
- **Pull requests into `master`, and pushes to it.** Every change lands through a PR
  ([[2026-11-09-pull-request-workflow]]), and the PR's green run is a merge gate. The run on `master` after a merge
  checks the rebased commits on the `master` they landed on. Until 2026-11-09 work was pushed straight to `master`,
  and CI ran only after the push. The `pull_request` trigger was added that day for Dependabot's PRs (`2fb04af`).
- **Basic Gradle caching** (`cache-provider: basic`). `setup-gradle` v6 defaults to a proprietary "enhanced"
  cache, which is a free preview for private repos with pricing announced for later. The basic provider is MIT and
  uses `actions/cache`.
- **`gradlew` is executable in git** (`git update-index --chmod=+x gradlew`). On Windows the bit isn't visible,
  but the Linux runner needs it.
- Action versions are pinned to major tags (`checkout@v7`, `setup-java@v6`, `setup-gradle@v6`, `cache@v6`,
  `upload-artifact@v7`). Dependabot proposes new majors (below).

## Dependabot

`.github/dependabot.yml` opens update PRs once a week, at most 5 open at a time for Gradle:
- **Gradle** (`gradle/libs.versions.toml`). Kotlin, the serialization plugin and KSP come as one grouped PR, because
  their versions are coupled ([[build-and-run]] → Dependencies); all `androidx.*` libraries come as another.
  `kotlinx-coroutines-test` is ignored: it must match the coroutines version the app resolves transitively, which
  Dependabot can't see, so bump it by hand when that changes.
- **GitHub Actions** (the `uses:` versions in the workflows), all in one PR.

Each PR runs the Tests workflow. A Gradle bump changes the Robolectric cache key and setup-gradle's, so its run
starts cold (about 5 minutes); the Gradle distribution still comes from its own cache unless the wrapper itself is
bumped (Caches, above).

**Handling them** (decided 2026-11-09, [[2026-11-09-pull-request-workflow]]): the agent reviews and merges them with
`/dependency-pr [PR #n]` (`.claude/commands/dependency-pr.md`). Each PR ends up in one of these:
- **Merged** (rebase) when CI is green and the review finds no blocker or major. The review reads the release notes
  for changes that touch how LoyScan uses the library, checks the coupling rules in [[build-and-run]] →
  Dependencies, and greps the wiki for the old version. A page that names it is updated in a commit pushed onto the
  Dependabot branch before the merge.
- **Waiting** when the bump fails because it needs other bumps at the same time (Kotlin, Hilt, AGP and Gradle:
  #110, #112, #114 wait for #115). It stays open with a comment naming the joint-upgrade issue. Once that upgrade
  lands, Dependabot closes a PR whose version is already reached on its next run (Dependabot's documented
  behaviour, not yet seen here).
- **Rebased** (`@dependabot rebase`) when CI failed before the build ran, e.g. on the Gradle download. Dependabot
  rebuilds the branch on the current `master`, so the run uses `master`'s workflow; a rerun from the Actions tab
  would reuse the old merge commit.
- **Left for the user** when the review finds a breaking change that needs code, or the build fails for another
  reason. The fix goes through its own issue and PR. Dependabot labels its PRs
`dependencies`, a label outside the issue vocabulary in [[CLAUDE]]; it applies to PRs only.
