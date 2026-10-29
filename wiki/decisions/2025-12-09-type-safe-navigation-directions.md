# Type-safe routes + per-screen Directions objects

**Status:** Active · **Date:** 2025-12-09 · **Commits:** `a33481a` (refactor), `45a33e1` (`color` added to `Detail`)

## Context
Navigation started with string routes (`"list"`, `"detail/{cardId}"`) and lambdas like `onItemClick: (Int) -> Unit`
passed into screens, with `DetailViewModel` reading `"cardId"` from `SavedStateHandle` by string key.

## Options considered
- Keep string routes + callback lambdas per screen.
- Navigation Compose's type-safe API (`@Serializable` routes, `composable<T>`, `toRoute<T>()`), available since
  navigation 2.8.

## Decision
- Routes are `@Serializable` types in `app/src/main/java/com/bdaniel/loyscan/Navigation.kt`: `object List`,
  `data class Detail(val cardId: Int, val color: String)`.
- Each screen gets an `@Immutable` **Directions** data class of lambdas (`ListDirections`, `DetailDirections`),
  built by a `NavController` extension (`listDirections()`, `detailDirections()`). Screens never see the
  `NavController`.
- ViewModels read args with `savedStateHandle.toRoute<Detail>()` (`DetailViewModel.kt:22`).
- Requires the `kotlin-serialization` plugin (added in the same commit) and navigation-compose 2.9.6.

## Why
Not recorded (inferred): compile-time-checked arguments instead of string keys and manual `toIntOrNull()`, and
one object per screen instead of a growing list of callback params. `@Immutable` keeps the lambda holder stable
for Compose skipping.

## Consequences
- A new screen = route type + Directions class + factory + `composable<Route>` entry — see the checklist in
  [[architecture]].
- `ListDirections.toBack` was never used (the list is the start destination); removed in #66 (corrected 2026-10-29).
- Naming the route `List` shadowed `kotlin.collections.List` — [[list-route-shadows-kotlin-list]]. Renamed to
  `CardList` in #17.
