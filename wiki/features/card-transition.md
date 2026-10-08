# List → detail shared-element transition

Tapping a tile morphs the tile's colored box into the whole detail screen (and back on return), using
Compose's experimental shared-element API.

**Entry points:** `SharedTransitionLayout` wrapping `AppNavigation`
(`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:27-29`); the `CardItem` box's
`.sharedElement(key = "card/${card.id}")` in `ListScreen.kt`; `Scaffold(modifier = Modifier.sharedElement(…))`
in `app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:68-71`.

## How it's wired

- `AppNavigation`, `ListScreen`, `CardItem` and `DetailScreen` are all extensions on `SharedTransitionScope`;
  each `composable<Route> { }` block passes `this` as the `AnimatedVisibilityScope`.
- Key format: `card/{id}` (was `item/{id}` until `669149e`).
- The detail screen takes its color from the route, so the destination is correctly colored from the first
  frame ([[2025-12-12-brand-color-in-route]], [[2025-12-12-detail-color-flash-on-transition]]).

## History

- `9ee1947` (2025-12-04) — first implementation: shared element on the list tile and on the detail `Card`.
- `669149e` (2025-12-12) — "transition fixes": the shared element moved to the tile's colored box ↔ the
  detail `Scaffold`, the key was renamed to `card/{id}`, and the brand color was passed through the route.
- Uncommitted on 2026-10-08: both screens' `Scaffold`s get `containerColor = Color.Transparent`. This is not
  in history yet; update this page when it lands.

## Caveats

- [[shared-element-key-from-async-state]] — the detail side's key depends on async-loaded data. Suspected, not
  verified on a device.
