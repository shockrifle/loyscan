# One app-wide Coil `ImageLoader`, provided by Hilt

**Status:** Active · **Date:** 2025-12-12 · **Commits:** `c4d6bdc` (inline loader), `3401694` (extracted)

## Context
`c4d6bdc` built a `coil.ImageLoader` (with `SvgDecoder` and a Timber error listener) **inline in
`DetailScreen`'s composition** — a new loader, with its own memory cache, on every recomposition.

## Options considered
- `remember { ImageLoader.Builder(...) }` per screen.
- Make it Coil's global default (`ImageLoaderFactory` on the `Application`).
- Provide a `@Singleton` via Hilt and fetch it in Compose.

## Decision
`ImageLoaderModule.provideImageLoader` is a `@Singleton` `@Provides`, plus `ImageLoaderEntryPoint`
(`app/src/main/java/com/bdaniel/loyscan/di/ImageLoaderModule.kt:16-42`). The composable `hiltImageLoader()` reads
it via `EntryPointAccessors.fromApplication`, `remember`ed per context
(`app/src/main/java/com/bdaniel/loyscan/ui/ImageLoader.kt:10-19`). The error log level was lowered from `e` to `w`.

## Why
Inferred from the commit ("extracted image loader"): one shared loader means one memory/disk cache and no
per-recomposition allocation, and DI keeps the configuration in one place. An EntryPoint is the standard way to
reach Hilt singletons from composables, which can't use `@Inject`.

## Consequences
- Every `AsyncImage` must pass `imageLoader = hiltImageLoader()`. Forgetting it silently falls back to Coil's
  default loader — this already happened in the list screen: [[list-screen-bypasses-image-loader]].
- An alternative that would remove that foot-gun: have `LoyScanApplication` implement Coil's
  `ImageLoaderFactory` returning the Hilt instance. Not done.
- See [[coil-image-loading]].
