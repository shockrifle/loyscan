# List → detail shared-element transition

Tapping a tile morphs the tile's colored box into the whole detail screen (and back on return), using
Compose's experimental shared-element API.

**Entry points:** `SharedTransitionLayout` wrapping `AppNavigation`
(`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:32-39`); the `CardItem` box's
`.sharedElement(key = cardTransitionKey(card.id))` in `ListScreen.kt`; `Scaffold(modifier = Modifier.sharedElement(…))`
in `app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:116-119`.

## How it's wired

- `AppNavigation`, `ListScreen`, `CardItem` and `DetailScreen` are all extensions on `SharedTransitionScope`;
  each `composable<Route> { }` block passes `this` as the `AnimatedVisibilityScope`.
- Key format: `card/{id}`, built by `cardTransitionKey(id)` in `ListScreen.kt` for both sides (was `item/{id}`
  until `45a33e1`).
- The detail screen takes its color and its card id from the route, so the destination is correctly colored and
  paired with the tile from the first frame, before Room emits ([[2025-12-12-brand-color-in-route]],
  [[2025-12-12-detail-color-flash-on-transition]], [[shared-element-key-from-async-state]], #16).
- Test: `DetailScreenTest.aTileOpeningTheCard_growsIntoTheScreen_beforeTheCardHasLoaded`. A matched shared element
  is first measured at the other side's bounds, an unmatched one at its own size, so the test checks the detail
  content's first measured width. Under a paused `mainClock`, the state change that starts the transition needs
  `runOnIdle { … }` and `waitForIdle()`, or the content never recomposes.

## History

- `1ea9fe0` (2025-12-04) — first implementation: shared element on the list tile and on the detail `Card`.
- `45a33e1` (2025-12-12) — "transition fixes": the shared element moved to the tile's colored box ↔ the
  detail `Scaffold`, the key was renamed to `card/{id}`, and the brand color was passed through the route.
- 2026-10-08 — transparent screens + one app-level background (see below).

## Background: transparent Scaffolds, one Surface

Both `ListScreen` and `DetailScreen` pass `containerColor = Color.Transparent` to their `Scaffold`, and
`MainActivity` wraps `SharedTransitionLayout` in a full-size `Surface(color = MaterialTheme.colorScheme.background)`
(`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:32-39`).

- **Why transparent:** the detail `Scaffold` *is* the shared element; with an opaque container it morphed out of
  the colored tile as a plain surface-colored rectangle. With the NavHost's default cross-fade, two opaque screen
  backgrounds also fade against each other. Transparent screens leave only content in the transition. The
  screen edits were made around 2025-12-12 and committed 2026-10-08; their purpose was reconstructed afterwards,
  not recorded at the time.
- **Why the Surface:** behind transparent Scaffolds is the window background, and `Theme.LoyScan` inherits
  `android:Theme.Material.Light.NoActionBar` with no `values-night` override, so dark mode would show a light
  window under light dynamic-dark text. The single theme-aware `Surface` fixes that.
- **Rule for new screens:** keep `Scaffold(containerColor = Color.Transparent)`. Don't add per-screen opaque
  backgrounds.

## Caveats

None open. The detail side's key depended on async-loaded data until #16
([[shared-element-key-from-async-state]], resolved).
