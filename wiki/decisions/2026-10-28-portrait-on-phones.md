# Phones keep every screen but the card in portrait

**Status:** Active · **Date:** 2026-10-28 · **Commits:** #63

## Context

The card list's tiles fill half the grid width at the ID-1 ratio. In phone landscape (e.g. 915×412dp) a tile is about
286dp tall, but the grid below the top bar and the search field is about 250dp, so no tile fit on screen whole, and
going back from a card after a rotation ([[card-transition]], #61) left the tile's bottom cut off (#63).

## Options considered

- More columns in landscape (by width, or just enough for a tile to fit the grid's height).
- Two columns with the tile height capped (narrower tiles, empty side margins).
- **Portrait on phones, except the card screen** (chosen).

## Decision

On a phone (smallest width under 600dp) every screen but the card ([[card-detail]]) is held in portrait;
the card screen follows the device's rotation. Screens 600dp or wider (tablets, unfolded foldables) are never locked.
`ScreenOrientation(rotates)` in `app/src/main/java/com/bdaniel/loyscan/ui/ScreenEffects.kt` sets the activity's
`requestedOrientation` (`SCREEN_ORIENTATION_PORTRAIT` or `UNSPECIFIED`); `AppNavigation` calls it once, with
`rotates` true when the current back-stack entry is a `Detail` (`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:55`).

## Why

Product call (2026-10-28): landscape brings nothing on a phone except on the card screen, where the barcode can use
the width. In portrait a tile always fits the grid, so the layout needs no landscape variant.

- **One call in `AppNavigation`, not one per screen:** during a transition both destinations are composed, so
  per-screen effects would race on enter and dispose; the current back-stack entry is one value (inferred design reason).
- **Phone check in the app:** Android 16 (the app targets SDK 36) ignores orientation requests on screens 600dp or
  wider, but Android 10–15 don't, so the app checks `smallestScreenWidthDp` itself. A landscape tile fits there
  (about 401dp tall in a 650dp grid on a 1280×800 tablet).
- **Not in the manifest:** there is one activity, and `screenOrientation` there would lock the card screen too, and
  tablets on Android 10–15.

## Consequences

- Back from a card held in landscape rotates the list to portrait (an activity recreation) during the return
  transition; [[card-transition]] → Returning to a list the use re-sorted handles the changed screen size.
- Split-screen ignores orientation requests, so a short, wide window on a phone is still possible there.
- Tests: `ScreenEffectsTest.screenOrientation_…`, `AppNavigationTest.onAPhone_onlyTheCardRotates`.
