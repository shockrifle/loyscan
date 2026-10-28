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
`Detail(val cardId: Int, val color: String)` (`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:24-25`).
`ListDirections.toCard(id, color)` passes `card.brand.color`; `AppNavigation` extracts it with `toRoute<Detail>()`
and hands it to `DetailScreen(color, …)` (`MainActivity.kt:60-68`), which uses it for the top bar, header
background and header text contrast.

## Why
Inferred from the diff: the transition's destination must look right on frame one, and the route is the only
synchronous data source.

## Consequences
- The color is now duplicated (route and DB). If the brand color could change while the detail screen is open,
  the route value would be stale — not possible today (no edit UI).
- The route color was used unguarded until #14 ([[empty-brand-color-crash]], resolved): it is now parsed with a fallback.
- The same reasoning applied to the shared-element key, which came from the ViewModel until #16; the card id is
  now passed from the route too ([[shared-element-key-from-async-state]]).
- Pattern for future screens: anything the first frame of a transition needs goes in the route
  ([[architecture]] checklist).
