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
| **Persistence** | `data/local/db/` — `AppDatabase`, `entity/*Entity`, `dao/*Dao` | Room tables, queries returning `Flow` | Entities never leave `data/`. Joins are flattened via `@Embedded` (see `LoyaltyCardWithBrand`). |
| **Mapping** | `data/mapper/LoyaltyCardMapper.kt` | `LoyaltyCardWithBrand.toDomainModel()` | Extension functions, one direction (DB → domain). |
| **Repository** | `data/repository/` — `LoyaltyCardRepository` (interface) + `RoomLoyaltyCardRepository` (`@Singleton` impl); `BrandRepository` | Exposes `Flow<domain>` | ViewModels depend on the **interface**, so tests pass a fake (see Testing seams). Reads return domain models. |
| **Domain** | `domain/` — `LoyaltyCard`, `Brand`, `BarcodeFormat`; `domain/cardnumber/` — card-number rule engine ([[2026-10-09-card-number-rule-engine]]) | Plain data classes with an `EMPTY` sentinel; pure-Kotlin logic | No Android/Room imports — unit-tested on the JVM. |
| **Presentation state** | root package — `ListViewModel`, `DetailViewModel` | `@HiltViewModel`; turn repository Flows into `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` | Route args read with `savedStateHandle.toRoute<Route>()`. |
| **UI** | root package — `ListScreen`, `DetailScreen`; `ui/` — theme, icons, `hiltImageLoader()` | Composables, extensions on `SharedTransitionScope` | Screens receive a `*Directions` object, never a `NavController`. |
| **Navigation** | `Navigation.kt`, `MainActivity.kt` (`AppNavigation`) | `@Serializable` routes + `NavController.*Directions()` factories | See [[2025-12-09-type-safe-navigation-directions]]. |
| **DI** | `di/DatabaseModule.kt`, `di/ImageLoaderModule.kt`, `di/RepositoryModule.kt` | `@Module @InstallIn(SingletonComponent)` providers; `@Binds` repository interface → Room impl | Implementations use `@Inject constructor`. |

