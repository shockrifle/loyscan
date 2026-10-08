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
| **Repository** | `data/repository/*Repository` | `@Singleton`, exposes `Flow<domain>` and `suspend` writes | Reads return domain models. Writes currently still take *entities* (see note below). |
| **Domain** | `domain/` — `LoyaltyCard`, `Brand` | Plain data classes with an `EMPTY` sentinel | No Android/Room imports. |
| **Presentation state** | root package — `ListViewModel`, `DetailViewModel` | `@HiltViewModel`; turn repository Flows into `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` | Route args read with `savedStateHandle.toRoute<Route>()`. |
| **UI** | root package — `ListScreen`, `DetailScreen`; `ui/` — theme, icons, `hiltImageLoader()` | Composables, extensions on `SharedTransitionScope` | Screens receive a `*Directions` object, never a `NavController`. |
| **Navigation** | `Navigation.kt`, `MainActivity.kt` (`AppNavigation`) | `@Serializable` routes + `NavController.*Directions()` factories | See [[2025-12-09-type-safe-navigation-directions]]. |
| **DI** | `di/DatabaseModule.kt`, `di/ImageLoaderModule.kt` | `@Module @InstallIn(SingletonComponent)` providers | Repositories use `@Inject constructor` — no module needed. |

Note: `LoyaltyCardRepository.insert/delete` and `BrandRepository.insert` accept **entities**
(`data/repository/LoyaltyCardRepository.kt:17-23`), so the domain/data boundary is only enforced on reads.
Nothing calls these writes yet; when a feature first needs them, prefer adding domain→entity mappers.

### Composition / DI

- `LoyScanApplication` is `@HiltAndroidApp` (`LoyScanApplication.kt:7`); `MainActivity` is `@AndroidEntryPoint`.
- `DatabaseModule` provides the singleton `AppDatabase` (file `loyscan.db`, seeded on creation — see
  [[seed-data]]) and unscoped DAO providers (`di/DatabaseModule.kt:20-46`).
- `ImageLoaderModule` provides a singleton Coil `ImageLoader`. Composables can't `@Inject`, so it is reached
  through a Hilt `@EntryPoint` wrapped in the `hiltImageLoader()` composable (`ui/ImageLoader.kt:10-19`).
  See [[2025-12-12-singleton-image-loader]] and [[coil-image-loading]].
- ViewModels are obtained with `hiltViewModel()` as a default parameter of each screen.

### Build tooling

Version catalog `gradle/libs.versions.toml`; KSP (not kapt) for Hilt + Room ([[2025-12-08-ksp-instead-of-kapt]]);
Room schemas exported to `app/schemas/` (`app/build.gradle.kts:27-29`); Java/JVM target 21; minSdk 29,
target/compileSdk 36. See [[android-app]] and [[build-and-run]].

## Worked example: tap a card → see its details

This traces one feature through every layer. **Use it as the template for new features.**

1. **Data at rest.** Rows live in `brands` and `loyalty_cards` (FK `brandId` → `brands.id`, `ON DELETE CASCADE`,
   indexed) — `data/local/db/entity/LoyaltyCardEntity.kt:8-19`. On first launch they're inserted from
   `app/src/main/assets/database/loyscan.sql` ([[seed-data]]).
2. **DAO query.** `LoyaltyCardDao.getAllWithBrand()` / `getByIdWithBrand(id)` run a `JOIN` and alias brand
   columns with a `brand_` prefix so they map onto `@Embedded(prefix = "brand_") val brand: BrandEntity`
   (`data/local/db/dao/LoyaltyCardDao.kt:13-18, 31-35`). They return `Flow`, so the UI updates when tables change.
3. **Mapping.** `LoyaltyCardWithBrand.toDomainModel()` builds `LoyaltyCard(…, brand = Brand(…))`
   (`data/mapper/LoyaltyCardMapper.kt:11-24`).
4. **Repository.** `LoyaltyCardRepository.getAllWithBrand()` = `dao.getAllWithBrand().map { it.toDomainModel() }`
   (`data/repository/LoyaltyCardRepository.kt:13-15`).
5. **List ViewModel.** `ListViewModel.cards` = repository flow `.stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())`
   (`ListViewModel.kt:16-20`).
6. **List UI.** `ListScreen` collects `cards`, renders a 2-column `LazyVerticalGrid` of `CardItem`s; each
   `CardItem`'s colored box is a shared element keyed `"card/${card.id}"`. Click calls
   `directions.toCard(card.id, card.brand.color)` ([[card-list]]).
7. **Navigation.** `NavController.listDirections().toCard` → `navigate(Detail(id, color))`
   (`Navigation.kt:16-21, 24-25`). `AppNavigation` maps `composable<Detail>` to `DetailScreen`, pulling
   `color` out of the route and passing it as a plain parameter (`MainActivity.kt:50-57`).
8. **Detail ViewModel.** `DetailViewModel` reads `savedStateHandle.toRoute<Detail>().cardId`, then
   `repository.getByIdWithBrand(cardId).filterNotNull().stateIn(…, initialValue = LoyaltyCard.EMPTY)`
   (`DetailViewModel.kt:22-31`).
9. **Detail UI.** `DetailScreen` paints the top bar and header with the **route** color (available on frame 1),
   and fills text/logo from the **ViewModel** card (available once Room emits). The whole `Scaffold` is the
   matching shared element `"card/${card.id}"` (`DetailScreen.kt:63-71`). Back → `directions.toBack()` → `navigateUp()`.
   See [[card-detail]], [[card-transition]].

### Checklist for a new screen/feature (derived from the example)

1. Entity + DAO method returning `Flow` (and bump DB version — [[database-schema-changes]]).
2. Mapper to a domain model in `domain/`; repository method returning `Flow<Domain>`.
3. `@HiltViewModel` exposing `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), <EMPTY/initial>)`.
4. `@Serializable` route + `*Directions` data class + `NavController.*Directions()` factory in `Navigation.kt`;
   register `composable<Route>` in `AppNavigation`.
5. Screen composable as `SharedTransitionScope.XScreen(animatedVisibilityScope, directions, viewModel = hiltViewModel())`.
6. Anything the first frame needs (e.g. colors for a transition) goes in the route, not just the ViewModel.
7. Images: always pass `imageLoader = hiltImageLoader()` to `AsyncImage`.
8. Update this wiki per [[CLAUDE]].
