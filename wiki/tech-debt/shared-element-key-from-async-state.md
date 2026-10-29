# Detail shared-element key depends on async-loaded data

**Status:** Resolved 2026-10-28 (#16) · **Issue:** [#16](https://github.com/shockrifle/loyscan/issues/16) · **Since:** 2025-12-04 (`1ea9fe0`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:133-136`

**Resolved:** `DetailScreen` takes `cardId` from the route, as it takes `color` (`MainActivity.kt:67-76`), and
keys its `Scaffold` with `cardTransitionKey(cardId)`, the helper the list tile uses too (`ListScreen.kt:322`).
The regression test, `DetailScreenTest.aTileOpeningTheCard_growsIntoTheScreen_beforeTheCardHasLoaded`, confirmed
the suspicion on Robolectric, not on a device: with the old key and a card not loaded yet, the detail screen didn't
pair with the tile and filled the screen on its first frame (411dp wide, not the tile's 80dp).

## What
Before #16, the detail `Scaffold`'s shared key was `"card/${card.id}"`, where `card` came from `DetailViewModel.card`, which is
`LoyaltyCard.EMPTY` (id `""` since #1, `0` before) until Room emits. On the first composed frames of the
destination, the key was `card/`, which doesn't match the list tile's `card/<uuid>`. The correct id was already available synchronously in the
route (`Detail.cardId`).

## Why it exists
`45a33e1` fixed the same first-frame problem for the **color** by moving it into the route
([[2025-12-12-brand-color-in-route]]) but left the key on the ViewModel.

## Risk / impact
Before #16, the transition could start unmatched and only "snap" into a shared-bounds animation once data
arrived, or mis-animate on fast taps. The size of the effect depended on how quickly Room emits; it was never
observed on a device.

## How to pay it down
(Done in #16, see Resolved above.) Pass `cardId` (from `toRoute<Detail>()`, as is already done for `color` in `MainActivity.kt:68`) into
`DetailScreen` and build the key from it. If it fixes a visible glitch, write a `bugs/` page.
