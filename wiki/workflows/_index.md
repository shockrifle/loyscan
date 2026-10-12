# Workflows

How things get done in day-to-day development: build, run, change the schema, manage dependencies. Each page is
a procedure with real commands, plus the constraints that have bitten before.

- [[tdd]] — test-first loop, which test per layer, fixtures and fakes, reading coverage
- [[build-and-run]] — Gradle commands, dependency/version-coupling rules, logs
- [[database-schema-changes]] — bump the Room version, export the schema, update the seed SQL
- [[ci]] — GitHub Actions: tests + coverage gate on every push to `master`, where the reports are
- [[code-review]] — local review before push: `/review-changes`, then `/review-fix`
