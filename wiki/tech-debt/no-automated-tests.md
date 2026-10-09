# No automated tests

**Status:** Open · **Issue:** [#13](https://github.com/shockrifle/loyscan/issues/13) · **Since:** 2025-12-03 (`00933cd`) · **Where:** `app/src/test/`

## What
Every layer now has a safety net of tests (#30), and coverage is gated at 90 %. What is still missing is the
habit and the machinery: the test-first workflow (#28) and CI (#29).

Progress (2026-10-09):
- The card-number rule engine has JVM unit tests (`app/src/test/java/com/bdaniel/loyscan/domain/cardnumber/`,
  [[2026-10-09-card-number-rule-engine]]).
- Test infrastructure is in place (#26): every kind of test runs on the JVM, and there is one example test per
  kind (DAO, ViewModel, Compose screen). See [[build-and-run]] → Tests. The Android Studio template tests were
  removed.
- Coverage is measured and gated (#27): `koverVerify` fails below the minimum, which started at the 63 % baseline
  and only goes up — [[2026-10-09-coverage-gate-kover]].
- Safety-net tests backfilled (#30): seed script, DAO join, mapper, repository, both ViewModels, both screens and
  an end-to-end Hilt navigation test. Line coverage went from 63 % to 90 %. They found
  [[2026-10-09-missing-card-throws-from-room]].

## Why it exists
Early prototype phase (20 commits, 2025-12-03 → 2025-12-12).

## Risk / impact
Without the test-first habit and CI, new code can still go in without tests. The gate catches some of that, but only
for people who run `./gradlew check`.

## How to pay it down
The remaining sub-issues of #13 are the test-first workflow (#28) and CI (#29), which keep it paid down.
