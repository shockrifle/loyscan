# No automated tests

**Status:** Resolved 2026-10-12 (#13; last piece #29) · **Issue:** [#13](https://github.com/shockrifle/loyscan/issues/13) · **Since:** 2025-12-03 (`00933cd`) · **Where:** `app/src/test/`

**Resolved:** every layer has tests (#30), coverage is gated at 90 % (#27), test-first is the documented default
(#28), and CI runs the tests and the gate on every push (#29, [[ci]]). Feature work keeps it paid down; the
coverage ratchet makes slipping back visible.

## What
The app had no automated tests apart from the Android Studio templates.

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
- Test-first is the default (#28): [[2026-10-12-tdd-and-coverage]], how-to in [[tdd]]. It is in the root
  `CLAUDE.md`, and `/review-changes` enforces it.
- CI (#29): GitHub Actions runs `testDebugUnitTest koverVerify` on every push to `master`, and on every PR since
  2026-11-09; lint too since 2026-11-11 (#126) — [[ci]].

## Why it exists
Early prototype phase (20 commits, 2025-12-03 → 2025-12-12).

## Risk / impact
(Historical) Regressions in the hand-written SQL, the seed script or the screens were only caught by running the app.

## How to pay it down
Done: the sub-issues of #13 (#26, #27, #28, #29, #30) are all closed.
