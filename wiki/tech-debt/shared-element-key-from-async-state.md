# Detail shared-element key depends on async-loaded data

**Status:** Open — **suspected, not verified on a device** · **Since:** 2025-12-04 (`9ee1947`), still present at
`669149e` · **Where:** `app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:69`

## What
The detail `Scaffold`'s shared key is `"card/${card.id}"`, where `card` comes from `DetailViewModel.card`, which is
`LoyaltyCard.EMPTY` (id `0`) until Room emits. On the first composed frame of the destination, the key is likely
`card/0`, which doesn't match the list tile's `card/<n>`. The correct id is already available synchronously in the
route (`Detail.cardId`).

## Why it exists
`669149e` fixed the same first-frame problem for the **color** by moving it into the route
([[2025-12-12-brand-color-in-route]]) but left the key on the ViewModel.

## Risk / impact
The transition may start unmatched and only "snap" into a shared-bounds animation once data arrives, or
mis-animate on fast taps. The size of the effect depends on how quickly Room emits. Observe on a slow device or
emulator before treating this as a bug.

## How to pay it down
Pass `cardId` (from `toRoute<Detail>()`, as is already done for `color` in `MainActivity.kt:51`) into
`DetailScreen` and build the key from it. If it fixes a visible glitch, write a `bugs/` page.
