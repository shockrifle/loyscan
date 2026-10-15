# Card detail

Shows one card at the checkout: a top bar and a 200dp header in the store's brand color with its logo (or store
name), then a `Card` with the card's label, the store name, the barcode on white and the display number in large
type. The screen runs at full brightness; a double-tap on the barcode opens a full-screen scan mode.

**Entry points:** `DetailScreen` (`app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:67-75`);
`DetailViewModel` (`app/src/main/java/com/bdaniel/loyscan/DetailViewModel.kt:17-38`); route
`Detail(cardId, color)` (`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:24-25`), where `cardId` is the card's
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
- Header text color: white if the brand color's luminance is < 0.5, else black.
- The number shown is `LoyaltyCard.displayNumber`: the number formatted by the store's card-number rule (for a
  scanned card, the core the rule recovers from the scan), or the raw number when there is no rule or no match
  ([[2026-10-12-data-model-v2]]).
- **Opening a card counts a use:** `DetailViewModel`'s `init` calls `LoyaltyCardRepository.recordUse(cardId)`, which
  adds 1 to `useCount` and sets `lastUsedAt` to now in one `UPDATE` (`LoyaltyCardDao.recordUse`). Once per
  ViewModel, so a rotation doesn't count again (a restore after process death does). `updatedAt` is left alone: a
  use isn't an edit of the card. These fields feed the list's "most / recently used" sort (#7).
- **Barcode:** below the label and store name, on a white rounded card with 16dp padding in both themes; the barcode
  carries its own quiet zone ([[zxing-core]]). `BarcodeEncoder` encodes `barcodeContent` in `barcodeFormat`, falling back to `CODE_128`, then QR
  ([[zxing-core]]); nothing is drawn for empty content. 1D barcodes are 120dp high across the card; 2D ones keep
  their aspect ratio at full width. Its content description is "Barcode".
- **Number:** `displayNumber` in `headlineMedium` below the barcode. A tap copies it to the clipboard exactly as
  shown, rule spacing included (product choice, 2026-10-15). Android 13+ confirms the copy itself; on 10–12 a toast
  says "Number copied".
- The card's content scrolls vertically when it doesn't fit.
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
- Back arrow → `directions.toBack()` → `navigateUp()`. The Edit icon is a no-op (`DetailScreen.kt:96`,
  `/* TODO */`).
- The whole `Scaffold` is the shared element `card/{id}` — [[card-transition]].

## Not implemented (as of 2026-10-15)

- Editing, deleting and adding cards (`LoyaltyCardDao` has insert/update/delete; the repository's only write is `recordUse`).

## Known issues

- [[shared-element-key-from-async-state]] — the shared key uses the ViewModel's card id, which is empty until
  Room emits.
