# No automated tests

**Status:** Open · **Since:** 2025-12-03 (`d5affac`) · **Where:** `app/src/test/…/ExampleUnitTest.kt`,
`app/src/androidTest/…/ExampleInstrumentedTest.kt` (Android Studio templates only)

## What
No tests cover DAOs, the mapper, the repositories, the ViewModels, navigation or the UI. The test dependencies
(JUnit 4, AndroidX JUnit, Espresso, Compose UI test) are declared but unused.

## Why it exists
Early prototype phase (20 commits, 2025-12-03 → 2025-12-12).

## Risk / impact
Regressions in the hand-written `JOIN` + `brand_` aliasing (`LoyaltyCardDao.kt:31-35`) or in the seed SQL are
only caught by running the app.

## How to pay it down
The highest value for the least effort: an instrumented Room test using `Room.inMemoryDatabaseBuilder` that
inserts a brand and a card and asserts `getAllWithBrand()` maps every field. Then a JVM test for `toDomainModel()`.
