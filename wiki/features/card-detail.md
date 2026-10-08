# Card detail

Shows one card: a top bar and a 200dp header in the brand color with the brand logo (or brand name), then a
`Card` listing the card name, brand name and card code as plain text.

**Entry points:** `DetailScreen` (`app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:38-46`);
`DetailViewModel` (`app/src/main/java/com/bdaniel/loyscan/DetailViewModel.kt:16-32`); route
`Detail(cardId, color)` (`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:24-25`).

## Behavior

- **Two data sources, on purpose:** the brand `color` comes from the navigation route (available on the first
  frame); everything else comes from `DetailViewModel.card`, which starts as `LoyaltyCard.EMPTY` and fills in
  when Room emits. Why: [[2025-12-12-brand-color-in-route]].
- The logo uses `hiltImageLoader()` (SVG-capable, logs failures) — [[coil-image-loading]].
- Header text color: white if the brand color's luminance is < 0.5, else black.
- Back arrow → `directions.toBack()` → `navigateUp()`. The Edit icon is a no-op (`DetailScreen.kt:59`,
  `/* TODO */`).
- The whole `Scaffold` is the shared element `card/{id}` — [[card-transition]].

## Not implemented (as of 45a33e1)

- The barcode itself: `barcodeFormat` and `code` are stored, but only `code` is shown, as text. No barcode
  library is a dependency.
- Editing, deleting and adding cards (repository write methods exist but have no callers).

## Known issues

- [[empty-brand-color-crash]] — `Color(color.toColorInt())` throws on an empty string.
- [[shared-element-key-from-async-state]] — the shared key uses the ViewModel's card id, which is `0` until
  Room emits.
