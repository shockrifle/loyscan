# Coil image loading (brand logos)

Brand logos are remote URLs loaded with **Coil 2.7.0** (`io.coil-kt:coil-compose`, `coil-svg`). No shipped store
has a logo today: the catalogue ships none ([[store-catalogue]]), and the debug demo stores that pointed at
**picsum.photos** placeholders were removed in #55 ([[seed-data]]).
This requires `android.permission.INTERNET`.

## Contract

- `Store.logo` is a nullable URL string. **`null` or blank means no logo**, and the screens render the store name instead.
- The app-wide `ImageLoader` is a Hilt `@Singleton` with the SVG decoder and an `EventListener` that logs
  failures at WARN via Timber (`app/src/main/java/com/bdaniel/loyscan/di/ImageLoaderModule.kt:22-39`). Get it in
  Compose with `hiltImageLoader()` and pass it to `AsyncImage(imageLoader = …)` —
  [[2025-12-12-singleton-image-loader]].

## Quirks

- **Coil 2 package names** (`coil.*`, not `coil3.*`). Upgrading to Coil 3 changes the artifact group
  (`io.coil-kt.coil3`) and packages, and needs an explicit network-fetcher artifact.
- `AsyncImage` **without** `imageLoader =` silently uses Coil's own default singleton, which has no SVG support
  and no logging. The list screen does this today: [[list-screen-bypasses-image-loader]].
- No placeholder or error drawable is set, so a logo that can't load (e.g. offline) shows an empty box. The former
  demo stores used `picsum.photos` placeholders ([[seed-data]], removed in #55).
