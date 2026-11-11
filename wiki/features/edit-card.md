# Edit card

The detail screen's Edit icon, or Edit on the list with one card selected ([[card-list]] → Multi-select), opens the card form ([[add-card]]) for that card: its label and number, checked live
against the store's rule as when adding it, its note (#9), its photos ([[card-photos]], #10), and its store, which "Change store" replaces with any other. Saving writes
the edit and opens the card again. Issue [#8](https://github.com/shockrifle/loyscan/issues/8): editing the label,
number and store of one card. Deleting a card is on [[card-detail]].

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `DetailScreen.kt`: the Edit icon → `DetailDirections.toEdit(cardId, storeId)` → `CardForm(storeId, cardId = cardId)`
  (`Navigation.kt`). The icon is disabled until the card has loaded.
- `ListScreen.kt`: Edit in the `SelectionBar` → `ListDirections.toEditCard(id, storeId)` → the same route, over the list.
- `CardFormViewModel.kt`, `CardFormScreen.kt`: the same form as adding, in edit mode (`CardFormState.editing`, title
  "Edit card").
- `LoyaltyCardRepository.update(card)` → `LoyaltyCardDao.update(LoyaltyCardEdit)`, mapped with `LoyaltyCard.toEdit()`
  (`data/mapper/LoyaltyCardMapper.kt`).
- `StoreList.kt`: `StoreChooser`, the full-screen dialog "Change store" opens, over `StoreSearchList`, the search field
  and list it shares with the store picker.

## Behaviour

- **The form starts as the card.** The ViewModel loads the card by `CardForm.cardId`. Until the label, the note or the number is
  changed, the form shows the card's own: a typed card's number as typed, a scanned card as its untouched scan
  ("Scanned · <format>", the number the rule recovers, read-only if it is shown as printed, with "Type the number
  instead"). The saved-state keys for the label, note and number stay `null` until the user changes them, so the card's
  values aren't copied into them and a change survives process death.
- **Editing a scanned card's number by hand** makes it a typed number: `scannedFormat` is cleared, so the barcode is
  built from the typed number by the store's rule, not drawn verbatim ([[2026-10-12-data-model-v2]]). Changing only the
  label keeps the scan as it is.
- The live check, "Save anyway", "Shown as …" and the "It looks like a card from:" hints work as when adding
  ([[add-card]]). Switching to a suggested store moves the card to that store.
- **Change store** (next to the store's name, only while editing): a full-screen dialog, "Choose store", lists every
  other store (`CardFormState.otherStores`, by name) with the picker's search over names and aliases. Tapping one
  makes the form that store's through `CardFormViewModel.switchStore`, as a suggested store does: the label and the
  number, typed or an untouched scan, stay and are checked against the new store's rule, so a number it rejects needs
  "Save anyway". Close (or system Back) keeps the store. The chosen store is kept in the `SavedStateHandle`, so it
  survives process death. Once saving has started the store can't be switched (`switchStore` refuses), so the card
  opens in the colour of the store it was saved in (found in review). Adding a card doesn't offer it: Back returns to
  the picker there. Catalogue and custom stores are both listed; "Other card" isn't, so a card can't be moved to a
  store that doesn't exist yet. Its texts are translated with the store picker's, and the form's own with the card
  form's ([[add-card]] → Language, #72; `CardFormScreenTest.inHungarian_editingACard_isTranslated`).
- **Saving writes only what the form edits:** the store, label, number, scanned format, note (#9), photos (#10) and
  `updatedAt` (now). Room's partial `@Update(entity = LoyaltyCardEntity::class)` with `LoyaltyCardEdit` writes those
  columns and no others, so `createdAt`, the use count and `lastUsedAt` stay as stored even if they changed after the
  form loaded the card. A photo the edit replaces or removes is deleted, and one whose file is missing starts as no photo, so the save clears its name ([[card-photos]], #87). A blank label or note is saved as `null`; the note is trimmed.
- **After saving**, `CardFormDirections.toCard` opens the card in place of the card and its form
  (`popUpTo<CardList>()`, `Detail.countUse = false`), the same direction as after adding. The new `Detail` route carries
  the store's current colour, which matters when the store was switched. Back returns to the list.
- A failed save keeps the form open with "The card couldn't be saved. Try again.", as when adding (#49).
- Back during the save leaves the user on the card, and the edit is still saved, as when adding ([[add-card]], #64).
- A card that no longer exists (deleted, or a stale route) leaves the form empty and it can't be saved.

## Tests

- Note (#9): `CardFormViewModelTest.editingATypedCard_startsWithItsStoreLabelNumberAndNote`, `editingTheNote_savesIt`,
  `aBlankNote_isSavedAsNull`, `anEdit_isRestoredAfterProcessDeath`; `CardFormScreenTest.editingACard_showsItsNote_andSavesTheChangedNote`;
  `LoyaltyCardDaoTest.updateEdit_…`, `RoomLoyaltyCardRepositoryTest.update_…`, `LoyaltyCardMapperTest.toEdit_…`.
- `CardFormViewModelTest`: `editingATypedCard_…`, `editingAScannedCard_…`, `savingAnEdit_…`,
  `editingAScannedCardsNumberByHand_…`, `savingAScannedCardWithOnlyItsLabelEdited_…`, `anEdit_isRestoredAfterProcessDeath`,
  `editingACardThatIsGone_cantBeSaved`, `aFailedEdit_…`. `CardFormScreenTest.editingACard_saysSo_andSavingOpensTheCard`.
- `LoyaltyCardDaoTest.updateEdit_writesTheEditedColumns_andLeavesTheRestAsStored`,
  `RoomLoyaltyCardRepositoryTest.update_…`, `LoyaltyCardMapperTest.toEdit_…`.
- `DetailScreenTest.editAction_opensTheCardsForm`; `AddCardDirectionsTest.editOnACard_…`, `savingAnEdit_…`,
  `anEditThatEndsAfterBackFromTheForm_…`; `CardFormViewModelTest.anEditStillSavingWhenTheFormCloses_isStillSaved`
  (#64).
- Change store: `CardFormViewModelTest.editingACard_offersEveryOtherStore_byName`, `addingACard_offersNoOtherStore`,
  `movingATypedCardToAnotherStore_checksItsNumberAgainstThatStoresRule_andSavesItThere`,
  `movingAScannedCardToAStoreWhoseRuleRejectsIt_…`, `theStoreCantBeSwitchedWhileTheCardIsSaving`;
  `CardFormScreenTest.editingACard_canMoveItToAnotherStore_foundBySearch`,
  `theStoreChooser_canBeClosedWithoutChangingTheStore`, `addingACard_saysAddCard_andDoesNotOfferToChangeTheStore`.
- `AppNavigationTest.editingACardsLabel_savesIt_showsTheCard_andBackReturnsToTheList`,
  `movingACardToAnotherStore_savesIt_andTheCardShowsThatStore`.

## Not implemented

Deleting several cards at once belongs to the list's multi-select (#7). A custom store's barcode format belongs to the store, so changing it is editing the store (#53).
