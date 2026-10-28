# Card detail

Shows one card at the checkout: a top bar and a 200dp header in the store's brand color with its logo (or store
name), then a `Card` with the card's label, the store name, the barcode on white and the display number in large
type. The screen runs at full brightness; a double-tap on the barcode opens a full-screen scan mode.

**Entry points:** `DetailScreen` (`app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:69-78`);
`DetailViewModel` (`app/src/main/java/com/bdaniel/loyscan/DetailViewModel.kt`); route
`Detail(cardId, color, countUse)` (`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:30-31`), where `cardId` is the card's
UUID string.

## Behavior

- **Two data sources, on purpose:** the brand `color` comes from the navigation route (available on the first
  frame); everything else comes from `DetailViewModel.card`, which starts as `LoyaltyCard.EMPTY` and fills in
  when Room emits. Why: [[2025-12-12-brand-color-in-route]].
- If no card has the route's id (deleted, or a stale route), the screen stays on `LoyaltyCard.EMPTY`, or on the
  last card it showed. It doesn't crash: [[2026-10-09-missing-card-throws-from-room]].
- The logo uses `hiltImageLoader()` (SVG-capable, logs failures) — [[coil-image-loading]].
- The brand colour is parsed with `toBrandColorOrNull()`. An empty or malformed colour falls back to `surfaceVariant`
  instead of crashing ([[empty-brand-color-crash]], resolved).
