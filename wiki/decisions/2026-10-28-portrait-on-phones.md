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
`rotates` true when the current back-stack entry is a `Detail` (`app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:78`).
Until the entry is known, `rotates` is `null` and nothing is requested: `currentBackStackEntryAsState()` starts at
`null`, and after a recreation the restored back stack arrives a frame later. Asking for portrait in that frame would
turn a card held in landscape back to portrait, which recreates the activity again (found in review;
`ScreenEffectsTest.screenOrientation_beforeTheScreenIsKnown_leavesTheOrientationAlone`).

## Why

Product call (2026-10-28): landscape brings nothing on a phone except on the card screen, where the barcode can use
the width. In portrait a tile always fits the grid, so the layout needs no landscape variant.

- **One call in `AppNavigation`, not one per screen:** during a transition both destinations are composed, so
  per-screen effects would race on enter and dispose; the current back-stack entry is one value, once restored
  (inferred design reason).
- **Phone check in the app:** Android 16 (the app targets SDK 36) ignores orientation requests on screens 600dp or
  wider, but Android 10–15 don't, so the app checks `smallestScreenWidthDp` itself. A landscape tile fits there
  (about 401dp tall in a 650dp grid on a 1280×800 tablet).
- **Not in the manifest:** a runtime request overrides the manifest's `screenOrientation`, so a manifest `portrait`
  plus this code would also work. The difference is the first launch: without it (this choice), a phone launched in
  landscape draws the list in landscape, then turns to portrait; with it, a tablet on Android 10–15 launched in
  landscape would start in portrait and turn back. Kept to one place, the code.

## Consequences

- Back from a card held in landscape rotates the list to portrait (an activity recreation) during the return
  transition; [[card-transition]] → Returning to a list the use re-sorted handles the changed screen size.
- A phone launched in landscape shows the list in landscape for a frame, then turns to portrait (one recreation).
- Split-screen ignores orientation requests, so a short, wide window on a phone is still possible there.
- "Phone" is the current **window's** smallest width (`LocalConfiguration.smallestScreenWidthDp`), not the device's.
  A small freeform window on a large screen on Android 10–15 (e.g. desktop windowing) counts as a phone and can be
  held in portrait shape. Accepted: such windows are rare for this app.
- Tests: `ScreenEffectsTest.screenOrientation_…`, `AppNavigationTest.onAPhone_onlyTheCardRotates`. Robolectric
  doesn't recreate the activity on an orientation request, so the recreation cases above are covered only through
  the `null` contract.
