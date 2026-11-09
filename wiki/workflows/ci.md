# Continuous integration (GitHub Actions)

Every push to `master`, and every pull request into it, runs the unit tests and the Kover coverage gate on GitHub Actions. A red run means
`master` is broken, so fix it before pushing anything else. Workflow: `.github/workflows/tests.yml` (added for #29).

## What runs

One job, `test`, on `ubuntu-latest`, with JDK 21 (Temurin). It has a 30-minute timeout and starts:
- on every push to `master`
- on every pull request into `master` (Dependabot's, see below), on the PR's merge commit
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
  so a PR that leaves the wrapper alone restores it from `master`'s entry. On 2026-11-09 all five Dependabot PR runs
  started cold and downloaded the distribution themselves. Two failed on that download, before any build: #113 on
  a read timeout (the wrapper's `networkTimeout` is 10 s, run 42583614640) and #111 on HTTP 504 from GitHub's
  release download (run 42583570631). The other three got Gradle and failed on the version coupling in #115. The
  `master` runs restored the cache and never downloaded it (#116). A wrapper bump (like #114) still misses this
  cache, and the retry step is all it has
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
- **Push to `master`, and pull requests into it.** Work has landed directly on `master` ([[code-review]]), with
  CI as a safety net after the push; the local gate in [[tdd]] and `/review-changes` keep red commits off `master`.
  The `pull_request` trigger was added on 2026-11-09 so Dependabot's PRs are tested before they are merged; the
  user decided the same day to move all work to pull requests, and the review commands will be changed for that
  separately.
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

Each PR runs the Tests workflow. Merge it once CI is green and the change has been reviewed. A Gradle bump
changes the Robolectric cache key, so its run is a cold one (about 5 minutes). Dependabot labels its PRs
`dependencies`, a label outside the issue vocabulary in [[CLAUDE]]; it applies to PRs only.
