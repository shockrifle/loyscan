# Architecture

LoyScan is one Gradle module (`:app`) with a simple **unidirectional, Flow-based layering**:
Room (SQLite) → DAO → Repository (maps to domain) → `@HiltViewModel` (`stateIn`) → Compose screen
(`collectAsState`). Navigation is type-safe Navigation Compose with serializable route objects and
per-screen **Directions** objects. Hilt is the only DI mechanism; KSP runs both the Hilt and Room
annotation processors. Everything runs inside one `SharedTransitionLayout` so screens can share elements.

All paths below are under `app/src/main/java/com/bdaniel/loyscan/` unless written in full.

## Layers

| Layer | Package / files | Responsibility | Rules |
|---|---|---|---|
| **Persistence** | `data/local/db/` — `AppDatabase`, `entity/*Entity`, `dao/*Dao` | Room tables, queries returning `Flow` | Entities never leave `data/`. A card's store is loaded with `@Relation` (see `LoyaltyCardWithStore`). `Converters` maps lists, rules and `Instant`s to columns. |
| **Mapping** | `data/mapper/LoyaltyCardMapper.kt` | `LoyaltyCardWithStore.toDomainModel()`, `StoreEntity.toDomainModel()`; `LoyaltyCard.toEntity()` | Extension functions: DB → domain for reads, domain → entity for writes. |
| **Repository** | `data/repository/` — `LoyaltyCardRepository`, `StoreRepository` and `UserPreferencesRepository` (interfaces) + `RoomLoyaltyCardRepository` / `RoomStoreRepository` / `DataStoreUserPreferencesRepository` (`@Singleton` impls; preferences: [[2026-10-28-datastore-for-user-preferences]]) | Exposes `Flow<domain>` | ViewModels depend on the **interface**, so tests pass a fake (see Testing seams). Reads return domain models. |
| **Domain** | `domain/` — `LoyaltyCard`, `Store`, `BarcodeFormat`; `domain/cardnumber/` — card-number rule engine ([[2026-10-09-card-number-rule-engine]]) | Plain data classes with an `EMPTY` sentinel; pure-Kotlin logic. Barcode content, display number and barcode format are derived on read (`LoyaltyCard`), never stored ([[2026-10-12-data-model-v2]]) | No Android/Room imports — unit-tested on the JVM. |
| **Presentation state** | root package — one `*ViewModel` per screen, e.g. `ListViewModel`, `DetailViewModel` (`ScannerViewModel` holds UI state only, no repository) | `@HiltViewModel`; turn repository Flows into `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` | Route args read with `savedStateHandle.toRoute<Route>()`. |
| **UI** | root package — one `*Screen` per destination, e.g. `ListScreen`, `DetailScreen`; `ui/` — theme and the shared design values ([[design-system]]: `Spacing`, shapes, `CardShape`), icons, `hiltImageLoader()`; `ui/barcode/` — ZXing encoding and barcode drawing ([[zxing-core]]); `ui/scanner/` — camera, permission and zxing-cpp decoding for `ScannerScreen` ([[zxing-cpp]]) | Composables, extensions on `SharedTransitionScope` | Screens receive a `*Directions` object, never a `NavController`. Texts come from string resources (`stringResource`, `pluralStringResource`), English by default and Hungarian in `values-hu` ([[add-a-string]], #72), on every screen; `StringResourcesTest` checks each default string has a Hungarian one. |
| **Navigation** | `Navigation.kt`, `MainActivity.kt` (`AppNavigation`) | `@Serializable` routes + `NavController.*Directions(entry)` factories | See [[2025-12-09-type-safe-navigation-directions]]. |
| **DI** | `di/*Module.kt` (database, preferences, repositories, image loader, card photos, clock, picture reader, widget, user country); the widget's logos and photos in `widget/glance/WidgetPicturesModule.kt` | `@Module @InstallIn(SingletonComponent)` providers; `@Binds` repository interface → Room / DataStore impl | Implementations use `@Inject constructor`. |

Note: `LoyaltyCardRepository` also counts a store's cards (`countForStore`, a `Flow<Int>`, #53). Its writes are `recordUse(id)` (opening a card counts a use, #4), `delete(id)` and `update(card)` (#8), `deleteAll(ids)` (the list's
multi-select, #7: one `DELETE … WHERE id IN (:ids)`), and
`add(card)` (the add-card flow, #6), which maps the domain card to its row with `LoyaltyCard.toEntity()` (`data/mapper/LoyaltyCardMapper.kt`).
`update` writes only the columns an edit changes, through Room's partial `@Update(entity = …)` with `LoyaltyCardEdit`
(`LoyaltyCard.toEdit()`), so usage and creation time are never overwritten from a stale copy ([[edit-card]]).
`update`, `delete`/`deleteAll` and `StoreRepository.delete` also delete the photo files the write drops, after it: the
DAO's `@Transaction` methods (`LoyaltyCardDao.deleteReturningPhotos` / `updateReturningDroppedPhotos`,
`StoreDao.deleteCustomReturningCardPhotos`) return the names ([[card-photos]],
[[2026-11-03-card-photos-as-private-files]]).
`StoreRepository` reads every store (`getAll`), adds one (`add(store)`, a custom store, #6; mapped with
`Store.toEntity()`), sets any store's logo (`setLogo(storeId, logo)`, #151: `StoreDao.setLogoReturningDropped`, which
names the file to delete, [[2026-11-13-user-supplied-store-logos]]) and edits or deletes a custom store (`update(store)`, `delete(id)`, #53: `StoreDao.updateCustom` / `deleteCustom`,
`… AND isCustom = 1`, so a catalogue row is never touched; the cards go by `ON DELETE CASCADE`, [[edit-store]]), an
interface with `RoomStoreRepository` behind it since #6; its
unused entity-taking `insert` was dropped then, and `getById` once the card form needed every store (2026-10-22). `StoreDao.getById` stays: no production code
reads it, but it is this page's single-row query example and tests use it to read a store back. When a feature needs more writes, add them to the interface in domain
terms, with domain→entity mappers. Time comes from an injected `java.time.Clock`
(`di/ClockModule.kt`), so tests pass `Clock.fixed(…)`.

