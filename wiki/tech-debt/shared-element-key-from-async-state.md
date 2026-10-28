# Detail shared-element key depends on async-loaded data

**Status:** Resolved 2026-10-28 (#16) · **Issue:** [#16](https://github.com/shockrifle/loyscan/issues/16) · **Since:** 2025-12-04 (`1ea9fe0`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:117-120`

**Resolved:** `DetailScreen` takes `cardId` from the route, as it takes `color` (`MainActivity.kt:60-68`), and
keys its `Scaffold` with `cardTransitionKey(cardId)`, the helper the list tile uses too (`ListScreen.kt:88-89`).
The regression test, `DetailScreenTest.aTileOpeningTheCard_growsIntoTheScreen_beforeTheCardHasLoaded`, confirmed
the suspicion on Robolectric, not on a device: with the old key and a card not loaded yet, the detail screen didn't
pair with the tile and filled the screen on its first frame (411dp wide, not the tile's 80dp).

## What
The detail `Scaffold`'s shared key is `"card/${card.id}"`, where `card` comes from `DetailViewModel.card`, which is
`LoyaltyCard.EMPTY` (id `""` since #1, `0` before) until Room emits. On the first composed frame of the
destination, the key is likely `card/`, which doesn't match the list tile's `card/<uuid>`. The correct id is already available synchronously in the
route (`Detail.cardId`).

## Why it exists
`45a33e1` fixed the same first-frame problem for the **color** by moving it into the route
([[2025-12-12-brand-color-in-route]]) but left the key on the ViewModel.

## Risk / impact
The transition may start unmatched and only "snap" into a shared-bounds animation once data arrives, or
mis-animate on fast taps. The size of the effect depends on how quickly Room emits. Observe on a slow device or
emulator before treating this as a bug.

## How to pay it down
Pass `cardId` (from `toRoute<Detail>()`, as is already done for `color` in `MainActivity.kt:61`) into
`DetailScreen` and build the key from it. If it fixes a visible glitch, write a `bugs/` page.
