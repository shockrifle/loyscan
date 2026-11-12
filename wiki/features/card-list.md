# Card list ("My Cards")

The start destination: a top bar titled "My Cards" with a Sort menu, a search field, and a 2-column grid of card
tiles, one per row in `loyalty_cards`, live-updating from Room. Sort, search and multi-select: #7.

**Entry points:** `ListScreen` and `CardItem` in `app/src/main/java/com/bdaniel/loyscan/ListScreen.kt`;
`ListViewModel` (`app/src/main/java/com/bdaniel/loyscan/ListViewModel.kt`); sort and search in
`app/src/main/java/com/bdaniel/loyscan/domain/CardSearch.kt`; route `CardList`
(`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:11-12`).

## Behavior

- Data: `ListViewModel.cards` ← `LoyaltyCardRepository.getAllWithStore()` (each card with its store), sorted by the
  stored choice, then narrowed by the query (`ListViewModel.kt:52-68`). Initial value `null` (not loaded): the grid is
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
  in the saved state: it survives process death, not a restart, and is cleared when no card is left. A card's note is written in the card form
  ([[add-card]], #9).
- **Empty state** (`EmptyCardList`): once the cards have loaded, with none stored and no search, the screen shows "No
  cards yet" and an "Add your first card" button (→ `directions.toAddCard()`, like the floating button) in place of the
  search field and grid. It waits for the load (`cards` is `null` until then), and a search that matches nothing keeps
  the field and says "No card matches" instead. When the last card is deleted, the ViewModel clears the query
  (`ListViewModel.cards`), so a search left from before shows neither "No card matches" over nothing nor hides the
  next card added (`ListViewModelTest.deletingTheLastCard_clearsTheQuery`,
  `ListScreenTest.deletingTheLastCard_duringASearch_showsTheEmptyState`). Tests: `ListScreenTest.noCards_…`, `theEmptyState_waitsForTheCardsToLoad`,
  `aSearchThatMatchesNothing_isNotTheEmptyState`.
- Two columns of card-ratio tiles in `CardShape`, 16dp from the screen's edges with 12dp gaps ([[design-system]], #108;
  `ListScreenTest.tiles_areInset16dpFromTheScreenEdges_with12dpGaps`); a photo tile and a plain one are the same size
  (`aPhotoTile_andAPlainTile_haveTheSameSize`).
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
- **A card with a front photo** ([[card-photos]], #10) shows it as its tile, cropped to fill the box (`CARD_TILE_PHOTO_TAG`),
  in place of the initials or logo; the label stays at the bottom left in white, on a 60% black band
  (`PHOTO_LABEL_SCRIM`, `ui/CardPhoto.kt`) that a 16dp gradient fades into above it. Over a white photo that is 5.7:1, past
  WCAG AA's 4.5:1 (`BrandColorTest.whiteText_onThePhotoLabelScrim_readsOnAWhitePhoto`); the first version's gradient ran
  through the text and left it at about 2.4:1 (review of #10). The brand colour shows under the photo while it loads
  (initials there would flash on every photo tile), and a photo the loader can't load, e.g. a broken file, falls back to
  the initials (`onError`). The back photo is only on the card screen. A front photo whose file is missing (deleted or otherwise lost, #87) is left off the card by
  `ListViewModel` (`CardPhotos.withoutMissing`), so the tile shows the initials rather than an empty image (#89). Tile
  images, photo and logo, load through `hiltImageLoader()` ([[coil-image-loading]]). Tests:
  `ListScreenTest.aCardWithAFrontPhoto_showsItOnItsTile_inPlaceOfTheInitials`, `aCardWithOnlyABackPhoto_…`,
  `aTileWhoseFrontPhotoFileIsMissing_showsTheInitials`, `aFrontPhotoThatCantBeLoaded_showsTheInitials`, `ListViewModelTest.aFrontPhotoWhoseFileIsMissing_isLeftOffTheTile`,
  end to end `AppNavigationTest.aCardsFrontPhoto_isLoadedOntoItsTile_byTheAppsImageLoader` (only the app's loader can
  map a `CardPhoto` to its file, so the photo reaches its memory cache).
- The colored box is the shared element `card/{id}` — [[card-transition]].
- Tap → `directions.toCard(card.id, card.store.color)` → [[card-detail]], unless cards are selected.
- **Multi-select.** A long press on a tile selects it (TalkBack offers it as "Select"). While any card is selected, the
  top bar is the `SelectionBar`: Close, "N selected", Edit when exactly one card is selected, and Delete; the Add
  button is hidden. A tap then selects or deselects a tile instead of opening it, and deselecting the last one, Close
  or system Back (`BackHandler`) ends the selection. A selected tile has a primary-colour border and a tick, and is
  `selected` in semantics. Tests: `ListScreenTest.longPressingATile_…`, `whileSelecting_…`, `close_…`, `back_…`.
  - The selected ids live in `ListViewModel`'s `SavedStateHandle`, so they survive process death
    (`ListViewModelTest.theSelection_isRestoredAfterProcessDeath`). `selection` starts from the saved ids, so a delete
    dialog open at process death is still open after it (`ListScreenTest.theDeleteDialog_survivesProcessDeath`). `selection` keeps only ids of cards the list
    shows, so a card deleted elsewhere drops out. A new search ends the selection, so a card the search hides is never
    selected, and never deleted with the others (`changingTheQuery_clearsTheSelection`).
  - **Edit** → `directions.toEditCard(id, storeId)` → the card form over the list ([[edit-card]]), and the selection
    ends. Saving opens the card, as an edit from the card does; Back from it returns to the list
    (`AddCardDirectionsTest.editOnTheList_…`, `AppNavigationTest.editingTheOneSelectedCard_…`).
  - **Delete** asks first ("Delete this card?" / "Delete N cards?"), then deletes the selected cards in one write,
    `LoyaltyCardRepository.deleteAll(ids)` → `DELETE … WHERE id IN (:ids)`, and the selection ends. The write
    finishes even if the list is cleared meanwhile, as on the card screen (#65); a second tap on Delete while it
    runs is ignored, and the dialog can't be cancelled until the write ends (cancelling wouldn't stop it). A failed
    delete keeps the cards and the selection, the dialog says so, and Delete tries again; a failure is forgotten once
    the selection ends
    (`ListViewModelTest.deletingTheSelection_…`, `aFailedDelete_…`, `ListScreenTest.deletingTheSelection_…`,
    `AppNavigationTest.selectingCardsOnTheList_andDeletingThem_keepsTheOthers`). The cards' photo files are deleted
    with them ([[card-photos]]).
- **Language** (#72): every text on the list is a string resource, in English or Hungarian by the phone's language;
  the store names and labels are the user's data and aren't translated ([[add-a-string]]). Tests:
  `ListScreenTest.inHungarian_everyTextOnTheList_isTranslated`.
- The Settings icon in the top bar is a no-op (`ListScreen.kt:145`, `/* TODO */`).
- The "Add card" floating button → `directions.toAddCard()` → the store picker of [[add-card]]. The grid has 88dp of bottom content
  padding (`ADD_BUTTON_CLEARANCE`) so the button never covers the last row (`ListScreenTest.theLastTile_isNotCoveredByTheAddButtonWhenScrolledToTheEnd`).

## Known issues

None recorded. (Tile images used Coil's default loader until #10's photo tiles: [[list-screen-bypasses-image-loader]],
resolved.)
