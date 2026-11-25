# FAQ

Questions that took real effort to answer once. Newest at the bottom; keep answers short and sourced.

### My debug build still shows the "Brand A" … demo cards.
The demo seed was removed (#55), but it only ever ran when the DB file was created, so an older install keeps its
rows. Clear app data once: see [[reset-local-database]] and [[seed-data]]. Catalogue changes, by contrast, apply on
the next open ([[store-catalogue]]).

### I added a field to an entity and the app crashes on launch.
Every schema version from 2 on is frozen, so a field added without bumping `AppDatabase.VERSION` fails
`FrozenSchemaTest` before it reaches a device. Bump the version, pin the new schema and add a migration:
[[database-schema-changes]]. A dev install from an in-between build can be wiped ([[reset-local-database]]).

### Why does the detail screen get the brand color as a navigation argument when the ViewModel loads the card anyway?
So the color is available on the very first frame of the shared-element transition, before Room has
emitted. See [[2025-12-12-brand-color-in-route]] and [[2025-12-12-detail-color-flash-on-transition]].

### Why are icons hand-written `ImageVector`s instead of `Icons.Default.*` from Material?
The Material icons stopped resolving after the Compose BOM upgrade. See [[2025-12-08-hand-rolled-icons]].

### Why set a `SavedStateHandle` key to `null` instead of removing it, to clear it?
`savedStateHandle.remove(key)` clears the stored value (so `get` returns `null`) but does **not** emit to a
`getStateFlow(key, …)` collector, so anything observing that flow (e.g. a `combine` feeding the UI state) keeps the
stale value. Setting `savedStateHandle[key] = null` emits `null` and clears `get`. The card form clears the crop
screen's keys this way (`CardFormViewModel.clearCropping`, #10); keys read only through `get` (the camera capture
keys) can still be removed. Found while building the crop flow: `cancelCrop` left the crop screen on screen.

### Where do logs go?
Timber, with a `DebugTree` planted only in debug builds (`LoyScanApplication.onCreate`). Release builds log
nothing. Image-load failures are logged at WARN with the message prefix `Image loading failed for:`
(`ImageLoaderModule.provideImageLoader` in `app/src/main/java/com/bdaniel/loyscan/di/ImageLoaderModule.kt`) — but only for images loaded through
the shared loader; see [[list-screen-bypasses-image-loader]].

### Why don't catalogue stores show the retailer's logo?
Google Play's IP policy asks for the owner's permission to use a logo, and getting it from each retailer isn't
practical. Their press kits allow editorial use at most. The tile shows the name on the brand colour, or the user's
own front card photo. Research and sources: [[2026-11-12-no-bundled-store-logos]].
