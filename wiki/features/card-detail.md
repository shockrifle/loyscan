# Card detail

Shows one card: a top bar and a 200dp header in the store's brand color with its logo (or store name), then a
`Card` listing the card's label (if it has one), the store name and the display number as plain text.

**Entry points:** `DetailScreen` (`app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:38-46`);
`DetailViewModel` (`app/src/main/java/com/bdaniel/loyscan/DetailViewModel.kt:16-32`); route
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
- Back arrow → `directions.toBack()` → `navigateUp()`. The Edit icon is a no-op (`DetailScreen.kt:60`,
  `/* TODO */`).
- The whole `Scaffold` is the shared element `card/{id}` — [[card-transition]].

## Not implemented (as of 45a33e1)

- The barcode itself: `LoyaltyCard.barcodeContent` and `barcodeFormat` are derived (#1), but nothing draws them.
  No barcode library is a dependency.
- Editing, deleting and adding cards (`LoyaltyCardDao` has insert/update/delete; the repository has no write methods).

## Known issues

- [[shared-element-key-from-async-state]] — the shared key uses the ViewModel's card id, which is empty until
  Room emits.
