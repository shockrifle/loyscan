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
| DB callbacks (catalogue) | The real callback on a real database; an on-disk one, closed and reopened, when the behaviour is about reopening | Robolectric | `data/local/db/CatalogueDatabaseCallbackTest` |
| The database as the app builds it | `DatabaseModule.provideAppDatabase(context)` on Robolectric's file system; close it and delete `loyscan.db` after | Robolectric | `di/DatabaseModuleTest` |
| Repository | The real repository over an in-memory database | Robolectric | `data/repository/RoomLoyaltyCardRepositoryTest` |
| Preferences repository | The real DataStore on a `TemporaryFolder` file; a restart is a second store on the same file after the first one's scope is cancelled. Write each file at most once ([[2026-10-28-datastore-for-user-preferences]]) | default | `data/repository/DataStoreUserPreferencesRepositoryTest` |
| ViewModel (no route) | Constructed with a fake repository; Turbine on the `StateFlow`; `MainDispatcherRule` | default | `ListViewModelTest` |
| ViewModel reading a route | Same, with `SavedStateHandle(route = Detail(…))` | Robolectric | `DetailViewModelTest` |
| Drawing geometry, barcode encoding (`ui/barcode/`) | Plain JUnit. Robolectric doesn't run Compose draw code, so what a `drawWithCache` block draws is computed by a pure function (`barcodeRects`) and tested there. Encoded barcodes are read back with ZXing's reader | default | `ui/barcode/BarcodeLayoutTest`, `BarcodeEncoderTest` |
| Screen | `createComposeRule()`, a fake-backed ViewModel, recording `*Directions` lambdas, wrapped in `SharedTransitionLayout { AnimatedVisibility(true) { … } }`. Robolectric's default screen is 320×470dp; a screen taller than that gets `@Config(qualifiers = "w411dp-h891dp")` | Robolectric | `ListScreenTest`, `DetailScreenTest` |
| Screen across navigation (leaving and coming back) | As above, but the screen sits in an `AnimatedContent` whose other state is an empty `Box`, inside a `rememberSaveableStateHolder()` provider, as the `NavHost` keeps a back-stack entry's saved state; a `StateRestorationTester` adds process death | Robolectric | `ListScreenTest.returningFromACard_…` |
| Screen using the camera or a permission | As above, with the hardware passed in: `FakeCameraPermission` and a fake `ScannerCamera` lambda | Robolectric | `ScannerScreenTest` |
| Camera / permission glue (`ui/scanner/`) | The real composable over a fake `CameraOpener` / `FrameReader`; frames are `FakeImageProxy`. A permission request is answered by a test `ActivityResultRegistry` (`LocalActivityResultRegistryOwner`), the grant and rationale by Robolectric's shadows | Robolectric (analyzer: default) | `CameraXScannerTest`, `CameraPermissionTest`, `ScanAnalyzerTest` |
| Where directions navigate | `TestNavHostController` with `ComposeNavigator` and `DialogNavigator`, a `NavHost` of the app's routes with empty screens; call the `*Directions()` factory and check the back stack | Robolectric | `AddCardDirectionsTest` |
| App wiring / navigation | `@HiltAndroidTest` with `createAndroidComposeRule<MainActivity>()` and `InMemoryDatabaseModule`. Every `waitUntil` passes `WAIT_TIMEOUT_MS` (10 s), text waits through `waitForText`: the 1 s default timed out on a slow CI runner (#56) | Robolectric + `HiltTestApplication` | `AppNavigationTest` |

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
- **Fakes**, e.g. `FakeLoyaltyCardRepository` and `FakeStoreRepository` (each a `MutableStateFlow` you set): a fake implements the real
  interface *and its edge cases*. For example, `getByIdWithStore` emits `null` for a missing id, as Room does. A failure the code must
  survive is a switch on the fake: `FakeLoyaltyCardRepository.addFailure` makes `add` throw, and `loadGate` holds back `getAllWithStore` until it completes.
  When you change an interface's contract, change the fake in the same commit. No mocking library.
- **`MainDispatcherRule`** for anything that uses `viewModelScope`. A timer in a ViewModel takes its duration as a
  constructor parameter (`ScannerViewModel(hintDelay)`): `advanceTimeBy` tests it in virtual time, and a screen test
  passes `Duration.ZERO`.
- **`TemporaryPreferencesModule`** replaces `PreferencesModule` in every `@HiltAndroidTest`: a new preferences file per
  test, since DataStore throws when two stores open one file.
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
