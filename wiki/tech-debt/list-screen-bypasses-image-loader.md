# List tiles don't use the app's ImageLoader

**Status:** Resolved (`08c4eca`, 2026-11-03, #10) · **Issue:** [#10](https://github.com/shockrifle/loyscan/issues/10) (was
[#15](https://github.com/shockrifle/loyscan/issues/15), folded into #7 on 2026-10-22, moved to #10 on 2026-10-30) · **Since:** 2025-12-12 (`45a33e1`) · **Where:** `CardItem` in
`app/src/main/java/com/bdaniel/loyscan/ListScreen.kt` (`AsyncImage(model = card.store.logo, …)` with no
`imageLoader`)

## What
`DetailScreen` passes `imageLoader = hiltImageLoader()`; `CardItem` does not, so it uses Coil's default singleton.

## Why it exists
`45a33e1` added logos to the tiles after `3401694` had extracted the shared loader; the parameter was most likely
just missed.

## Risk / impact
- No effect today: catalogue stores have no logo ([[2026-10-13-curated-store-catalogue]]) and custom stores have
  none either, so the tile's `AsyncImage` never runs (checked 2026-10-22).
- It matters again when a tile shows an image, e.g. the card's front photo (#7, #10): it would then load without
  the app's decoders, logging and cache.

## How to pay it down
Load any tile image through `hiltImageLoader()`, which #10 lists as part of its photo tiles. To prevent recurrences,
consider making the Hilt loader Coil's default (see the Consequences in [[2025-12-12-singleton-image-loader]]).

## Resolved
`08c4eca` (2026-11-03), with #10's photo tiles: `CardItem` passes `hiltImageLoader()` to both its images, the front photo and the
logo. The photo proves it: only the app's loader maps a `CardPhoto` to its file, and
`AppNavigationTest.aCardsFrontPhoto_isLoadedOntoItsTile_byTheAppsImageLoader` waits for the photo in that loader's
memory cache (it fails with the parameter removed). Making the Hilt loader Coil's default was not done ([[2025-12-12-singleton-image-loader]]).
