# Coil image loading (brand logos)

Brand logos are remote URLs loaded with **Coil 2.7.0** (`io.coil-kt:coil-compose`, `coil-svg`). The seed data
points every logo at **picsum.photos** placeholders (`https://picsum.photos/seed/<n>/200`, since `c4d6bdc`).
This requires `android.permission.INTERNET`.

## Contract

- `Brand.logo` is a URL string. **Blank means no logo**, and the screens render the brand name instead.
- The app-wide `ImageLoader` is a Hilt `@Singleton` with the SVG decoder and an `EventListener` that logs
  failures at WARN via Timber (`app/src/main/java/com/bdaniel/loyscan/di/ImageLoaderModule.kt:20-35`). Get it in
  Compose with `hiltImageLoader()` and pass it to `AsyncImage(imageLoader = …)` —
  [[2025-12-12-singleton-image-loader]].

## Quirks

- **Coil 2 package names** (`coil.*`, not `coil3.*`). Upgrading to Coil 3 changes the artifact group
  (`io.coil-kt.coil3`) and packages, and needs an explicit network-fetcher artifact.
- `AsyncImage` **without** `imageLoader =` silently uses Coil's own default singleton, which has no SVG support
  and no logging. The list screen does this today: [[list-screen-bypasses-image-loader]].
- picsum.photos returns random photos, not logos. That's fine for demo data. Offline devices just show an empty
  box (no placeholder or error drawable is set).