- Header text color: black or white, whichever contrasts more with the brand color (`contrastingTextColor()`, the
  tile's rule, [[card-list]]). Until 2026-10-28 it was white below a luminance of 0.5, which put white on mid tones
  where black reads better.
- The number shown is `LoyaltyCard.displayNumber`: the number formatted by the store's card-number rule (for a
  scanned card, the core the rule recovers from the scan), or the raw number when there is no rule or no match
  ([[2026-10-12-data-model-v2]]). A GS1 scan (`BarcodeFormat.isGs1`: the `GS1_` formats, DataBar) with no matching rule is shown as printed under
  GS1 barcodes, `(01)09501101020917(10)12AB`; any other scanned content shows each run of control characters as a
  space ([[2026-10-21-gs1-scans-shown-as-hri]], #42).
- **Opening a card counts a use** (except right after adding it, `Detail.countUse = false`, [[add-card]]): `DetailViewModel`'s `init` calls `LoyaltyCardRepository.recordUse(cardId)`, which
  adds 1 to `useCount` and sets `lastUsedAt` to now in one `UPDATE` (`LoyaltyCardDao.recordUse`). Once per
  ViewModel, so a rotation doesn't count again (a restore after process death does). `updatedAt` is left alone: a
  use isn't an edit of the card. These fields feed the list's "most / recently used" sort (#7).
- **Barcode:** below the label and store name, on a white rounded card with 16dp padding in both themes; the barcode
  carries its own quiet zone ([[zxing-core]]). `BarcodeEncoder` encodes `barcodeContent` in `barcodeFormat`, falling back to `CODE_128`, then QR (GS1 data: `GS1_128`, then GS1 QR)
  ([[zxing-core]]); nothing is drawn for empty content. 1D barcodes are 120dp high across the card; 2D ones keep
  their aspect ratio at full width. Its content description is "Barcode".
- **Fallback note:** when the barcode is drawn in a format other than the card's `barcodeFormat`, a `bodySmall` line
  under it names the format drawn, e.g. a DataBar card shows "Shown as GS1-128" (#35, #45). The name is
  `BarcodeFormat.displayName`. A card drawn in its own format shows no note. Tests:
  `DetailScreenTest.cardDrawnInAFallbackFormat_namesTheFormatDrawn`, `contentNotEvenCode128CanHold_namesQrAsTheFormatDrawn`,
  `cardDrawnInItsOwnFormat_showsNoFormatNote`.
- **Scan mode shows no fallback note** (product choice, 2026-10-16, #37): it stays barcode and number only. The note
  is for the user, who has just seen it on the detail card they opened scan mode from. Whether tills accept a
  fallback barcode is unknown ([[zxing-core]]); if one rejects it, the cashier keys in the number shown below the
  barcode. The space goes to the barcode. Test:
  `DetailScreenTest.scanMode_forAFallbackCard_showsNoFormatNote`.
- **Number:** `displayNumber` in `headlineMedium` below the barcode. A tap copies it to the clipboard exactly as
  shown, rule spacing included (product choice, 2026-10-15). Android 13+ confirms the copy itself; on 10–12 a toast
  says "Number copied".
- The card's content scrolls vertically when it doesn't fit.
- **Rotation:** the only screen that turns with a phone; every other screen is held in portrait on a phone
  ([[2026-10-28-portrait-on-phones]], `AppNavigationTest.onAPhone_onlyTheCardRotates`).
- **Full brightness:** `FullBrightness()` (`app/src/main/java/com/bdaniel/loyscan/ui/ScreenEffects.kt`) sets the
  window's `screenBrightness` to `BRIGHTNESS_OVERRIDE_FULL` while the detail destination is resumed and puts the
  window's previous value back on pause (home, app switch) and when the screen leaves composition (back). It follows
  the `NavBackStackEntry`'s lifecycle, so it only brightens once the enter transition has finished. Tests:
  `ScreenEffectsTest`, `DetailScreenTest`, `AppNavigationTest.detailsFullBrightness_isRestoredOnBack`.
- **Scan mode:** a double-tap on the barcode (or the accessibility action "Show full screen") lays `ScanMode` over
  the whole screen, status bar included: white, the barcode as large as fits inside 24dp padding with the number
  below it in black (1D: full width, 200dp high; 2D: square modules, height-bound in landscape so the number keeps
  its room). It keeps the screen on (`KeepScreenOn()`, `FLAG_KEEP_SCREEN_ON`, same
  resumed-only rule as the brightness). A tap anywhere or back closes it. Its open state survives rotation
  (`rememberSaveable`). 1D barcodes are not rotated to the long side.
- Back arrow → `directions.toBack()` → `navigateUp()`. The Edit icon opens the card form for this card
  (`directions.toEdit(cardId, storeId)`, [[edit-card]]); it is disabled until the card has loaded.
- **Delete** (#8): the top bar's Delete icon asks "Delete this card?" ("It is removed from this phone for good.").
  "Cancel" or a tap outside closes the dialog. "Delete" calls `DetailViewModel.delete()` →
  `LoyaltyCardRepository.delete(id)` (`LoyaltyCardDao.deleteById`, one `DELETE`), and once it is gone
  (`Deletion.DELETED`; the enum in `Deletion.kt` is shared with deleting a custom store, [[edit-store]]) the screen goes back (`directions.toBack()`, to the list). Until then the card stays on screen:
  the card flow skips the `null` a deleted card emits. A failed delete (the repository throws) is logged with Timber,
  and the dialog says "The card couldn't be deleted. Try again."; "Delete" tries again (as a failed save does, #49).
  Closing the dialog forgets the failure (`dismissDeletion`), and a delete already under way or done isn't repeated.
  Tests: `DetailViewModelTest.delete_…`, `DetailScreenTest.deleteAction_…`, `confirmingDelete_…`,
  `aDeleteThatFails_…`, `AppNavigationTest.deletingACard_afterConfirming_returnsToTheListWithoutIt`.
- The whole `Scaffold` is the shared element `card/{id}` — [[card-transition]].

## Not implemented (as of 2026-10-22)

- Picking another store for a card (#8, [[edit-card]]). Deleting several cards at once waits for the list's multi-select (#7),
  and deleting a card's photo files for photos (#10); nothing stores photos yet.

## Known issues

- [[shared-element-key-from-async-state]] (resolved, #16) — the shared key used the ViewModel's card id, which is
  empty until Room emits; it now comes from the route.
