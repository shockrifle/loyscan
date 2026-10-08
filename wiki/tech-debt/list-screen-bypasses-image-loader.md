# List tiles don't use the app's ImageLoader

**Status:** Open · **Since:** 2025-12-12 (`45a33e1`) · **Where:** `CardItem` in
`app/src/main/java/com/bdaniel/loyscan/ListScreen.kt` (`AsyncImage(model = card.brand.logo, …)` with no
`imageLoader`)

## What
`DetailScreen` passes `imageLoader = hiltImageLoader()`; `CardItem` does not, so it uses Coil's default singleton.

## Why it exists
`45a33e1` added logos to the tiles after `3401694` had extracted the shared loader; the parameter was most likely
just missed.

## Risk / impact
- SVG logos would render in the detail screen but **not** in the list (no `SvgDecoder` in the default loader).
- List image failures are not logged.
- Two separate memory caches for the same URLs.

## How to pay it down
Add `imageLoader = hiltImageLoader()` to the tile's `AsyncImage`. To prevent recurrences, consider making the Hilt
loader Coil's default (see the Consequences in [[2025-12-12-singleton-image-loader]]).
