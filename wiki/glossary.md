# Glossary

Domain and codebase vocabulary. Paths are under `app/src/main/java/com/bdaniel/loyscan/` unless written in full.
Alphabetical.

**AnimatedVisibilityScope** — Compose scope passed from each `composable<Route> { }` block (`this`) into a screen;
required by `Modifier.sharedElement`. See [[card-transition]].

**AppNavigation** — The `NavHost` composable, defined in `MainActivity.kt:45-69`, that maps routes to screens.

**Barcode content** — The exact string a card's barcode encodes. Derived from the core number by the store's
card-number rule (`CardNumberEngine.barcodeContent`); equals the raw number when there is no rule.
See [[2026-10-09-card-number-rule-engine]].

**Barcode format** — `LoyaltyCard.barcodeFormat` / column `loyalty_cards.barcodeFormat`: a free-form string
naming the barcode symbology (`EAN_13`, `EAN_8`, `CODE_128`, `CODE_39`, `QR_CODE`, `AZTEC`, `DATA_MATRIX`,
`PDF_417` appear in the seed). Not an enum; not rendered anywhere yet. The values match ZXing/ML Kit
`BarcodeFormat` names, but no barcode library is a dependency. The enum `domain/BarcodeFormat.kt` (2026-10-09)
is used by card-number rules; the card field is still a string until #1.

**Brand** — The issuer of a card (a store/chain). `domain/Brand.kt`; table `brands`. Has `name`, `color`, `logo`.
Several cards can share one brand.

**Brand color** — `Brand.color`, a hex string like `#FF0000`, parsed with `String.toColorInt()` at render time.
Drives card tile and detail header backgrounds; text on it is black/white by luminance (< 0.5 → white).
Also travels in the `Detail` route — see [[2025-12-12-brand-color-in-route]].

**Brand logo** — `Brand.logo`, an image URL (seed uses `https://picsum.photos/seed/<n>/200`). Blank means
"show the brand name as text instead". Loaded with Coil — [[coil-image-loading]].

**Card-number rule** — `domain/cardnumber/CardNumberRule`: per-store JSON rule for validating, formatting and
building barcode content from a core number, and matching scanned content back to one.
See [[2026-10-09-card-number-rule-engine]].

**Card code** — `LoyaltyCard.code`: the membership number the barcode would encode. Was `cardNumber` before `4cbc71d`.

**`card/{id}`** — The shared-content key that links a list tile to the detail screen in the shared-element transition.
Was `item/{id}` before `45a33e1`.

**Core number** — The card number as the user types it or as it's printed, after normalisation (whitespace and
separators removed, letter case applied). Rules turn it into barcode content. See [[2026-10-09-card-number-rule-engine]].

**DAO** — Room data-access interface in `data/local/db/dao/`. Reads return `Flow`.

**Detail** — Navigation route `data class Detail(val cardId: Int, val color: String)` (`Navigation.kt:24-25`).

**Directions** — Per-screen data class of navigation lambdas (`ListDirections`, `DetailDirections`), created by
`NavController.listDirections()` / `detailDirections()` (`Navigation.kt`). Screens depend on Directions, never on
`NavController`. See [[2025-12-09-type-safe-navigation-directions]].

**Domain model** — Plain Kotlin data classes in `domain/` (`LoyaltyCard`, `Brand`) that the UI consumes.

**`EMPTY`** — Companion sentinel (`LoyaltyCard.EMPTY`, `Brand.EMPTY`) with id `0` and empty strings; used as
`stateIn` initial value so screens never deal with `null`. Its `id = 0` matters for [[shared-element-key-from-async-state]].

**Entity** — Room table class in `data/local/db/entity/` (`BrandEntity`, `LoyaltyCardEntity`). Stays inside `data/`.

**hiltImageLoader()** — Composable in `ui/ImageLoader.kt` that returns the app-wide Coil `ImageLoader` from Hilt
via `ImageLoaderEntryPoint`. See [[2025-12-12-singleton-image-loader]].

**KSP** — Kotlin Symbol Processing; runs the Hilt and Room compilers. Replaced kapt in `ae2118e` —
[[2025-12-08-ksp-instead-of-kapt]].

**List** — Navigation route `object List` (`Navigation.kt:7-8`), the start destination. **Shadows
`kotlin.collections.List`** in the root package — [[list-route-shadows-kotlin-list]].

**Loyalty card** — The core entity: a membership card for a brand. `domain/LoyaltyCard.kt`; table `loyalty_cards`.
Fields: `id`, `name` (user-facing card name, e.g. "Coffee Bean Club"), `brand`, `code`, `barcodeFormat`.

**LoyaltyCardWithBrand** — Room result class joining a card with its brand via two `@Embedded` fields, brand
columns prefixed `brand_` (`data/local/db/dao/LoyaltyCardDao.kt:13-18`).

**`loyscan.db`** — The on-device SQLite file name (`di/DatabaseModule.kt:25`).

**`loyscan.sql`** — Seed script `app/src/main/assets/database/loyscan.sql`, executed line-by-line on DB creation.
See [[seed-data]], [[line-based-sql-seed-parser]].

**Mapper** — `toDomainModel()` extension functions in `data/mapper/` converting Room results to domain models.

**Repository** — `@Singleton` classes in `data/repository/` exposing domain `Flow`s; the ViewModels' only data dependency.

**Room schema export** — JSON snapshots of each DB version written by KSP to `app/schemas/<db class>/<version>.json`
(`app/build.gradle.kts:27-29`). Committed; needed to write migrations. See [[database-schema-changes]].

**Shared element** — Compose `Modifier.sharedElement(rememberSharedContentState(key), animatedVisibilityScope)`
— the same key on two destinations animates bounds between them. Requires the `SharedTransitionLayout` set up in
`MainActivity.kt:36`. See [[card-transition]].

**SharedTransitionScope** — Receiver of `AppNavigation`, `ListScreen`, `DetailScreen`, `CardItem`, so they can call
`sharedElement`. Experimental API (`@OptIn(ExperimentalSharedTransitionApi::class)`).

**Timber** — Logging library; `DebugTree` planted in debug builds only (`LoyScanApplication.kt:11-13`).
