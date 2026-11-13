# Glossary

Domain and codebase vocabulary. Paths are under `app/src/main/java/com/bdaniel/loyscan/` unless written in full.
Alphabetical.

**AnimatedVisibilityScope** — Compose scope passed from each `composable<Route> { }` block (`this`) into a screen;
required by `Modifier.sharedElement`. See [[card-transition]].

**AppNavigation** — The `NavHost` composable, defined in `MainActivity.kt:69-116`, that maps routes to screens.

**Barcode content** — The exact string a card's barcode encodes. Derived from the core number by the store's
card-number rule (`CardNumberEngine.barcodeContent`); equals the raw number when there is no rule.
See [[2026-10-09-card-number-rule-engine]].

**Barcode format** — The symbology, enum `domain/BarcodeFormat.kt` (ZXing names, plus `DATABAR*` and the `GS1_` formats). A card's
`barcodeFormat` is derived: `scannedFormat` (column `loyalty_cards.scannedFormat`), else the store rule's
format, else `Store.defaultBarcodeFormat` ([[2026-10-12-data-model-v2]]). Drawn on the detail screen with ZXing
core, falling back to `CODE_128`, then QR (GS1 data: `GS1_128`, then `GS1_QR_CODE`), when the format can't hold
the content or has no writer (DataBar):
[[zxing-core]]. Stored as the enum name. Was a free-form string before #1.

**Brand** — Old name of **Store** (`domain/Brand.kt`, table `brands`) until #1. "Brand colour" is still
used for `Store.color`.

**Brand color** — `Store.color`, a hex string like `#FF0000`, parsed at render time with `String.toBrandColorOrNull()` (`ui/BrandColor.kt`); empty or malformed →
`null`, and the screens fall back to `surfaceVariant`.
Drives card tile and detail header backgrounds; text on it is black or white, whichever has the higher WCAG contrast
ratio (`contrastingTextColor()`; black above a luminance of about 0.18).
Also travels in the `Detail` route — see [[2025-12-12-brand-color-in-route]].

