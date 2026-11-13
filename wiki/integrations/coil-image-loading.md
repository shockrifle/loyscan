# Coil image loading

Images are loaded with **Coil 2.7.0** (`io.coil-kt:coil-compose`, `coil-svg`) through one app-wide loader. Since #151
every image the app shows is a private file: a card photo ([[card-photos]]) or the store logo the user gave a store
([[2026-11-13-user-supplied-store-logos]]). No shipped store has a logo, and none is fetched
([[2026-11-12-no-bundled-store-logos]]); the debug demo stores that pointed at **picsum.photos** placeholders were removed
in #55 ([[seed-data]]).

## Contract

- `Store.logo` is a file name in the card photos' directory, or `null` (the screens render the store name or initials
  instead). It was a URL until #151; nothing ever stored one but the removed demo stores.
- Both a photo and a logo are shown as a `CardPhoto(name)` model, which only the app's loader maps to its file
  (`CardPhotoMapper`, `ui/CardPhoto.kt`).
- The app-wide `ImageLoader` is a Hilt `@Singleton` with the SVG decoder, the `CardPhotoMapper` and an `EventListener`
  that logs failures at WARN via Timber (`app/src/main/java/com/bdaniel/loyscan/di/ImageLoaderModule.kt`). Get it in
  Compose with `hiltImageLoader()` and pass it to `AsyncImage(imageLoader = …)` —
  [[2025-12-12-singleton-image-loader]].

## Quirks

- **Coil 2 package names** (`coil.*`, not `coil3.*`). Upgrading to Coil 3 changes the artifact group
  (`io.coil-kt.coil3`) and packages, and needs an explicit network-fetcher artifact.
- `AsyncImage` **without** `imageLoader =` silently uses Coil's own default singleton, which has no SVG support,
  no logging and no `CardPhotoMapper`, so a card photo fails on it. The list tiles did this until #10
  ([[list-screen-bypasses-image-loader]], resolved); every `AsyncImage` now passes `hiltImageLoader()`.
- No placeholder or error drawable is set. A tile or header whose photo or logo can't load sets an `onError` flag and
  falls back to the initials or the name.
- The SVG decoder and the `INTERNET` permission no longer serve any store field: the decision to keep or remove them
  is [[2026-11-12-no-bundled-store-logos]] → Consequences.