Note: `LoyaltyCardRepository` has no write methods; the unused entity-taking `insert/delete` were dropped when
it became an interface (#26). `BrandRepository` is still a concrete class whose `insert` takes an **entity**, and
nothing injects it. When a feature first needs writes, add them to the interface in domain terms, with
domain→entity mappers.

### Composition / DI

- `LoyScanApplication` is `@HiltAndroidApp` (`LoyScanApplication.kt:7`); `MainActivity` is `@AndroidEntryPoint`.
- `DatabaseModule` provides the singleton `AppDatabase` (file `loyscan.db`, seeded on creation — see
  [[seed-data]]) and unscoped DAO providers (`di/DatabaseModule.kt`). The seeding itself is `SeedDatabaseCallback`
  (`data/local/db/`).
- `ImageLoaderModule` provides a singleton Coil `ImageLoader`. Composables can't `@Inject`, so it is reached
  through a Hilt `@EntryPoint` wrapped in the `hiltImageLoader()` composable (`ui/ImageLoader.kt:10-19`).
  See [[2025-12-12-singleton-image-loader]] and [[coil-image-loading]].
- ViewModels are obtained with `hiltViewModel()` as a default parameter of each screen.

### Testing seams

Tests run on the JVM (`./gradlew testDebugUnitTest`, see [[build-and-run]]); shared helpers live in
`app/src/test/java/com/bdaniel/loyscan/testing/`.

- **ViewModels** are constructed directly with `FakeLoyaltyCardRepository` (a `MutableStateFlow` of domain cards)
  and `MainDispatcherRule` in place of `Dispatchers.Main`. A route-reading ViewModel gets
  `SavedStateHandle(route = Detail(…))` from navigation-testing.
- **Screens** take the ViewModel and a `*Directions` object as parameters, so a test passes a fake-backed
  ViewModel and lambdas that record navigation (`ListScreenTest`).
- **DAOs and the repository** are tested against `Room.inMemoryDatabaseBuilder` (`LoyaltyCardDaoTest`,
  `RoomLoyaltyCardRepositoryTest`). The seed script runs through the real `SeedDatabaseCallback` with a direct
  executor, so it completes synchronously (`SeedDatabaseCallbackTest`).
- **The wired-up app** (`MainActivity`, Hilt, navigation) runs in `@HiltAndroidTest`s on Robolectric.
  `testing/InMemoryDatabaseModule` (`@TestInstallIn`) swaps `DatabaseModule` for an empty in-memory database
  that the test injects and fills (`AppNavigationTest`).
- Fixture builders (`aLoyaltyCard()`, `aBrandEntity()`, …) give every field a default.

### Build tooling

Version catalog `gradle/libs.versions.toml`; KSP (not kapt) for Hilt + Room ([[2025-12-08-ksp-instead-of-kapt]]);
Room schemas exported to `app/schemas/` (`app/build.gradle.kts`, `ksp { room.schemaLocation }`); Java/JVM target 21; minSdk 29,
target/compileSdk 36. Kover gates line coverage ([[2026-10-09-coverage-gate-kover]]). See [[android-app]] and
[[build-and-run]].

## Worked example: tap a card → see its details

This traces one feature through every layer. **Use it as the template for new features.** Each layer starts with
its test: write it, watch it fail, then write the code ([[tdd]], [[2026-10-12-tdd-and-coverage]]). The tests named
here exist; read them next to the code.

1. **Data at rest.** Rows live in `brands` and `loyalty_cards` (FK `brandId` → `brands.id`, `ON DELETE CASCADE`,
   indexed): `data/local/db/entity/LoyaltyCardEntity.kt:8-19`. On first launch they're inserted from
   `app/src/main/assets/database/loyscan.sql` ([[seed-data]]).
   *Test first:* `SeedDatabaseCallbackTest`: the script loads, and the row counts and one full row match.
2. **DAO query.** *Test first:* `LoyaltyCardDaoTest` inserts a brand and cards whose every field differs, then
   expects the joined row (`getByIdWithBrand_mapsEveryCardAndBrandField`). A missing id emits `null`
   (`…_unknownId_emitsNull`).
   Then the code: `LoyaltyCardDao.getAllWithBrand()` / `getByIdWithBrand(id)` run a `JOIN` and alias brand
   columns with a `brand_` prefix, so they map onto `@Embedded(prefix = "brand_") val brand: BrandEntity`
   (`data/local/db/dao/LoyaltyCardDao.kt:13-18, 31-36`). They return `Flow`, so the UI updates when tables change.
   The single-card query returns `Flow<…?>`: with a non-null type Room throws for a missing row
   ([[2026-10-09-missing-card-throws-from-room]]).
3. **Mapping.** *Test first:* `LoyaltyCardMapperTest.toDomainModel_copiesEveryField`.
   Then `LoyaltyCardWithBrand.toDomainModel()` builds `LoyaltyCard(…, brand = Brand(…))`
   (`data/mapper/LoyaltyCardMapper.kt:11-24`).
4. **Repository.** *Test first:* `RoomLoyaltyCardRepositoryTest`: a real in-memory database in, domain cards out.
   Then `RoomLoyaltyCardRepository.getAllWithBrand()` = `dao.getAllWithBrand().map { it.toDomainModel() }`
   (`data/repository/RoomLoyaltyCardRepository.kt:14`), behind the `LoyaltyCardRepository` interface. Change
   `FakeLoyaltyCardRepository` in the same step, so it keeps the interface's contract.
5. **List ViewModel.** *Test first:* `ListViewModelTest`: built with the fake repository, Turbine on `cards`. It
   checks the initial empty list, then each update.
   Then `ListViewModel.cards` = repository flow `.stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())`
   (`ListViewModel.kt:16-20`).
6. **List UI.** *Test first:* `ListScreenTest`: a fake-backed ViewModel and a recording `ListDirections`. It
   checks one tile per card, and that a click calls `toCard(id, color)`.
   Then `ListScreen` collects `cards` and renders a 2-column `LazyVerticalGrid` of `CardItem`s. Each `CardItem`'s
   colored box is a shared element keyed `"card/${card.id}"`. A click calls
   `directions.toCard(card.id, card.brand.color)` ([[card-list]]).
7. **Navigation.** *Test first:* `AppNavigationTest`, the one end-to-end test for this flow: real Hilt and
   navigation over `InMemoryDatabaseModule`, list → detail → back.
   Then `NavController.listDirections().toCard` → `navigate(Detail(id, color))` (`Navigation.kt:16-21, 24-25`).
   `AppNavigation` maps `composable<Detail>` to `DetailScreen`, pulling `color` out of the route and passing it
   as a plain parameter (`MainActivity.kt:60-67`).
8. **Detail ViewModel.** *Test first:* `DetailViewModelTest` with `SavedStateHandle(route = Detail(…))`. It
   checks `EMPTY` first, then the routed card. A missing card stays `EMPTY`, and a deleted card keeps the last
   value.
   Then `DetailViewModel` reads `savedStateHandle.toRoute<Detail>().cardId`, then
   `repository.getByIdWithBrand(cardId).filterNotNull().stateIn(…, initialValue = LoyaltyCard.EMPTY)`
   (`DetailViewModel.kt:22-31`).
9. **Detail UI.** *Test first:* `DetailScreenTest`: it shows the name and code, the back arrow calls `toBack`,
   and an empty or malformed route color doesn't crash.
   Then `DetailScreen` paints the top bar and header with the **route** color (available on frame 1), and fills
   text/logo from the **ViewModel** card (available once Room emits). The whole `Scaffold` is the matching shared
   element `"card/${card.id}"` (`DetailScreen.kt:64-72`). Back → `directions.toBack()` → `navigateUp()`.
   See [[card-detail]], [[card-transition]].

### Checklist for a new screen/feature (derived from the example)

Each step is red → green → refactor: the test is written and seen failing before the code.

1. Entity + DAO method returning `Flow` (single-row queries: `Flow<T?>`), and bump the DB version
   ([[database-schema-changes]]). Test: DAO test on an in-memory database, including the no-row case.
2. Mapper to a domain model in `domain/` (test: plain JUnit, every field). Then a repository interface method
   returning `Flow<Domain>`, implemented by the Room-backed class (test: repository over an in-memory database).
   New repositories need an interface, an impl, a `@Binds` in `di/RepositoryModule.kt` and a fake in
   `app/src/test/…/testing/`.
3. `@HiltViewModel` exposing `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), <EMPTY/initial>)`.
   Test: constructed with fakes, Turbine on the state, `MainDispatcherRule`.
4. `@Serializable` route + `*Directions` data class + `NavController.*Directions()` factory in `Navigation.kt`;
   register `composable<Route>` in `AppNavigation`. Test: extend or add an `AppNavigationTest`-style flow, one per
   user journey.
5. Screen composable as `SharedTransitionScope.XScreen(animatedVisibilityScope, directions, viewModel = hiltViewModel())`,
   with `Scaffold(containerColor = Color.Transparent)` (the app background comes from `MainActivity`,
   [[card-transition]]). Test: `createComposeRule()`, a fake-backed ViewModel, recording directions.
6. Anything the first frame needs (e.g. colors for a transition) goes in the route, not just the ViewModel.
7. Images: always pass `imageLoader = hiltImageLoader()` to `AsyncImage`.
8. `./gradlew testDebugUnitTest koverVerify` passes; raise `minBound` if coverage rose
   ([[2026-10-09-coverage-gate-kover]]).
9. Update this wiki per [[CLAUDE]].
