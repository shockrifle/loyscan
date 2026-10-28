# Card list ("My Cards")

The start destination: a top bar titled "My Cards" with a Sort menu, a search field, and a 2-column grid of card
tiles, one per row in `loyalty_cards`, live-updating from Room. Sort and search: #7.

**Entry points:** `ListScreen` and `CardItem` in `app/src/main/java/com/bdaniel/loyscan/ListScreen.kt`;
`ListViewModel` (`app/src/main/java/com/bdaniel/loyscan/ListViewModel.kt`); sort and search in
`app/src/main/java/com/bdaniel/loyscan/domain/CardSearch.kt`; route `CardList`
(`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:9-10`).

## Behavior

- Data: `ListViewModel.cards` ← `LoyaltyCardRepository.getAllWithStore()` (each card with its store), sorted by the
  stored choice, then narrowed by the query (`ListViewModel.kt:39-46`). Initial value `emptyList()`: the grid is
  briefly empty on cold start. The SQL has no `ORDER BY`; ordering is done in Kotlin (`CardSort`), so it ignores
  case and accents the way search does.
- **Sort** (the Sort icon's menu, current choice marked): *Name* (store name, then label, an unlabelled card
  first), *Most used* (`useCount`), *Recently used* (`lastUsedAt`, never-used cards last). Ties go by name, then
  id, so tiles don't swap on updates. Default *Name*. The choice is a user preference kept in DataStore and
  survives restarts ([[2026-10-28-datastore-for-user-preferences]]); a stored value this version doesn't know reads
  as *Name*. Tests: `CardSearchTest`, `ListViewModelTest`, `ListScreenTest.choosingASort_…`,
  `DataStoreUserPreferencesRepositoryTest.aChosenSort_survivesARestart`.
- **Search** (`CARD_SEARCH_TAG` field, "Search cards"): keeps the cards whose store name, store alias, label or
  note contains the query, ignoring case and accents (`searchKey()`, shared with the store picker), in the sort
  order. The number isn't searched. A query that matches nothing says "No card matches “…”". The query is kept
  in the saved state: it survives process death, not a restart. Notes can't be edited yet (#9), but a stored
  note is searched.
- Grid items are keyed by card id, so a re-sort moves tiles rather than rebinding them.
- Tile (`CardItem`): a `Card` containing a 1.5:1 box filled with the brand color (or `surfaceVariant` if the
  color string is empty or malformed, via `toBrandColorOrNull()`). Inside: the store logo via Coil at 60% size, or the store name in black/white
  (chosen by luminance) when the logo is `null` or blank.
- The colored box is the shared element `card/{id}` — [[card-transition]].
- Tap → `directions.toCard(card.id, card.store.color)` → [[card-detail]].
- The Settings icon in the top bar is a no-op (`ListScreen.kt:71`, `/* TODO */`).
- The "Add card" floating button → `directions.toAddCard()` → the store picker of [[add-card]]. The grid has 88dp of bottom content
  padding so the button never covers the last row (`ListScreenTest.theLastTile_isNotCoveredByTheAddButtonWhenScrolledToTheEnd`).

## Known issues

- Tile logos use Coil's default loader, not the app's — no SVG, no error logging:
  [[list-screen-bypasses-image-loader]]. No store has a logo today, so no tile loads one; it matters once tiles show
  card photos (#7, #10).
