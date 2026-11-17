# Scripts

Notable scripts, CLIs and custom Gradle tasks: what each does, how to invoke it, inputs and outputs, and gotchas.

- [[reviewer-model]]: which model and effort a Claude Code subagent (the `/next-task` reviewer) really ran on.

- `:app:printVersion` (Gradle task, `app/build.gradle.kts`): prints `<versionName> <versionCode>` as Gradle resolved them
  (`./gradlew -q :app:printVersion`), for the release scripts ([[2026-11-17-release-versioning]], #74).
- `.github/scripts/check-release-build.sh`: the release build's checks, run on CI and in Git Bash ([[ci]], #74).

Otherwise the build uses the stock Gradle wrapper tasks ([[build-and-run]]).
