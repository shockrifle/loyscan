# No automated tests

**Status:** Open · **Issue:** [#13](https://github.com/shockrifle/loyscan/issues/13) · **Since:** 2025-12-03 (`00933cd`) · **Where:** `app/src/test/`

## What
The data and UI layers have almost no tests, and nothing measures coverage.

Progress (2026-10-09):
- The card-number rule engine has JVM unit tests (`app/src/test/java/com/bdaniel/loyscan/domain/cardnumber/`,
  [[2026-10-09-card-number-rule-engine]]).
- Test infrastructure is in place (#26): every kind of test runs on the JVM, and there is one example test per
  kind (DAO, ViewModel, Compose screen). See [[build-and-run]] → Tests. The Android Studio template tests were
  removed.
- Coverage is measured and gated (#27): `koverVerify` fails below the minimum, which started at the 63 % baseline
  and only goes up — [[2026-10-09-coverage-gate-kover]].

## Why it exists
Early prototype phase (20 commits, 2025-12-03 → 2025-12-12).

## Risk / impact
Regressions in the hand-written `JOIN` + `brand_` aliasing (`LoyaltyCardDao.kt:31-35`) or in the seed SQL are
only caught by running the app.

## How to pay it down
Tracked as sub-issues of #13: tests for the seed SQL, mapper and screens (#30), raising the coverage minimum as
they land, and the test-first workflow (#28) and CI (#29) that keep it paid down.
