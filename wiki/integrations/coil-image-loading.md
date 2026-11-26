# Coil image loading

Images on the app's screens are loaded with **Coil 2.7.0** (`io.coil-kt:coil-compose`; `coil-svg` was dropped in #157) through one app-wide loader; the home-screen widget decodes its store logos and card photos itself with `BitmapFactory`
(`WidgetPictures`, [[card-widget]], #181, #185). Since #151
every image the app shows is a private file: a card photo ([[card-photos]]) or the store logo the user gave a store
([[2026-11-13-user-supplied-store-logos]]). No shipped store has a logo, and none is fetched
([[2026-11-12-no-bundled-store-logos]]); the debug demo stores that pointed at **picsum.photos** placeholders were removed
in #55 ([[seed-data]]).

## Contract

- `Store.logo` is a file name in the card photos' directory, or `null` (the screens render the store name or initials
  instead). It was a URL until #151; nothing ever stored one but the removed demo stores.
- Both a photo and a logo are shown as a `CardPhoto(name)` model, which only the app's loader maps to its file
  (`CardPhotoMapper`, `ui/CardPhoto.kt`).
- The app-wide `ImageLoader` is a Hilt `@Singleton` with the `CardPhotoMapper` and an `EventListener`
  that logs failures at WARN via Timber (`app/src/main/java/com/bdaniel/loyscan/di/ImageLoaderModule.kt`). Get it in
  Compose with `hiltImageLoader()` and pass it to `AsyncImage(imageLoader = …)` —
  [[2025-12-12-singleton-image-loader]].

## Quirks

- **Coil 2 package names** (`coil.*`, not `coil3.*`). Upgrading to Coil 3 changes the artifact group
  (`io.coil-kt.coil3`) and packages, and needs an explicit network-fetcher artifact.
- `AsyncImage` **without** `imageLoader =` silently uses Coil's own default singleton, which has no logging
  and no `CardPhotoMapper`, so a card photo fails on it. The list tiles did this until #10
  ([[list-screen-bypasses-image-loader]], resolved); every `AsyncImage` now passes `hiltImageLoader()`.
- No placeholder or error drawable is set. A tile or the card screen's band whose photo or logo can't load sets an
  `onError` flag and falls back to the initials.
- **No `INTERNET` permission** since #157 (2026-11-17), so a URL load fails at run time. Coil 2's `coil-base` still
  ships its OkHttp network fetcher; only the permission is gone. `AppPermissionsTest` fails if the permission comes
  back, from the app's manifest or a library's ([[android-app]] → Manifest facts). The SVG decoder (`coil-svg`) was
  removed in the same change: every image is a PNG or JPEG file the app wrote. An SVG logo is drawn into a PNG when
  it is copied in, before Coil sees it ([[androidsvg]], #211).
