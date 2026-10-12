# FAQ

Questions that took real effort to answer once. Newest at the bottom; keep answers short and sourced.

### Why can't I write `List<Foo>` in the `com.bdaniel.loyscan` package?
Because the navigation route `object List` (`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:7-8`)
shadows `kotlin.collections.List` in that package. Use type inference, `kotlin.collections.List<Foo>`, or
move code into a sub-package. See [[list-route-shadows-kotlin-list]].

### I changed `loyscan.sql` but the app still shows the old cards.
The seed only runs in `RoomDatabase.Callback.onCreate`, i.e. when the DB file is first created. Clear app
data — see [[reset-local-database]] and [[seed-data]].

### I added a field to an entity and the app crashes on launch.
The DB is at `version = 2` with no migrations (`AppDatabase.kt:11`). Bump the version and either add a
`Migration` or wipe the DB. See [[database-schema-changes]] and [[no-room-migrations]].

### Why does the detail screen get the brand color as a navigation argument when the ViewModel loads the card anyway?
So the color is available on the very first frame of the shared-element transition, before Room has
emitted. See [[2025-12-12-brand-color-in-route]] and [[2025-12-12-detail-color-flash-on-transition]].

### Why are icons hand-written `ImageVector`s instead of `Icons.Default.*` from Material?
The Material icons stopped resolving after the Compose BOM upgrade. See [[2025-12-08-hand-rolled-icons]].

### Where do logs go?
Timber, with a `DebugTree` planted only in debug builds (`LoyScanApplication.kt:11-13`). Release builds log
nothing. Image-load failures are logged at WARN with the message prefix `Image loading failed for:`
(`app/src/main/java/com/bdaniel/loyscan/di/ImageLoaderModule.kt:31`) — but only for images loaded through
the shared loader; see [[list-screen-bypasses-image-loader]].
