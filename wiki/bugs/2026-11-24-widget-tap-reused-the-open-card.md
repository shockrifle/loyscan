# A widget tap over another card's screen showed that card again

**Fixed in:** #213 · **Date:** 2026-11-24 · **Area:** [[card-widget]]

## Symptom
With card A's screen open, Home, then a tap on card B's widget tile: LoyScan came back still showing card A. From the
list or any other screen the tap opened the right card, and the existing warm-tap test started from the list, so
nothing caught it (#213).

## Root cause
`NavController.openCard` (`Navigation.kt`) navigated to `Detail(cardId, color)` with `launchSingleTop = true`.
Navigation matches "single top" by **destination**, not by arguments: with a `Detail` already on top, it kept that
back-stack entry and only gave it B's arguments. The entry's view model, and the `SavedStateHandle` it read its card
id from when it was created (`DetailViewModel`, `toRoute<Detail>()`), stayed card A's, so card A kept showing. A test
that read only the top entry's route saw card B and passed against the bug; only a new entry id, or the screen itself,
shows it.

## Fix
`openCard` no longer uses `launchSingleTop`. A tap for the card already on top does nothing; any other tap navigates
with `popUpTo<CardList>()`, so the card opens in a new entry over the list and Back returns to the list (the issue's
third criterion, also asked by #189). That drops a half-filled add/edit form a tap used to keep
([[2026-11-04-home-screen-widget]], amended). Tests: `WidgetTapOpensCardTest.aWarmOpenCardIntent_withAnotherCardsScreenOpen_…`
(end to end), `WidgetOpenCardDirectionsTest` (a new entry over another card, the same entry for the card on top,
Back to the list from a form).

## Lessons
- `launchSingleTop` is for "the same screen with the same content". For a route whose arguments pick the content,
  compare the arguments yourself; the entry, its view model and its saved state are reused otherwise.
- A navigation test that checks only the route's arguments can't see a reused entry: also check the entry id, or
  test through the screen.