**Store logo** — `Store.logo`: the user's own picture for a store, a file name in the card photos' directory
([[store-logo]], #151); never a URL, and no shipped store has one. `null` shows the store's name (or initials) on its
colour. Loaded with Coil — [[coil-image-loading]].

**Catalogue store** — A store from the shipped catalogue `app/src/main/assets/catalogue/stores.json`
(`domain/catalogue/CatalogueEntry`): slug id like `hu-ikea-family`, no logo, `isCustom = false`. Its row is rewritten
from the catalogue on every DB open. See [[store-catalogue]].

**Card-number rule** — `domain/cardnumber/CardNumberRule`: per-store JSON rule for validating, formatting and
building barcode content from a core number, and matching scanned content back to one.
See [[2026-10-09-card-number-rule-engine]].

**Card code** — Old name of `LoyaltyCard.number` (was `cardNumber` before `4cbc71d`, `code` until #1).

**Card number** — `LoyaltyCard.number`: the raw input, as typed or exactly as scanned (`scannedFormat` set).
Barcode content and display number are derived from it on read ([[2026-10-12-data-model-v2]]).

**CardList** — Navigation route `object CardList` (`Navigation.kt:11-12`), the start destination: "My Cards".
Was `object List` until #17, which shadowed `kotlin.collections.List` — [[list-route-shadows-kotlin-list]].

**`card/{id}`** — The shared-content key that links a list tile to the detail screen in the shared-element transition.
Was `item/{id}` before `45a33e1`.

**Core number** — The card number as the user types it or as it's printed, after normalisation (whitespace and
separators removed, letter case applied). Rules turn it into barcode content. See [[2026-10-09-card-number-rule-engine]].

**DAO** — Room data-access interface in `data/local/db/dao/`. Reads return `Flow`.

**Detail** — Navigation route `data class Detail(val cardId: String, val color: String, val countUse: Boolean = true, val added: Boolean = false)` (`Navigation.kt:40-41`).

**Display number** — `LoyaltyCard.displayNumber`: the number formatted by the store rule's display pattern
(`CardNumberEngine.displayNumber`); the trimmed number when there is no rule. Shown on the detail screen.

**Directions** — Per-screen data class of navigation lambdas (`ListDirections`, `DetailDirections`), created by
`NavController.listDirections(entry)` / `detailDirections(entry)` (`Navigation.kt`). Screens depend on Directions, never on
`NavController`. Every direction navigates only while the screen's own back-stack entry is current
(`whileCurrent`, [[2026-10-28-save-navigates-after-back]]), so each factory takes that entry. See [[2025-12-09-type-safe-navigation-directions]].

**Domain model** — Plain Kotlin data classes in `domain/` (`LoyaltyCard`, `Store`) that the UI consumes.

**`EMPTY`** — Companion sentinel (`LoyaltyCard.EMPTY`, `Store.EMPTY`) with id `""` and empty or null fields; used as
`stateIn` initial value so screens never deal with `null`. Its empty id is why the detail screen's shared key comes from the route ([[shared-element-key-from-async-state]]).

**Entity** — Room table class in `data/local/db/entity/` (`StoreEntity`, `LoyaltyCardEntity`). Stays inside `data/`.

**Frequently added** — Catalogue stores flagged `frequentlyAdded`, offered first in the store picker to a user in one
of their countries (#6). An editorial pick: [[2026-10-30-frequently-added-stores]].

**GS1-128** — Code 128 with a leading FNC1, which tells the reader the content is GS1 data (Application
Identifiers such as `90` or `97`). `BarcodeFormat.GS1_128`: scanned as a Code 128 marked GS1 ([[zxing-cpp]]),
drawn with the FNC1 ([[zxing-core]]). See [[gs1-128-drawn-as-code-128]]. `GS1_DATA_MATRIX` and `GS1_QR_CODE` are
the same for Data Matrix and QR ([[2026-10-21-gs1-mark-as-barcode-format]]).

**GS1 HRI** — Human-readable interpretation: GS1 data as printed under its barcode, each Application Identifier in
brackets, `(01)09501101020917(10)12AB`. `Gs1.humanReadable` (`domain/Gs1.kt`) builds it for the display number of a
GS1 scan; the separator GS (U+001D) between fields isn't shown. See [[2026-10-21-gs1-scans-shown-as-hri]].

**hiltImageLoader()** — Composable in `ui/ImageLoader.kt` that returns the app-wide Coil `ImageLoader` from Hilt
via `ImageLoaderEntryPoint`. See [[2025-12-12-singleton-image-loader]].

**KSP** — Kotlin Symbol Processing; runs the Hilt and Room compilers. Replaced kapt in `ae2118e` —
[[2025-12-08-ksp-instead-of-kapt]].

**Label** — `LoyaltyCard.label`: the user's own optional name for a card (e.g. "Coffee Bean Club"). Was `name`
until #1.

**Loyalty card** — The core entity: a user's membership card for one store. `domain/LoyaltyCard.kt`; table
`loyalty_cards`. UUID string id; fields per [[2026-10-12-data-model-v2]].

**LoyaltyCardWithStore** — Room result class: the card `@Embedded`, its store loaded by `@Relation`
(`data/local/db/dao/LoyaltyCardDao.kt:16-22`). Was `LoyaltyCardWithBrand` (a hand-aliased `JOIN`) until #1.

**`loyscan.db`** — The on-device SQLite file name (`di/DatabaseModule.kt`, `provideAppDatabase`).

**`loyscan.sql`** — The former demo seed script, run line by line on DB creation. Removed in #18; the demo cards then became
debug-only Kotlin, removed in #55 ([[seed-data]]).

**Mapper** — `toDomainModel()` extension functions in `data/mapper/` converting Room results to domain models.

**Quiet zone** — The blank margin a scanner needs around a barcode, in modules (EAN-13: 11 on the left; QR: 4).
Added by `EncodedBarcode` per symbology, since ZXing's are too narrow or missing ([[zxing-core]]).

**Repository** — interfaces in `data/repository/` exposing domain `Flow`s, implemented by `@Singleton` `Room*Repository`
classes and bound in `di/RepositoryModule.kt`; the ViewModels' only data dependency (tests use a fake).

**Room schema export** — JSON snapshots of each DB version written by KSP to `app/schemas/<db class>/<version>.json`
(`app/build.gradle.kts`, `ksp { room.schemaLocation }`). Committed; needed to write migrations. See [[database-schema-changes]].

**Shared element** — Compose `Modifier.sharedElement(rememberSharedContentState(key), animatedVisibilityScope)`
— the same key on two destinations animates bounds between them. Requires the `SharedTransitionLayout` set up in
`MainActivity.kt:60`. See [[card-transition]].

**SharedTransitionScope** — Receiver of `AppNavigation`, `ListScreen`, `DetailScreen`, `CardItem`, so they can call
`sharedElement`. Experimental API (`@OptIn(ExperimentalSharedTransitionApi::class)`).

**Slug id** — A catalogue store's id: the ISO country code, then lowercase letters, digits and single hyphens
(`hu-tesco`). Never changes once shipped. See [[2026-10-13-curated-store-catalogue]].

**Store** — The issuer of a card: a catalogue store (stable slug id) or a custom store the user created
(`isCustom`, UUID id). `domain/Store.kt`; table `stores`. Several cards can share one store. Carries the optional
card-number rule. Was `Brand` until #1 — [[2026-10-12-data-model-v2]].

**Timber** — Logging library; `DebugTree` planted in debug builds only (`LoyScanApplication.kt:11-13`).
