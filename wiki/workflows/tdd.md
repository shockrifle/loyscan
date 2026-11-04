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
| DB callbacks (catalogue, photo sweep) | The real callback on a real database; an on-disk one, closed and reopened, when the behaviour is about reopening | Robolectric | `data/local/db/CatalogueDatabaseCallbackTest`, `CardPhotoSweepCallbackTest` |
| The database as the app builds it | `DatabaseModule.provideAppDatabase(context)` on Robolectric's file system; close it and delete `loyscan.db` after | Robolectric | `di/DatabaseModuleTest` |
| Card photos as the app builds them (the `FileProvider`) | `CardPhotosModule.provideFileCardPhotos(context)`; the provider is resolved from the manifest by Robolectric's package manager, and a capture's Uri is written through the app's `ContentResolver`. That write is skipped on Windows (`assumeFalse`): androidx's `FileProvider` matches a file to its root with `/`, so on a Windows path it finds none ("Failed to find configured root", checked 2026-11-03). CI runs it | Robolectric | `di/CardPhotosModuleTest` |
| Repository | The real repository over an in-memory database, and a real `FileCardPhotos` over a `TemporaryFolder` | Robolectric | `data/repository/RoomLoyaltyCardRepositoryTest` |
| Files the app keeps (`data/photos/`) | The real class over a `TemporaryFolder` directory; a picked picture is a `Uri.fromFile`, read through the app's `ContentResolver`, or an `InputStream` passed as `openPicture` that blocks on a latch (cancel midway) or throws partway. Pictures are real: `@GraphicsMode(NATIVE)`, so `BitmapFactory`, `Bitmap.compress`, `Canvas` and `ExifInterface` work on real pixels, a fixture is drawn and encoded in the test (`aPicture`, as JPEG, PNG or WebP; EXIF tags through `withExif`, which uses androidx's `ExifInterface` since the platform's can't write PNG or WebP on API 29), and the result is checked by decoding it. A behaviour of an older Android runs on that SDK with `@Config(sdk = [29])` (#92); CI's Robolectric cache is keyed on `gradle/libs.versions.toml` only, so a jar for a newly pinned SDK is saved only on a run whose key is new (the cache is never overwritten on a hit): until that file next changes, CI downloads it on every run ([[ci]]). This runs on Windows too, but `ImageDecoder` doesn't ("Not supported on Windows", checked 2026-11-03 on Robolectric 4.17). Decode from a stream the code closes: `BitmapFactory.decodeFile` left the file open there, so it couldn't be deleted (#88) | Robolectric | `data/photos/FileCardPhotosTest` |
| Preferences repository | The real DataStore on a `TemporaryFolder` file; a restart is a second store on the same file after the first one's scope is cancelled. Write each file at most once ([[2026-10-28-datastore-for-user-preferences]]) | default | `data/repository/DataStoreUserPreferencesRepositoryTest` |
| ViewModel (no route) | Constructed with a fake repository; Turbine on the `StateFlow`; `MainDispatcherRule` | default | `ListViewModelTest` |
| ViewModel reading a route | Same, with `SavedStateHandle(route = Detail(…))` | Robolectric | `DetailViewModelTest` |
| Drawing geometry, barcode encoding (`ui/barcode/`) | Plain JUnit. Robolectric doesn't run Compose draw code, so what a `drawWithCache` block draws is computed by a pure function (`barcodeRects`) and tested there. Encoded barcodes are read back with ZXing's reader | default | `ui/barcode/BarcodeLayoutTest`, `BarcodeEncoderTest` |
| Screen | `createComposeRule()`, a fake-backed ViewModel, recording `*Directions` lambdas, wrapped in `SharedTransitionLayout { AnimatedVisibility(true) { … } }`. Robolectric's default screen is 320×470dp; a screen taller than that gets `@Config(qualifiers = "w411dp-h891dp")` | Robolectric | `ListScreenTest`, `DetailScreenTest` |
| Screen across navigation (leaving and coming back) | As above, but the screen sits in an `AnimatedContent` whose other state is an empty `Box`, inside a `rememberSaveableStateHolder()` provider, as the `NavHost` keeps a back-stack entry's saved state; a `StateRestorationTester` adds process death | Robolectric | `ListScreenTest.returningFromACard_…` |
| Screen with a system picker | As a screen, with the picker passed in: `ScannerScreen(picker = …)`, `CardFormScreen(photoPicker = { onPicked -> PicturePicker { onPicked(uri) } })`. `hiltImageLoader()` works here: Robolectric starts the real `LoyScanApplication`, and the entry point builds only the image loader | Robolectric | `ScannerScreenTest`, `CardFormScreenTest` |
| Screen using the camera or a permission | As above, with the hardware passed in: `FakeCameraPermission` and a fake `ScannerCamera` lambda; the card form's camera app is a `FakePhotoCamera` (`photoCamera = { onResult -> camera.also { it.onResult = onResult } }`), which answers `take` at once with its `answer` | Robolectric | `ScannerScreenTest`, `CardFormScreenTest` |
| Device facts (`data/device/`) | The real implementation on Robolectric, the facts set through its shadows (`shadowOf(TelephonyManager)`) and the locale through `@Config(qualifiers = "hu-rHU")`; callers get a lambda for the `fun interface` | Robolectric | `DeviceUserCountryTest` |
| Camera / permission glue (`ui/scanner/`) | The real composable over a fake `CameraOpener` / `FrameReader`; frames are `FakeImageProxy`. A permission request is answered by a test `ActivityResultRegistry` (`LocalActivityResultRegistryOwner`), the grant and rationale by Robolectric's shadows | Robolectric (analyzer: default) | `CameraXScannerTest`, `CameraPermissionTest`, `ScanAnalyzerTest`, `ui/PhotoCameraTest` (the registry answers the permission and the camera app by the input's type; a camera app is an activity with an `IMAGE_CAPTURE` filter on the shadow package manager) |
| System pickers and reading what they return (`ui/scanner/`) | The picker's request and result go through a test `ActivityResultRegistry`, as for the permission; the reader runs with its decoder and native reader passed in as lambdas | Robolectric | `PicturePickerTest`, `ZxingCppPictureReaderTest` |
| Where directions navigate | `TestNavHostController` with `ComposeNavigator` and `DialogNavigator`, a `NavHost` of the app's routes with empty screens; call the `*Directions()` factory and check the back stack | Robolectric | `AddCardDirectionsTest` |
| App wiring / navigation | `@HiltAndroidTest` with `createAndroidComposeRule<MainActivity>()` and `InMemoryDatabaseModule`. A test that checks a card photo's file is deleted first waits for the photo's load to finish (`waitForThePhotoToLoad`: the app's `ImageLoader`, injected, has it in its memory cache): on Windows a file the image loader still has open can't be deleted, so the delete failed after the card screen had just loaded it (#10). That wait idles the main looper itself (`ShadowLooper.idleMainLooper()`), since Coil delivers its result there and a condition that reads no Compose node doesn't pump it. Every `waitUntil` passes `WAIT_TIMEOUT_MS` (10 s), text waits through `waitForText`: the 1 s default timed out on a slow CI runner (#56). After a form's Save, wait for the form to close (`waitForTheCardFormToClose`) before waiting for text it also holds in its fields: otherwise Back can land on the form while the save runs, and leave it instead of the screen the test meant to leave (CI run 40681591473; before #64 the save then also opened the card over the list) | Robolectric + `HiltTestApplication` | `AppNavigationTest` |
| App started by an intent (a share) | As above, but `createEmptyComposeRule()` and `ActivityScenario.launch(intent)`, closed after the test; `onNewIntent` through `InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent`, an activity it starts through `shadowOf(application).nextStartedActivity`, rotation through `scenario.recreate()`. What the manifest declares is checked on Robolectric's package manager (`queryIntentActivities`) | Robolectric + `HiltTestApplication` | `ShareToLoyScanTest`, `ShareIntentTest` |

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
  survive is a switch on the fake: `FakeLoyaltyCardRepository.addFailure` makes `add` throw, and `loadGate` holds back `getAllWithStore` until it completes (`addGate`, `updateGate` and `deleteGate` hold a write the same way, on both repository fakes). To clear a ViewModel, as leaving its screen does, create it with `ViewModelStore().keep { … }` (`testing/ViewModelStores.kt`) and call `clear()` on the store (`CardFormViewModelTest`, #64).
  When you change an interface's contract, change the fake in the same commit. No mocking library.
- **`FakeCardPhotos`** stands in for `CardPhotos` in ViewModel and screen tests: `stored` is the set of photo names
  that exist. `stage` names staged files `staged-1`, `staged-2`… (listed in `staged`), `crop` names the framed photo
  `photo-1`, `photo-2`… (the pairs in `cropped`) and drops the staged one; `importFailure` / `importGate` make a copy
  fail or hold, `cropFailure` makes the crop fail. `newCapture` names captures `capture-1`… with a
  `content://photos.test/…` Uri, lists them in `captures` and adds them to `stored`. Because a pick stages then crops
  asynchronously, a crop-flow test asserts on `viewModel.state.value` after driving both steps (a `getStateFlow` reads
  back even before Turbine delivers the item), rather than on a Turbine item.
  It only answers `exists`: the image loader still looks for the file in `files/card_photos/`. A screen test that needs
  the photo to load writes one there with `File.writeOnePixelPng()` (`testing/PhotoFiles.kt`) and waits for it in the
  app loader's memory cache, idling the main looper (`ListScreenTest.aCardWithAFrontPhoto_…`); a name with no file
  there is a photo that fails to load (`aFrontPhotoThatCantBeLoaded_…`).
- **`MainDispatcherRule`** for anything that uses `viewModelScope`. A timer in a ViewModel takes its duration as a
  constructor parameter (`ScannerViewModel(hintDelay)`): `advanceTimeBy` tests it in virtual time, and a screen test
  passes `Duration.ZERO`. A timeout around work on another thread fires at once under `runTest`, which jumps virtual
  time ahead whenever the test thread is idle: run the work on the test scheduler behind a `CompletableDeferred`, or
  use `runBlocking` for a test about real threads (`ZxingCppPictureReaderTest`, #79).
- **`TemporaryPreferencesModule`** replaces `PreferencesModule` in every `@HiltAndroidTest`: a new preferences file per
  test, since DataStore throws when two stores open one file.
- **`FakePictureReaderModule`** replaces `PictureReaderModule` in every `@HiltAndroidTest`: one `FakePictureReader`
  that the test injects and sets.
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
