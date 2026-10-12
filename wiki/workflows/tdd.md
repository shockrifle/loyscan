# Test-driven development in LoyScan

Every change starts with a failing test, and `./gradlew testDebugUnitTest koverVerify` passes before every commit.
The rules and why: [[2026-10-12-tdd-and-coverage]]. This page is the how.

## The loop

1. **Pick the next acceptance criterion** of the issue. Criteria are phrased as testable statements; if one isn't,
   rephrase it first (in the issue).
2. **Red.** Write one test for it in the right place (table below) and run only that class:
   `./gradlew testDebugUnitTest --tests '*DetailViewModelTest'`. It must fail, and the failure must name the missing
   behaviour. A compile error means the API doesn't exist yet. Add the bare signature (returning a stub) so the
   test compiles and fails on its assertion.
3. **Green.** Write the least code that passes. Run the class again.
4. **Refactor** with the tests green: names, duplication, moving code to the right layer.
5. Repeat until the criteria are covered. Then run the whole suite and the gate:
   `./gradlew testDebugUnitTest koverVerify`. If coverage rose, raise `minBound` in `app/build.gradle.kts` to the
   new whole percent in the same commit ([[2026-10-09-coverage-gate-kover]]).
6. **Commit** with the wiki update. Say what the red step was in the message when it isn't obvious.

**Bug fix:** step 2 is a regression test that reproduces the bug through the public API. Run it and keep the
failure message for the commit, then fix it. Example: `12357a3` (#14).

Set `JAVA_HOME` first from a terminal (root `CLAUDE.md`).

## Which test for which layer

Paths are under `app/src/test/java/com/bdaniel/loyscan/`, in the same package as the code under test.

| Layer | Test type | Runner | Example |
|---|---|---|---|
| Domain logic (`domain/`) | Plain JUnit, no Android | default | `domain/cardnumber/CardNumberEngineTest` |
| Mapper (`data/mapper/`) | Plain JUnit on fixture entities | default | `data/mapper/LoyaltyCardMapperTest` |
| DAO / SQL (`data/local/db/`) | Real Room, `Room.inMemoryDatabaseBuilder(…).allowMainThreadQueries()` | Robolectric | `data/local/db/dao/LoyaltyCardDaoTest` |
| Seed / DB callbacks | The real callback with `executor = Runnable::run` | Robolectric | `data/local/db/SeedDatabaseCallbackTest` |
| Repository | The real repository over an in-memory database | Robolectric | `data/repository/RoomLoyaltyCardRepositoryTest` |
| ViewModel (no route) | Constructed with a fake repository; Turbine on the `StateFlow`; `MainDispatcherRule` | default | `ListViewModelTest` |
| ViewModel reading a route | Same, with `SavedStateHandle(route = Detail(…))` | Robolectric | `DetailViewModelTest` |
| Screen | `createComposeRule()`, a fake-backed ViewModel, recording `*Directions` lambdas, wrapped in `SharedTransitionLayout { AnimatedVisibility(true) { … } }` | Robolectric | `ListScreenTest`, `DetailScreenTest` |
| App wiring / navigation | `@HiltAndroidTest` with `createAndroidComposeRule<MainActivity>()` and `InMemoryDatabaseModule` | Robolectric + `HiltTestApplication` | `AppNavigationTest` |

Robolectric means `@RunWith(AndroidJUnit4::class)`. Keep end-to-end tests few, one per user flow; behaviour
details belong in the per-layer tests.

## Naming and shape

- Class: `<ClassUnderTest>Test`. Method: `subject_condition_expectedResult` or `behaviour_inPlainWords`, e.g.
  `getByIdWithStore_unknownId_emitsNull`, `clickingACard_navigatesToItWithItsStoreColor`.
- One behaviour per test, arranged as arrange / act / assert with blank lines between them. Assertions use Truth
  (`assertThat`), and `Flow`s are tested with Turbine (`flow.test { awaitItem() … }`).
- A regression test is named after the behaviour; a `// #N` comment links the issue.

## Fixtures and doubles

Shared helpers are in `testing/`:
- **Fixture builders**, `aLoyaltyCard()`, `aStore()`, `aStoreEntity()`, `aLoyaltyCardEntity()`: every field has a
  default, so a test names only what it is about. Add a builder when you add a model. Use example data only,
  never real stores.
- **Fakes**, e.g. `FakeLoyaltyCardRepository` (a `MutableStateFlow` you set): a fake implements the real
  interface *and its edge cases*. For example, `getByIdWithStore` emits `null` for a missing id, as Room does.
  When you change an interface's contract, change the fake in the same commit. No mocking library.
- **`MainDispatcherRule`** for anything that uses `viewModelScope`.
- **`InMemoryDatabaseModule`** replaces `DatabaseModule` in every `@HiltAndroidTest`: an empty database that the
  test injects and fills.

Make code testable through constructor parameters, not test hooks. Pass a ViewModel and `*Directions` into
screens, and an `Executor` into callbacks. See [[architecture]] → Testing seams.

## Running and reading coverage

- `./gradlew testDebugUnitTest`: all tests. Report: `app/build/reports/tests/testDebugUnitTest/index.html`.
- `./gradlew koverVerify`: the coverage gate (it runs the tests too). `./gradlew check` runs it as well.
- `./gradlew koverHtmlReportDebug`: line-by-line report at `app/build/reports/kover/htmlDebug/index.html`. Red
  lines are untested. Look at them before deciding a test is "done".
- More on the commands and the test libraries: [[build-and-run]] → Tests. CI runs the same gate after every push:
  [[ci]].
