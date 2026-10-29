# A save that ended after Back still opened the card

**Fixed in:** #64; the same bug on the custom store and card screens in #65, and on the other directions in #66, #68, #69 · **Date:** 2026-10-28 · **Area:** [[add-card]], [[edit-card]]

## Symptom
If Back was pressed on the card form while its save was still running, the form closed. When the save then ended,
the saved card opened over the list, so the user landed on the card they had backed out of. Found while debugging a
CI timeout in `AppNavigationTest` (#64; the test was fixed first by waiting for the form to close, `4001534`).

## Root cause
There were two causes:
- **The effect outlives the pop.** `CardFormScreen` opens the card from `LaunchedEffect(state.savedCardId)`. A popped
  `NavHost` entry stays composed while it animates out, so the effect still ran when `savedCardId` was set. It then
  called `CardFormDirections.toCard`, which navigated (`popUpTo<CardList>()`) from whatever screen Back had reached.
- **`viewModelScope` dies with the entry.** Once the exit animation ends, the entry is destroyed and its ViewModel is
  cleared. A Room write still waiting to run was then cancelled, so a later Back could lose the card.

## Fix
- `cardFormDirections(entry).toCard` navigates only while the form's own back-stack entry is current. Checking for any
  `CardForm` wasn't enough: Edit again on the card while the first form animates out opens a second form, which the
  first save would replace (found in review). The check is on the
  back stack, not on the entry's lifecycle (`dropUnlessResumed`): a save that ends while the app is in the background
  must still open the card.
- `CardFormViewModel.save` writes in `withContext(NonCancellable)`.
- Tests: `AddCardDirectionsTest.aSaveThatEndsAfterBackFromTheForm_…`, `anEditThatEndsAfterBackFromTheForm_…` and
  `aSaveThatEndsAfterBack_leavesAFormOpenedSinceAsItIs`;
  `CardFormViewModelTest.aSaveStillRunningWhenTheFormCloses_isStillSaved` and `anEditStillSavingWhenTheFormCloses_…`
  (a write held by `FakeLoyaltyCardRepository.addGate` / `updateGate`, then the ViewModel cleared).

## Lessons
- Navigating from a `LaunchedEffect` on ViewModel state can fire after the user has left the screen. The directions
  it calls must check that their screen is still the current one.
- A write the user has asked for must not run only in `viewModelScope`.
- The same two patterns were in `CustomStoreScreen` (create → scanner, save or delete → back) and `DetailScreen`
  (delete → back). Fixed the same way in [#65](https://github.com/shockrifle/loyscan/issues/65): every direction
  called when a write ends goes through `whileCurrent(entry)` in `Navigation.kt`, and the writes run
  `NonCancellable` ([[edit-store]], [[card-detail]]).
- A popped screen also takes taps while it animates out, so a second tap on its Back arrow popped the screen below
  too. Since [#66](https://github.com/shockrifle/loyscan/issues/66) every `toBack` goes through `whileCurrent(entry)`
  (`AddCardDirectionsTest.aSecondBackFrom…`). The list had a `toBack` that nothing called; it was removed.
- The same holds for forward directions: a screen takes taps until the next one covers it, so a double tap on a store,
  a card, Add or Edit pushed the next screen twice, and a camera frame decoded after Back from the scanner opened the
  card form over the picker. Since [#68](https://github.com/shockrifle/loyscan/issues/68) and
  [#69](https://github.com/shockrifle/loyscan/issues/69) every direction goes through `whileCurrent(entry)`, and
  `listDirections` takes the list's entry too (`AddCardDirectionsTest.aSecondTapOn…`, `…AfterBackFromTheScanner_…`).
  A new direction does the same.