### Composition / DI

- `LoyScanApplication` is `@HiltAndroidApp` (`app/src/main/java/com/bdaniel/loyscan/LoyScanApplication.kt`); `MainActivity` is `@AndroidEntryPoint`.
- `DatabaseModule` provides the singleton `AppDatabase` (file `loyscan.db`, seeded on creation — see
  [[store-catalogue]]) and unscoped DAO providers (`di/DatabaseModule.kt`). Two `RoomDatabase.Callback`s run
  on every open through the raw database: `CatalogueDatabaseCallback` writes the catalogue rows, and
  `CardPhotoSweepCallback` deletes old photo files no card names ([[card-photos]]). There are no debug-only demo
  cards any more ([[seed-data]], #55); `DatabaseModuleTest` checks a new database holds only the catalogue. It adds
  `AppDatabase.MIGRATIONS` (Room adds the `@Database` `autoMigrations`, v2 → v3 since #184, v3 → v4 since #185) and no destructive fallback; schemas from v2 on are frozen
  ([[2026-11-05-room-schema-frozen-from-v2]], [[database-schema-changes]]).
- `PreferencesModule` provides the singleton preferences `DataStore` (file `datastore/user_preferences.preferences_pb`,
  pinned by `PreferencesModuleTest`) behind `UserPreferencesRepository` ([[2026-10-28-datastore-for-user-preferences]]).
- `CardPhotosModule` provides `FileCardPhotos` over `files/card_photos/`, bound as `CardPhotos` ([[card-photos]]).
- `ImageLoaderModule` provides a singleton Coil `ImageLoader`. Composables can't `@Inject`, so it is reached
  through a Hilt `@EntryPoint` wrapped in the `hiltImageLoader()` composable (`app/src/main/java/com/bdaniel/loyscan/ui/ImageLoader.kt`).
  See [[2025-12-12-singleton-image-loader]] and [[coil-image-loading]].
- ViewModels are obtained with `hiltViewModel()` as a default parameter of each screen.

### Testing seams

Tests run on the JVM (`./gradlew testDebugUnitTest`, see [[build-and-run]]); shared helpers live in
`app/src/test/java/com/bdaniel/loyscan/testing/`. The one exception is the on-device smoke test of the minified build
(`app/src/androidTest/`, `MinifiedAppSmokeTest`): it drives the app from outside with UiAutomator, by visible text and
content descriptions, so it needs no seam in the app ([[2026-11-05-r8-smoke-test-on-a-device]]). The release build's
own configuration (version scheme and signing, #74) is checked by a shell script on CI, through Gradle's resolved values and its
outputs ([[tdd]] → the layer table).

- **ViewModels** are constructed directly with `FakeLoyaltyCardRepository` / `FakeStoreRepository` /
  `FakeUserPreferencesRepository` (each a
  `MutableStateFlow` of domain models)
  and `MainDispatcherRule` in place of `Dispatchers.Main`. A route-reading ViewModel gets
  `SavedStateHandle(route = Detail(…))` from navigation-testing.
- **Screens** take the ViewModel and a `*Directions` object as parameters, so a test passes a fake-backed
  ViewModel and lambdas that record navigation (`ListScreenTest`). What Robolectric can't run is a parameter too:
  `ScannerScreen` takes a `CameraPermission` and a `ScannerCamera` composable lambda, which `ScannerScreenTest`
  replaces with `FakeCameraPermission` and a recording fake ([[barcode-scanner]] → Tests). A system picker is a
  `PicturePicker` parameter, and reading what it returns a `PictureReader` in the ViewModel (`FakePictureReader`). The
  card form makes one picker per photo side, so it takes a `photoPicker` factory; copying a picked picture in is
  `CardPhotos` (`FakeCardPhotos`, [[card-photos]]). The camera app that takes a card photo is a `photoCamera` factory
  returning a `PhotoCamera` (`FakePhotoCamera`); the real `rememberPhotoCamera` is tested over a test
  `ActivityResultRegistry` (`PhotoCameraTest`).
- **Another app opened for the user** (the browser for "Find a logo online", #151) is a `fun interface LogoSearch`
  parameter, so a screen test passes a recording lambda; the real `rememberLogoSearch` (`ui/LogoSearch.kt`) is tested on
  Robolectric through the started intent, and with `checkActivities(true)` for no browser (`LogoSearchTest`).
- **Layout at a large font scale** is checked through the screen's semantics: `testing/Layout.kt`
  (`WithFontScale`, `assertTextFitsAndDoesNotOverlap`) reads every text node's bounds and text layout, under
  `@GraphicsMode(NATIVE)` so text is measured for real ([[tdd]], [[design-system]], #108).
- **Device facts behind a seam.** The user's country is a `fun interface UserCountry` (`data/device/UserCountry.kt`), so
  a ViewModel test passes a lambda (`StorePickerViewModel(repository) { "HU" }`) and only `DeviceUserCountry` reads
  the phone, tested on Robolectric's telephony shadow (`DeviceUserCountryTest`).
- **Hardware behind a seam.** The CameraX glue takes a `CameraOpener` and a `FrameReader`, so the camera lifecycle
  is tested with fakes (`CameraXScannerTest`) and only the few lines that call the real camera or native code are
  untested. A permission request is answered by an `ActivityResultRegistry` provided through
  `LocalActivityResultRegistryOwner` (`CameraPermissionTest`).
- **DAOs and the repository** are tested against `Room.inMemoryDatabaseBuilder` (`LoyaltyCardDaoTest`,
  `RoomLoyaltyCardRepositoryTest`, `RoomStoreRepositoryTest`), with a real `FileCardPhotos` over a `TemporaryFolder`
  so a test can check which photo files are left. Database callbacks are tested on a real database too: an on-disk one that is
  closed and reopened for the catalogue loader (`CatalogueDatabaseCallbackTest`). A database an older release left
  behind is built from its exported schema (`testing/ExportedSchemas.kt`) and opened through the app's builder
  (`DatabaseMigrationTest`); `FrozenSchemaTest` pins each released schema's hash.
- **The Glance widget** ([[card-widget]], #11) keeps its logic out of the composable: which tiles, in what order and
  their colour is a pure function (`toWidgetTiles`, `WidgetTile`) tested in plain/Robolectric JUnit, and the composable
  is asserted with Glance's JVM unit test (`runGlanceAppWidgetUnitTest`, `glance-appwidget-testing`) without inflating
  RemoteViews. Where a tile lands and how big it is, which depends on the layout the launcher picks and the widget's real
  width, is measured on the real RemoteViews inflated in a host view (`widget/WidgetRender.kt`, #216). The
  `GlanceAppWidget`/receiver glue runs only on a real home screen and is excluded from coverage.
- **The wired-up app** (`MainActivity`, Hilt, navigation) runs in `@HiltAndroidTest`s on Robolectric.
  `testing/TemporaryPreferencesModule` swaps `PreferencesModule` for a new preferences file per test, and
  `testing/InMemoryDatabaseModule` (`@TestInstallIn`) swaps `DatabaseModule` for an empty in-memory database
  that the test injects and fills (`AppNavigationTest`). `testing/FakePictureReaderModule` swaps the zxing-cpp
  `PictureReader` for one injected `FakePictureReader`. An activity started by an intent (a share, #77) is launched
  with `ActivityScenario.launch(intent)` under `createEmptyComposeRule()` (`ShareToLoyScanTest`).
- **Language.** Screen tests run in English, Robolectric's default locale, and assert English literals. A screen's
  Hungarian is checked by one test at `@Config(qualifiers = "+hu")` that collects every text it shows (`ShownTexts`) and
  fails on any that isn't the fixture's data or a `values-hu` value; `StringResourcesTest` checks `values-hu` covers
  every default string ([[2026-11-09-ui-language]], [[add-a-string]]).
- **Waits.** Every Compose `waitUntil*` passes the shared `WAIT_TIMEOUT_MS` (`testing/Waits.kt`), never the 1 s default
  that timed out on CI; `WaitTimeoutsTest` reads the test sources and fails on a wait that doesn't ([[tdd]], #59).
- **Play's listing texts** (`play/listing/`, #75) are files, so `StoreListingTest` reads them as the owner pastes them
  into Play Console and checks them against the app's own strings and the shipped catalogue ([[google-play]]). The
  privacy policy (`play/privacy-policy/`) is read the same way by `PrivacyPolicyTest`, each statement tied to its tests.
- Fixture builders (`aLoyaltyCard()`, `aStoreEntity()`, …) give every field a default; `cardId(n)` makes distinct UUID-shaped card ids.

### Build tooling

Version catalog `gradle/libs.versions.toml`; KSP (not kapt) for Hilt + Room ([[2025-12-08-ksp-instead-of-kapt]]);
Room schemas exported to `app/schemas/` (`app/build.gradle.kts`, `ksp { room.schemaLocation }`); Java/JVM target 21; minSdk 29,
targetSdk 36, compileSdk 37. Kover gates line coverage ([[2026-10-09-coverage-gate-kover]]). See [[android-app]] and
[[build-and-run]].

## Worked example: tap a card → see its details

This traces one feature through every layer. **Use it as the template for new features.** Each layer starts with
its test: write it, watch it fail, then write the code ([[tdd]], [[2026-10-12-tdd-and-coverage]]). The tests named
here exist; read them next to the code.

1. **Data at rest.** Rows live in `stores` and `loyalty_cards` (FK `storeId` → `stores.id`, `ON DELETE CASCADE`,
   indexed; string ids): `LoyaltyCardEntity` in
   `app/src/main/java/com/bdaniel/loyscan/data/local/db/entity/LoyaltyCardEntity.kt`. Lists, rules and timestamps go
   through `data/local/db/Converters.kt` ([[2026-10-12-data-model-v2]]). Catalogue stores are written from
   `app/src/main/assets/catalogue/stores.json` on every open ([[store-catalogue]]); cards come only from the
   add-card flow ([[add-card]]).
   *Test first:* `CatalogueDatabaseCallbackTest`: the rows land and read back as expected.
2. **DAO query.** *Test first:* `LoyaltyCardDaoTest` inserts a store and a card with every field set to a
   non-default value, then expects the same row back with its store (`getByIdWithStore_storesEveryCardField`);
   `StoreDaoTest.getById_storesEveryField` does the same for a store. A missing id emits `null`
   (`…_unknownId_emitsNull`).
   Then the code: `LoyaltyCardDao.getAllWithStore()` / `getByIdWithStore(id)` select cards inside `@Transaction`,
   and Room fills `@Relation val store: StoreEntity` with a second query
   (`LoyaltyCardWithStore`, `getAllWithStore` and `getByIdWithStore` in
   `app/src/main/java/com/bdaniel/loyscan/data/local/db/dao/LoyaltyCardDao.kt`). They return `Flow`, so the UI updates when either table changes.
   The single-card query returns `Flow<…?>`: with a non-null type Room throws for a missing row
   ([[2026-10-09-missing-card-throws-from-room]]).
3. **Mapping.** *Test first:* `LoyaltyCardMapperTest.toDomainModel_copiesEveryField`.
   Then `LoyaltyCardWithStore.toDomainModel()` builds `LoyaltyCard(…, store = Store(…))`
   (`app/src/main/java/com/bdaniel/loyscan/data/mapper/LoyaltyCardMapper.kt`). The derived fields (`barcodeContent`, `displayNumber`,
   `barcodeFormat`) are computed by `LoyaltyCard` itself; test: `domain/LoyaltyCardTest`.
4. **Repository.** *Test first:* `RoomLoyaltyCardRepositoryTest`: a real in-memory database in, domain cards out.
   Then `RoomLoyaltyCardRepository.getAllWithStore()` = `dao.getAllWithStore().map { it.toDomainModel() }`
   (`app/src/main/java/com/bdaniel/loyscan/data/repository/RoomLoyaltyCardRepository.kt`), behind the `LoyaltyCardRepository` interface. Change
   `FakeLoyaltyCardRepository` in the same step, so it keeps the interface's contract.
5. **List ViewModel.** *Test first:* `ListViewModelTest`: built with the fake repositories and a `SavedStateHandle`,
   Turbine on `cards`. It checks the initial empty list, then each update, the stored sort and the search.
   Then `ListViewModel.cards` = the repository flow combined with the sort preference and the query, sorted then
   searched, `.stateIn(viewModelScope, WhileSubscribed(5_000), null)` (`null`: not loaded yet; [[card-list]]).
6. **List UI.** *Test first:* `ListScreenTest`: a fake-backed ViewModel and a recording `ListDirections`. It
   checks one tile per card, and that a click calls `toCard(id, color)`.
   Then `ListScreen` collects `cards` and renders a 2-column `LazyVerticalGrid` of `CardItem`s, keyed by card id. Each `CardItem`'s
   colored box is a shared element keyed `cardTransitionKey(card.id)` (`"card/<id>"`). A click calls
   `directions.toCard(card.id, card.store.color)` ([[card-list]]).
7. **Navigation.** *Test first:* `AppNavigationTest`, the one end-to-end test for this flow: real Hilt and
   navigation over `InMemoryDatabaseModule`, list → detail → back.
   Then `NavController.listDirections(entry).toCard` → `navigate(Detail(id, color))`
   (`listDirections` and `Detail` in `app/src/main/java/com/bdaniel/loyscan/Navigation.kt`).
   `AppNavigation` maps `composable<Detail>` to `DetailScreen`, pulling `cardId` and `color` out of the route and
   passing them as plain parameters (`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt`).
8. **Detail ViewModel.** *Test first:* `DetailViewModelTest` with `SavedStateHandle(route = Detail(…))`. It
   checks `EMPTY` first, then the routed card. A missing card stays `EMPTY`, and a deleted card keeps the last
   value.
   Then `DetailViewModel` reads `savedStateHandle.toRoute<Detail>().cardId`, then
   `repository.getByIdWithStore(cardId).filterNotNull().stateIn(…, initialValue = LoyaltyCard.EMPTY)`, with
   a photo whose file is missing left off on the way (`CardPhotos.withoutMissing`, [[card-photos]]) (`DetailViewModel.card`).
9. **Detail UI.** *Test first:* `DetailScreenTest`: it shows the label and the display number (formatted by the
   store's rule), the back arrow calls `toBack`, an empty or malformed route color doesn't crash, and a tile
   grows into the screen before the card has loaded.
   Then `DetailScreen` paints its band ([[2026-11-24-detail-header-band]]) with the **route** color until the card has loaded (frame 1
   on), then with the loaded card's store color (#226), and fills text/logo from the **ViewModel** card
   (available once Room emits). (Corrected 2026-11-25: this said the route color was used throughout.) The whole `Scaffold` is the matching shared
   element, keyed from the **route** `cardId` (`cardTransitionKey(cardId)` in `DetailScreen`, #16). Back → `directions.toBack()` → `navigateUp()`.
   See [[card-detail]], [[card-transition]].

### Checklist for a new screen/feature (derived from the example)

Each step is red → green → refactor: the test is written and seen failing before the code.

1. Entity + DAO method returning `Flow` (single-row queries: `Flow<T?>`), and bump the DB version with a migration
   ([[database-schema-changes]]). Test: DAO test on an in-memory database, including the no-row case, and the
   migration's test.
2. Mapper to a domain model in `domain/` (test: plain JUnit, every field). Then a repository interface method
   returning `Flow<Domain>`, implemented by the Room-backed class (test: repository over an in-memory database).
   New repositories need an interface, an impl, a `@Binds` in `di/RepositoryModule.kt` and a fake in
   `app/src/test/…/testing/`.
3. `@HiltViewModel` exposing `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), <EMPTY/initial>)`.
   If an action guards on `state.value` (e.g. ignores an edit the state forbids), the guard must refuse while the
   state hasn't loaded: `Eagerly` and `WhileSubscribed` both start at the initial value, and a Room upstream emits
   later. Start such a state `Eagerly` too, or it goes stale once the screen stops observing it
   (`CardFormViewModel.onNumberChange`, #50).
   Test: constructed with fakes, Turbine on the state, `MainDispatcherRule`.
4. `@Serializable` route + `*Directions` data class + `NavController.*Directions(entry)` factory in `Navigation.kt`;
   register `composable<Route>` in `AppNavigation`. Test: extend or add an `AppNavigationTest`-style flow, one per
   user journey. A popped screen still runs its effects and takes taps while it animates out, so a direction called
   from a `LaunchedEffect` on ViewModel state (e.g. "saved → open it"), or a second tap on a Back arrow, can fire
   after Back; a tap or a late camera frame can also arrive before the next screen covers this one. So every direction
   navigates only while its own back-stack entry is current (`whileCurrent(entry)` in `Navigation.kt`; the factory
   takes the composable's entry, `cardFormDirections(it)`). The write must not be cancelled with `viewModelScope`
   ([[2026-10-28-save-navigates-after-back]]).
5. Screen composable as `SharedTransitionScope.XScreen(animatedVisibilityScope, directions, viewModel = hiltViewModel())`,
   with `Scaffold(containerColor = Color.Transparent)` (the app background comes from `MainActivity`,
   [[card-transition]]). Test: `createComposeRule()`, a fake-backed ViewModel, recording directions.
6. Anything the first frame needs (e.g. colors for a transition) goes in the route, not just the ViewModel.
7. Images: always pass `imageLoader = hiltImageLoader()` to `AsyncImage`.
8. The gate (root `CLAUDE.md`) passes; raise `minBound` if coverage rose
   ([[2026-10-09-coverage-gate-kover]]).
9. Update this wiki per [[CLAUDE]].
