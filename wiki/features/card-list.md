# Card list ("My Cards")

The start destination: a top bar titled "My Cards" with a Sort menu, an About button and a Settings icon, a search field, and a 2-column grid of card
tiles, one per row in `loyalty_cards`, live-updating from Room. Sort, search and multi-select: #7.

**Entry points:** `ListScreen` and `CardItem` in `app/src/main/java/com/bdaniel/loyscan/ListScreen.kt`, the tile's face
(`CardTileFace`, shared with the store's logo preview, #192) in `app/src/main/java/com/bdaniel/loyscan/ui/CardTileFace.kt`;
`ListViewModel` (`app/src/main/java/com/bdaniel/loyscan/ListViewModel.kt`); sort and search in
`app/src/main/java/com/bdaniel/loyscan/domain/CardSearch.kt`; route `CardList`
(`app/src/main/java/com/bdaniel/loyscan/Navigation.kt`).

## Behavior

- Data: `ListViewModel.cards` ← `LoyaltyCardRepository.getAllWithStore()` (each card with its store), sorted by the
  stored choice, then narrowed by the query (`ListViewModel.cards`). Initial value `null` (not loaded): the grid is
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
- Tile (`CardItem`: the `Card`, its click, selection, shared element and TalkBack description around `CardTileFace`,
  which draws the rest): a `Card` containing a box in bank-card (ID-1, 85.60 × 53.98 mm) proportions filled with the brand
  color (or `surfaceVariant` if the color string is empty or malformed, via `toBrandColorOrNull()`). It shows the store's
  initials, large (`Store.initials`: the first letter or digit of the name's first two words, upper case;
  `StoreTest`), or the store's logo when the user gave the store one (#151, [[2026-11-13-user-supplied-store-logos]]):
  a `CardPhoto` file through the app's loader, tagged `CARD_TILE_LOGO_TAG`, 60% of the tile's height
  (`TILE_LOGO_FRACTION`), drawn the way the user chose for the store (`StoreLogoImage`, #240, [[store-logo]]; test:
  `ListScreenTest.aLogoInSingleColour_isDrawnInItsColour_onTheTile`; a Recoloured one on the tile's colour, redrawn when it
  changes: `aRecolouredLogo_isDrawnInItsColour_onTheTile_andFollowsANewBrandColour`); a logo that can't be loaded, or whose file is missing (`CardPhotos.withoutMissing`), falls back
  to the initials. **Only the card's own label is drawn** (#183), one line at the bottom left over the tile, as on a
  photo; a card without a label shows no text at all, since the logo, the photo or the initials say which store it is.
  The cost: two unlabelled cards of stores with the same initials and a similar colour look alike (the store is still
  heard through TalkBack and found by search); a label or a logo tells them apart.
  Until #183 a tile showed the store name when there was no label. The logo and the initials are centred in the whole
  tile, with a label or without (#183, the user's call: as a photo sits under its label). The logo's size doesn't depend
  on the label, which is drawn over it: a square logo reaches under the label text on a phone, so on a logo tile the
  label is white on the photo's dark band and fade, as on a photo (review of #183, the user's call over shrinking the
  logo; `aLogoTile_hasTheDarkLabelBand_underItsLabel_anInitialsTileDoesNot`, pixels). An initials tile's label has no
  band. The initials get the height the label leaves (`CentredOverLabel`) and shrink to
  fit it (`autoSize`); they are centred when that keeps them clear of the label, else they sit just above it (large
  font scales on small tiles), so they never run into it or get squeezed out (`ListScreenTest.aTilesInitials_andLabel_doNotOverlap_…`;
  Robolectric's legacy text measuring makes widths near zero and a label 44px tall, so the centring test,
  `aTilesInitials_areCentredInTheTile_withALabelOrWithout`, uses real text measuring at 411dp). Tests:
  `aTileWithoutALabel_showsOnlyTheInitials`, `aLogoTileWithoutALabel_showsNoText`, `aPhotoTileWithoutALabel_showsNoText`,
  `aStoresLogo_isCentredInTheTile_atThreeFifthsOfItsHeight_withALabelOrWithout`. TalkBack reads the tile as
  "store, label" (or the store alone), so the store is still heard though it isn't drawn: the box carries that content
  description, because TalkBack reads a merged node's description in place of its text
  (`ListScreenTest.aTile_isReadOutAsTheStoreName_andTheLabel`, on the merged tiles). Tests find an unlabelled card's
  tile by that description (`tile(store)`). Text is black or white, whichever has the higher WCAG contrast ratio with the
  box (`contrastingTextColor()`, `ui/BrandColor.kt`; `BrandColorTest`). Tests: `ListScreenTest.aTile_…`.
- **A card with a front photo** ([[card-photos]], #10) shows it as its tile only when the card's "Show photo on My
  Cards" switch is on (`LoyaltyCard.showPhotoOnList`, #184; off by default and for every card from before it). Off, the
  tile shows the store's logo or the initials as for a card without a photo (`CardItem` passes no photo to
  `CardTileFace`; `ListScreenTest.aCardWithAFrontPhoto_notShownOnMyCards_showsTheInitials`,
  `aCardWithAFrontPhoto_notShownOnMyCards_showsItsStoresLogo`, `aCardSetToShowItsPhoto_withoutAFrontPhoto_showsTheInitials`). On, it is the tile, cropped to fill the box (`CARD_TILE_PHOTO_TAG`),
  in place of the initials or logo; a label stays at the bottom left in white, on a 60% black band
  (`PHOTO_LABEL_SCRIM`, `ui/CardPhoto.kt`) that a 16dp gradient fades into above it. The band and the fade are drawn
  only with a label: an unlabelled photo is shown whole (#183, `ListScreenTest.aPhotoTile_hasTheDarkLabelBand_onlyWithALabel`,
  pixels). Over a white photo that is 5.7:1, past
  WCAG AA's 4.5:1 (`BrandColorTest.whiteText_onThePhotoLabelScrim_readsOnAWhitePhoto`); the first version's gradient ran
  through the text and left it at about 2.4:1 (review of #10). The brand colour shows under the photo while it loads
  (initials there would flash on every photo tile), and a photo the loader can't load, e.g. a broken file, falls back to
  the initials (`onError`). The back photo is only on the card screen. A front photo whose file is missing (deleted or otherwise lost, #87) is left off the card by
  `ListViewModel` (`CardPhotos.withoutMissing`), so the tile shows the initials rather than an empty image (#89). Tile
  images, photo and logo, load through `hiltImageLoader()` ([[coil-image-loading]]). Tests:
  `ListScreenTest.aCardWithAFrontPhoto_showsItOnItsTile_inPlaceOfTheInitials`, `aCardWithOnlyABackPhoto_…`,
  `aTileWhoseFrontPhotoFileIsMissing_showsTheInitials`, `aFrontPhotoThatCantBeLoaded_showsTheInitials`, `ListViewModelTest.aFrontPhotoWhoseFileIsMissing_isLeftOffTheTile`,
  the logo (#151): `aCardWhoseStoreHasALogo_showsItOnItsTile_inPlaceOfTheInitials`, `aFrontPhoto_takesPrecedenceOverTheStoresLogo`,
  `aLogoThatCantBeLoaded_showsTheInitials`, `aLogoWhoseFileIsMissing_showsTheInitials`,
  `ListViewModelTest.aStoresLogoWhoseFileIsMissing_isLeftOffItsTiles`, `aStoresLogo_isLookedUpOnce_forAllItsCards` (the
  list's `withoutMissing(cards)` checks each store's logo file once per emission, not once per card),
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
- The Info icon in the top bar, "About", → `directions.toAbout()` → [[about]] (#150).
- The Settings icon in the top bar is a no-op (`ListScreen.kt`, `ListScreen`'s top bar, `/* TODO */`).
- The "Add card" floating button → `directions.toAddCard()` → the store picker of [[add-card]]. The grid has 88dp of bottom content
  padding (`ADD_BUTTON_CLEARANCE`) so the button never covers the last row (`ListScreenTest.theLastTile_isNotCoveredByTheAddButtonWhenScrolledToTheEnd`).

## Known issues

None recorded. (Tile images used Coil's default loader until #10's photo tiles: [[list-screen-bypasses-image-loader]],
resolved.)
