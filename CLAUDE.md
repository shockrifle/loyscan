# CLAUDE.md

## Project knowledge base — `wiki/`

`wiki/` is an LLM-maintained knowledge base for this project. It captures everything the code
alone doesn't show: architectural patterns, decisions and their rationale, bug postmortems, known
hacks and tech debt, integration contracts, runbooks, in-flight projects, and the domain glossary.

**On every non-trivial task in this project, before starting work:**

- Read `wiki/CLAUDE.md` — the schema. It defines when to update the wiki and how to write
  pages. Treat the schema's "When to update" list as a checklist for the current task.
- Skim `wiki/index.md` — the map of content. Find the pages relevant to the area you're
  touching and read them. The wiki is the fastest way to absorb context prior sessions spent hours
  learning.

**While working:**

- If a decision is made, a non-obvious bug is fixed, a hack is added, an integration changes, a
  domain term comes up, or an incident is handled — create or update the relevant wiki page per the
  schema, and append a one-line entry to `wiki/_log.md`. Wiki updates land in the same commit
  as the code change that triggered them, so `git log` ties them together.
- The wiki is outside the Gradle build, and no formatter is configured; if one is ever added, exclude
  `wiki/` from it (see the schema's tooling boundaries).

**For new feature work:**

`wiki/architecture.md` contains a worked end-to-end example showing the layered pattern every
new feature follows (Room entity/DAO → mapper → repository → `@HiltViewModel` → Compose screen +
type-safe route/Directions). Use it as the template. Each layer there starts with its test.

## Project quick facts

- Single-module Android app (`:app`), Kotlin 2.4 + Jetpack Compose (Material 3), Hilt + Room via KSP,
  Navigation Compose with `@Serializable` routes, Coil 2 for images, Timber for logging.
- Build: `./gradlew assembleDebug` · install: `./gradlew installDebug`. Versions live in `gradle/libs.versions.toml`.
  Gradle needs JDK 17+. CI uses JDK 21, as does Android Studio's JBR (`/c/Program Files/Android/Android Studio/jbr`),
  the safe `JAVA_HOME` for a terminal build — see `wiki/workflows/build-and-run.md`.
- **Test-first.** Write the failing test before the code, and see it fail. A bug fix starts with a failing
  regression test. **The gate**, `./gradlew testDebugUnitTest koverVerify lintDebug`, must pass before committing; raise the Kover
  `minBound` when coverage rises. How: `wiki/workflows/tdd.md`; why: `wiki/decisions/2026-10-12-tdd-and-coverage.md`.
- Remote: GitHub `shockrifle/loyscan` (`origin`, SSH). **Changes land through pull requests**: a branch off `master`,
  a PR that closes the issue, review, rebase-merge after green CI; nothing is committed to `master` directly. How
  and why: `wiki/workflows/code-review.md`. The repo-local `core.sshCommand` points at the user's SSH key, so plain
  `git push` / `git pull` work.
- Work is tracked in GitHub Issues + milestones (`gh issue list`); reference issues in commits (`Fixes #N`).
  Issues hold what/status, the wiki holds why/how — see "GitHub Issues vs this wiki" in `wiki/CLAUDE.md`.
  The repo is private but treated as public: nothing personal or third-party-proprietary in issues/commits.
