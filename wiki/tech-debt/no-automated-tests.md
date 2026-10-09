# No automated tests

**Status:** Open · **Issue:** [#13](https://github.com/shockrifle/loyscan/issues/13) · **Since:** 2025-12-03 (`00933cd`) · **Where:** `app/src/test/…/ExampleUnitTest.kt`,
`app/src/androidTest/…/ExampleInstrumentedTest.kt` (Android Studio templates only)

## What
No tests cover DAOs, the mapper, the repositories, the ViewModels, navigation or the UI. The test dependencies
(JUnit 4, AndroidX JUnit, Espresso, Compose UI test) are declared but unused.

Partial progress (2026-10-09): the card-number rule engine has JVM unit tests
(`app/src/test/java/com/bdaniel/loyscan/domain/cardnumber/`, [[2026-10-09-card-number-rule-engine]]). Nothing in
the data or UI layers is tested yet.

## Why it exists
Early prototype phase (20 commits, 2025-12-03 → 2025-12-12).

## Risk / impact
Regressions in the hand-written `JOIN` + `brand_` aliasing (`LoyaltyCardDao.kt:31-35`) or in the seed SQL are
only caught by running the app.

## How to pay it down
The highest value for the least effort: an instrumented Room test using `Room.inMemoryDatabaseBuilder` that
inserts a brand and a card and asserts `getAllWithBrand()` maps every field. Then a JVM test for `toDomainModel()`.
