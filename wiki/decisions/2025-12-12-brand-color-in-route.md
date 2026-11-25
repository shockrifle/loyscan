# Pass the brand color through the `Detail` route

**Status:** Active · **Date:** 2025-12-12 · **Commits:** `45a33e1`

## Context
`DetailScreen` derived its header/top-bar color from `DetailViewModel.card`, which is `LoyaltyCard.EMPTY` until
Room emits. During the list → detail shared-element transition, the first frames therefore had no color — see
[[2025-12-12-detail-color-flash-on-transition]].

## Options considered
- Keep the color in the ViewModel and accept the flash.
- Pre-load the card synchronously (blocking the main thread — not acceptable with Room).
- Pass what the first frame needs as a navigation argument.

## Decision
`Detail(val cardId: String, val color: String, …)` (in `app/src/main/java/com/bdaniel/loyscan/Navigation.kt`).
`ListDirections.toCard(id, color)` passes `card.store.color`; `AppNavigation` extracts it with `toRoute<Detail>()`
and hands it to `DetailScreen(cardId, color, …)` (`AppNavigation` in `app/src/main/java/com/bdaniel/loyscan/MainActivity.kt`), which uses it for the band's background and
the contrast of its initials and icons until the card has loaded (the top bar and header until #210, [[2026-11-24-detail-header-band]]); from then on the screen draws the loaded card's
`store.color` (since #226; until then it drew the route's colour throughout). (Corrected 2026-10-28: this paragraph still named the 2025-12-12 `Int` id,
`card.brand` and a line number in `Navigation.kt`.)

## Why
Inferred from the diff: the transition's destination must look right on frame one, and the route is the only
synchronous data source.

## Consequences
- The color is duplicated (route and DB), so the route's value goes stale when a custom store's colour is edited
  while its card is open on the back stack (card → "Add another" → picker → Edit store, #53). The screen therefore
  draws the route's colour only until the card has loaded and the stored `store.color` after that, so an edit shows
  on return (#226). History: written as "not possible today (no edit UI)"; found reachable in the 2026-11-25 wiki
  sweep; fixed and this bullet rewritten 2026-11-25 (#226).
- The route color was used unguarded until #14 ([[empty-brand-color-crash]], resolved): it is now parsed with a fallback.
- The same reasoning applied to the shared-element key, which came from the ViewModel until #16; the card id is
  now passed from the route too ([[shared-element-key-from-async-state]]).
- Pattern for future screens: anything the first frame of a transition needs goes in the route
  ([[architecture]] checklist).
