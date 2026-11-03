# Continuous integration (GitHub Actions)

Every push to `master` runs the unit tests and the Kover coverage gate on GitHub Actions. A red run means
`master` is broken, so fix it before pushing anything else. Workflow: `.github/workflows/tests.yml` (added for #29).

## What runs

One job, `test`, on `ubuntu-latest`, with JDK 21 (Temurin). It has a 30-minute timeout and starts:
- on every push to `master`
- by hand from the Actions tab, or `gh workflow run tests.yml --ref master`

A newer push to the same ref cancels a run that is still going.

The single build step:

```sh
./gradlew --continue testDebugUnitTest koverVerify koverHtmlReportDebug koverXmlReportDebug
```

`--continue` makes Gradle still write the coverage report when the gate fails (a failing test skips it).

Caches:
- Gradle user home, through `gradle/actions/setup-gradle` (which also validates the wrapper)
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

- **No emulator job.** Every test runs on the JVM via Robolectric ([[2026-10-12-tdd-and-coverage]]), and there is
  no `androidTest` source set. The repo is private, so Actions minutes count against a quota, and an emulator
  job would cost several times this job. Add one only for a test that needs a real device (e.g. camera, #4), and
  run it only when that code changes.
- **Push to `master` only, no `pull_request` trigger.** Work lands directly on `master` ([[code-review]]), so
  CI is a safety net after the push. The local gate in [[tdd]] and `/review-changes` are what keep red commits off
  `master`.
- **Basic Gradle caching** (`cache-provider: basic`). `setup-gradle` v6 defaults to a proprietary "enhanced"
  cache, which is a free preview for private repos with pricing announced for later. The basic provider is MIT and
  uses `actions/cache`.
- **`gradlew` is executable in git** (`git update-index --chmod=+x gradlew`). On Windows the bit isn't visible,
  but the Linux runner needs it.
- Action versions are pinned to major tags (`checkout@v7`, `setup-java@v6`, `setup-gradle@v6`, `cache@v6`,
  `upload-artifact@v7`). Run annotations warn when a major is deprecated; bump it then.
