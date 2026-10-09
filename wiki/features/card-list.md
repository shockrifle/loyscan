# Card list ("My Cards")

The start destination: a top bar titled "My Cards" and a 2-column grid of card tiles, one per row in
`loyalty_cards`, live-updating from Room.

**Entry points:** `ListScreen` and `CardItem` in `app/src/main/java/com/bdaniel/loyscan/ListScreen.kt`;
`ListViewModel` (`app/src/main/java/com/bdaniel/loyscan/ListViewModel.kt:11-21`); route `List`
(`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:7-8`).

## Behavior

- Data: `ListViewModel.cards` ← `LoyaltyCardRepository.getAllWithBrand()` (join of cards + brands), initial
  value `emptyList()` — the grid is briefly empty on cold start. No ordering is applied (the SQL has no
  `ORDER BY`).
- Tile (`CardItem`): a `Card` containing a 1.5:1 box filled with the brand color (or `surfaceVariant` if the
  color string is empty or malformed, via `toBrandColorOrNull()`). Inside: the brand logo via Coil at 60% size, or the brand name in black/white
  (chosen by luminance) when the logo is blank.
- The colored box is the shared element `card/{id}` — [[card-transition]].
- Tap → `directions.toCard(card.id, card.brand.color)` → [[card-detail]].
- The Settings icon in the top bar is a no-op (`ListScreen.kt:54`, `/* TODO */`).

## Known issues

- Tile logos use Coil's default loader, not the app's — no SVG, no error logging:
  [[list-screen-bypasses-image-loader]].
