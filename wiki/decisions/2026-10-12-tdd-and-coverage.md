# Test-first by default, JVM-first, fakes over mocks

**Status:** Active · **Date:** 2026-10-12 · **Issue:** [#28](https://github.com/shockrifle/loyscan/issues/28) (part of #13)

All new logic in LoyScan, whether written by a person or a Claude session, starts with a failing test. Tests run
on the JVM. Test doubles are hand-written fakes, not mocks. Bug fixes start with a failing regression test, and
the coverage gate only goes up. How to do this day to day: [[tdd]].

## Context
Until 2026-10-09 the app had no tests ([[no-automated-tests]]). #26, #27 and #30 put a harness, a coverage gate and
a safety net in place, and the backfill found two crashes no one had seen
([[2026-10-09-missing-card-throws-from-room]], [[empty-brand-color-crash]]). Without a rule about *when* tests
get written, new code would go back to being tested afterwards, or not at all.

## Options considered
- **Test-first (red → green → refactor) as the default.** The test is written against the behaviour the issue
  asks for, before the code exists.
- **Tests after, enforced only by the coverage gate.** Cheaper per change, but tests written after the code
  tend to restate the code. The gate also counts lines, not behaviours: a happy-path test can cover a line
  without checking it.
- **Instrumented (emulator) tests for Android code.** Closer to a device, but slow and needs an emulator, so
  they wouldn't run on every change or easily in CI (#29).
- **Mocking library (MockK/Mockito) for doubles.** Quick to write, but couples tests to call sequences, and
  every refactor breaks them.

## Decision
1. **Red → green → refactor.** Write the smallest test that fails for the right reason and run it. Write the
   least code that makes it pass, then clean up with the tests green. "Right reason" means the failure message
   names the missing behaviour, not a compile error elsewhere.
2. **Bug fixes start with a failing regression test** that reproduces the bug through the public API. You see it
   fail, then fix. The test stays, named after the behaviour (`emptyBrandColor_doesNotCrash`), not the issue
   number. A `// #N` comment links it.
3. **JVM-first.** Every test is in `app/src/test/`. Room, `SavedStateHandle`, Compose UI and Hilt run on
   Robolectric. Add instrumented tests only for something Robolectric can't do, such as the camera (#4), and record
   why on the feature page. *(Update 2026-11-05: the first one is the minified build's smoke test in
   `app/src/androidTest/`, which a JVM test can't cover. It runs by hand on a device, #73,
   [[2026-11-05-r8-smoke-test-on-a-device]].)*
4. **Fakes over mocks.** Doubles are small, hand-written implementations of our own interfaces
   (`FakeLoyaltyCardRepository`), shared in `app/src/test/java/com/bdaniel/loyscan/testing/`. Room is never
   faked; it runs for real, in memory. No mocking library is a dependency.
5. **Coverage:** about 80 % line coverage overall and at least 90 % for `domain/`, `data/` and the ViewModels.
   `koverVerify` enforces the bound and it is only ever raised ([[2026-10-09-coverage-gate-kover]]).
   `./gradlew testDebugUnitTest koverVerify` passes before every commit.
6. **Acceptance criteria are testable statements** (see [[CLAUDE]] → GitHub Issues vs this wiki), so each
   criterion maps to one or more tests.

## Why
- **Test-first** keeps tests about behaviour, and keeps code testable by construction: the seams in
  [[architecture]] → Testing seams exist because a test needed them. *(Chosen for #13; these are the usual
  reasons for TDD, not something measured on this project.)*
- **Seeing the test fail** proves it can fail. The #14 reproduction failed with two different exceptions
  before the fix, which also showed that the list screen had the bug too.
- **JVM-first** keeps the whole suite in one Gradle task with no device, fast enough to run before every
  commit and in CI.
- **Fakes over mocks:** the fake implements the same contract as the real implementation, so it has to copy the
  real edge cases. The missing-card bug showed what happens when it doesn't: a fake that was kinder than Room
  hid a crash. When a fake and the real implementation disagree, fix the fake.

## Consequences
- Every change has a test diff. `/review-changes` reports new logic without tests, and bug fixes without a
  regression test, as findings ([[code-review]]).
- Commits on `master` stay green, so a red test is never committed. Red happens locally. Describe the red step
  in the commit message (what failed, and with what).
- Robolectric start-up makes the first test in a run take about 10 s. That is the price of JVM-first, and much
  cheaper than an emulator.
- Some code has no logic to test (theme values, DI wiring) and is excluded or covered only indirectly through
  the end-to-end test. That is accepted; don't write tests that only restate wiring.
