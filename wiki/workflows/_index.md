# Workflows

How things get done in day-to-day development: build, run, change the schema, manage dependencies. Each page is
a procedure with real commands, plus the constraints that have bitten before.

- [[tdd]] — test-first loop, which test per layer, fixtures and fakes, reading coverage
- [[build-and-run]] — Gradle commands, dependency/version-coupling rules, logs
- [[database-schema-changes]] — bump the Room version, export the schema, update the raw-row mappers
- [[add-a-string]] — a UI string in English and Hungarian (`values/`, `values-hu/`), plurals, messages from a ViewModel
- [[add-a-catalogue-store]] — sourcing rules, entry format and test vector for a catalogue store
- [[test-backup-on-a-device]] — simulate a device-to-device transfer (or a cloud backup) of the smoke build on one phone with `bmgr`, and its traps
- [[ci]] — GitHub Actions: tests + coverage gate on every PR and on `master` (a wiki-only PR skips Gradle), where the reports are, how Dependabot's PRs are handled (`/dependency-pr`)
- [[code-review]] — changes land through PRs: `/review-changes`, then `/review-fix`; `/next-task` runs issue → plan critic → PR → independent review → merge end to end
- [[release]] — cut a release: bump `loyscan.versionName`, tag `vX.Y.Z` on `master`, the Release workflow signs the AAB onto a draft GitHub release, upload to Play by hand; where release files are kept (#74)
- [[public-safe-sweep]] — check history, issues, PRs and Actions logs for secrets and personal details (`PublicSafeRepoTest` covers the tracked files)
- [[agent-skills]] — the project skills in `.claude/skills/` (`diagnosing-bugs`, `writing-for-agents`, `grilling`, `retro`, `to-tickets`, `wizard`): what each is for, what was adapted, how to change one
