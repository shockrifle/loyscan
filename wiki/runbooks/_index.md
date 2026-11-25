# Runbooks

What to do when something breaks. Format: **Use when** (symptoms, exact error text) → numbered steps with the
actual commands → what to check if the steps don't help. Add one when an incident is handled or a failure
recurs.

- [[cli-build-jvm-target-mismatch]] — terminal `./gradlew` fails with Java 21 vs Kotlin 19; use Studio's JBR (no
  longer applies since #115, AGP 9)
- [[gradlew-hangs-in-git-bash]] — `./gradlew … 2>&1` piped into a native Windows program hangs in `gradlew`'s `xargs`; filter with Git Bash's own `grep` or use `gradlew.bat`
- [[leaked-signing-secret]] — the upload key or its password was committed, posted or logged: rotate it (new key, Play upload key reset, new secrets); deleting it is not enough (#74)
- [[reset-local-database]] — Room integrity crash after a schema change, stale or empty seed data
