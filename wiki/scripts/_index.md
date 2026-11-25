# Scripts

Notable scripts, CLIs and custom Gradle tasks: what each does, how to invoke it, inputs and outputs, and gotchas.

- [[reviewer-model]]: which model and effort a Claude Code subagent (the `/next-task` reviewer) really ran on.

- `:app:printVersion` (Gradle task, `app/build.gradle.kts`): prints `<versionName> <versionCode>` as Gradle resolved them
  (`./gradlew -q :app:printVersion`), for the release scripts ([[2026-11-17-release-versioning]], #74).
- `scripts/release-signing-setup.sh`: the release-signing wizard, run by the owner in Git Bash. It makes the upload key
  outside the repo, writes the three `loyscan.upload.*` Gradle properties to `~/.gradle/gradle.properties`, sets the
  three Actions secrets with `gh`, and points at Play Console; safe to re-run, and re-run with a new keystore path to
  rotate a leaked key ([[release]], [[leaked-signing-secret]], #74). Written with the `wizard` skill ([[agent-skills]]).
- `.github/scripts/release-bundle.sh X.Y.Z`: builds the signed AAB and refuses another version or an unsigned AAB; the
  Release workflow runs it ([[release]]).
- `.github/scripts/check-release-wizard.sh`: runs the release-signing wizard with piped answers in a sandbox ([[ci]], #74).
- `.github/scripts/check-release-build.sh`: the release build's checks, run on CI and in Git Bash ([[ci]], #74).
- `.github/scripts/ci-gate.sh`: the Tests workflow's first step; writes `run=true|false` (does this run need the gate),
  `wiki` (a wiki-only PR: only the wiki checks run), `setup` (set up the JDK and Gradle), `marker` (may a green PR run record its tree) and `tree` to `$GITHUB_OUTPUT`; on a push it asks the Actions API with
  `gh` and `jq` ([[ci]] → What runs, [[2026-11-23-ci-run-trims]], #199).
- `.github/scripts/check-ci-gate.sh`: runs `ci-gate.sh` case by case in throwaway git repos and checks what it writes;
  on CI and in Git Bash ([[ci]], #199).

Otherwise the build uses the stock Gradle wrapper tasks ([[build-and-run]]).
