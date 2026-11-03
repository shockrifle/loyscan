# List → detail shared-element transition

Tapping a tile morphs the tile's colored box into the whole detail screen (and back on return), using
Compose's experimental shared-element API.

**Entry points:** `SharedTransitionLayout` wrapping `AppNavigation`
(`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:56-63`); the `CardItem` box's
`.sharedElement(key = cardTransitionKey(card.id))` in `ListScreen.kt`; `Scaffold(modifier = Modifier.sharedElement(…))`
in `app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:152-156`.

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
(`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:56-63`).

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

## Returning to a list the use re-sorted (#61)

Opening a card counts a use ([[card-detail]]), so under *Most used* or *Recently used* its tile can move while the
card is open, often off screen: the grid keeps its first visible tile in place across a reorder, and the detail
screen then shrank into nothing. The reorder can also arrive *during* the return: after 5 s on the card, `ListViewModel`'s
`WhileSubscribed(5_000)` has stopped reading Room, so the list first shows the old order and re-sorts a few frames later.

`ListScreen` therefore remembers the tapped tile (`OpenedTile`: card id and its offset from the top of the grid, saved
state, so it survives process death too), only when the tap opened the card: `ListDirections.toCard` says whether it
did, and a second tile tapped while the list leaves opens nothing (#69). Until the list's return transition settles, it
calls `LazyGridState.requestScrollToItem(newIndex, -offset)` on every new order, in a `SideEffect`, which applies before that frame's measure
(`app/src/main/java/com/bdaniel/loyscan/ListScreen.kt:100-118`). The tile comes back where it was tapped, or as near as
the list allows (at the top when it moved there), and the card shrinks into it. After that, reorders scroll as usual.
The offset is in pixels, so it is kept only on the screen it was measured on (window size, density, font scale): after
a rotation or a display size change the tile's top is put at the top of the grid instead, so the tile is in view. On a
phone this happens when Back from a card held in landscape turns the list to portrait
([[2026-10-28-portrait-on-phones]]); there a tile always fits the grid.
Tests: `ListScreenTest.returningFromACard_…` (moved up, new order during the return, moved to the top, after process
death, on a screen that changed size), `aSecondTileTappedWhileTheListLeaves_isNotTheOneHeldOnReturn` and
`onceBackOnTheList_aLaterReorder_doesNotHoldTheOpenedTileInPlace`; the list there sits in an
`AnimatedContent` with a `SaveableStateHolder`, as in the `NavHost`.

Not chosen (2026-10-28): freezing the order until the user is back shows a stale order and then
moves tiles under the user; skipping the shared element when the tile is off screen still loses the tile from view.

## Caveats

None open. The detail side's key depended on async-loaded data until #16
([[shared-element-key-from-async-state]], resolved).
