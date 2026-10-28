# Card list ("My Cards")

The start destination: a top bar titled "My Cards" with a Sort menu, a search field, and a 2-column grid of card
tiles, one per row in `loyalty_cards`, live-updating from Room. Sort and search: #7.

**Entry points:** `ListScreen` and `CardItem` in `app/src/main/java/com/bdaniel/loyscan/ListScreen.kt`;
`ListViewModel` (`app/src/main/java/com/bdaniel/loyscan/ListViewModel.kt`); sort and search in
`app/src/main/java/com/bdaniel/loyscan/domain/CardSearch.kt`; route `CardList`
(`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:9-10`).

## Behavior

- Data: `ListViewModel.cards` ← `LoyaltyCardRepository.getAllWithStore()` (each card with its store), sorted by the
  stored choice, then narrowed by the query (`ListViewModel.kt:42-55`). Initial value `null` (not loaded): the grid is
  briefly empty on cold start, and "No card matches" waits for the load, since a query restored after process death
  is there at once (`ListScreenTest.aRestoredSearch_doesNotSayNothingMatches_beforeTheCardsHaveLoaded`). The SQL has no `ORDER BY`; ordering is done in Kotlin (`CardSort`), so it ignores
  case and accents the way search does.
- **Sort** (the Sort icon's menu, current choice marked): *Name* (store name, then label, an unlabelled card
  first), *Most used* (`useCount`), *Recently used* (`lastUsedAt`, never-used cards last). Ties go by name, then
  id, so tiles don't swap on updates. Default *Name*. The choice is a user preference kept in DataStore and
  survives restarts ([[2026-10-28-datastore-for-user-preferences]]); a stored value this version doesn't know, or a file that can't be
  read, reads as *Name*. A sort that can't be saved isn't applied (logged). Tests: `CardSearchTest`, `ListViewModelTest`, `ListScreenTest.choosingASort_…`,
  `DataStoreUserPreferencesRepositoryTest.aChosenSort_survivesARestart`.
- **Search** (`CARD_SEARCH_TAG` field, "Search cards"): keeps the cards whose store name, store alias, label or
  note contains the query, ignoring case and accents (`searchKey()`, shared with the store picker), in the sort
  order. The number isn't searched. A query that matches nothing says "No card matches “…”". The query is kept
  in the saved state: it survives process death, not a restart, and is cleared when no card is left. Notes can't be edited yet (#9), but a stored
  note is searched.
- **Empty state** (`EmptyCardList`): once the cards have loaded, with none stored and no search, the screen shows "No
  cards yet" and an "Add your first card" button (→ `directions.toAddCard()`, like the floating button) in place of the
  search field and grid. It waits for the load (`cards` is `null` until then), and a search that matches nothing keeps
  the field and says "No card matches" instead. When the last card is deleted, the ViewModel clears the query
  (`ListViewModel.cards`), so a search left from before shows neither "No card matches" over nothing nor hides the
  next card added (`ListViewModelTest.deletingTheLastCard_clearsTheQuery`,
  `ListScreenTest.deletingTheLastCard_duringASearch_showsTheEmptyState`). Tests: `ListScreenTest.noCards_…`, `theEmptyState_waitsForTheCardsToLoad`,
  `aSearchThatMatchesNothing_isNotTheEmptyState`.
- On a phone the list is held in portrait, where a tile always fits the grid; screens 600dp or wider rotate
  ([[2026-10-28-portrait-on-phones]], #63).
- Grid items are keyed by card id, so a re-sort moves tiles rather than rebinding them. A use counted on opening a
  card can re-sort the list; on the way back, the opened card's tile is put back where it was tapped
  ([[card-transition]] → Returning to a list the use re-sorted, #61).
- Tile (`CardItem`): a `Card` containing a box in bank-card (ID-1, 85.60 × 53.98 mm) proportions filled with the brand
  color (or `surfaceVariant` if the color string is empty or malformed, via `toBrandColorOrNull()`). Inside: the store's
  initials, large (`Store.initials`: the first letter or digit of the name's first two words, upper case;
  `StoreTest`), or the store logo via Coil at 60% size when the store has one (none does today). Below them, one line
  at the bottom left: the card's label, or the store name when there is no label, so two cards of one store can be told
  apart. The box is a `Column`: the initials take the height the label leaves and shrink to fit it (`autoSize`), so
  they don't run into the label at large font scales (`ListScreenTest.aTilesInitials_andLabel_doNotOverlap_…`;
  Robolectric measures text widths as near zero, so only the vertical layout is tested). TalkBack reads the tile as
  "store, label" (or the store alone): the box carries that content description, because TalkBack reads a merged
  node's description in place of its text (`ListScreenTest.aTile_isReadOutAsTheStoreName_andTheLabel`). Text is black or white, whichever has the higher WCAG contrast ratio with the
  box (`contrastingTextColor()`, `ui/BrandColor.kt`; `BrandColorTest`). Tests: `ListScreenTest.aTile_…`,
  `aTileWithoutALabel_showsTheStoreName`.
- The colored box is the shared element `card/{id}` — [[card-transition]].
- Tap → `directions.toCard(card.id, card.store.color)` → [[card-detail]].
- The Settings icon in the top bar is a no-op (`ListScreen.kt:75`, `/* TODO */`).
- The "Add card" floating button → `directions.toAddCard()` → the store picker of [[add-card]]. The grid has 88dp of bottom content
  padding so the button never covers the last row (`ListScreenTest.theLastTile_isNotCoveredByTheAddButtonWhenScrolledToTheEnd`).

## Known issues

- Tile logos use Coil's default loader, not the app's — no SVG, no error logging:
  [[list-screen-bypasses-image-loader]]. No store has a logo today, so no tile loads one; it matters once tiles show
  card photos (#7, #10).
