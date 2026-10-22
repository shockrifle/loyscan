# Edit card

The detail screen's Edit icon opens the card form ([[add-card]]) for that card: its label and number, checked live
against the store's rule as when adding it. Saving writes the edit and opens the card again. Issue
[#8](https://github.com/shockrifle/loyscan/issues/8); this page covers the slice that exists: editing the label and
number of one card. Deleting a card is on [[card-detail]].

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `DetailScreen.kt`: the Edit icon → `DetailDirections.toEdit(cardId, storeId)` → `CardForm(storeId, cardId = cardId)`
  (`Navigation.kt`). The icon is disabled until the card has loaded.
- `CardFormViewModel.kt`, `CardFormScreen.kt`: the same form as adding, in edit mode (`CardFormState.editing`, title
  "Edit card").
- `LoyaltyCardRepository.update(card)` → `LoyaltyCardDao.update(LoyaltyCardEdit)`, mapped with `LoyaltyCard.toEdit()`
  (`data/mapper/LoyaltyCardMapper.kt`).

## Behaviour

- **The form starts as the card.** The ViewModel loads the card by `CardForm.cardId`. Until the label or the number is
  changed, the form shows the card's own: a typed card's number as typed, a scanned card as its untouched scan
  ("Scanned · <format>", the number the rule recovers, read-only if it is shown as printed, with "Type the number
  instead"). The saved-state keys for the label and number stay `null` until the user changes them, so the card's
  values aren't copied into them and a change survives process death.
- **Editing a scanned card's number by hand** makes it a typed number: `scannedFormat` is cleared, so the barcode is
  built from the typed number by the store's rule, not drawn verbatim ([[2026-10-12-data-model-v2]]). Changing only the
  label keeps the scan as it is.
- The live check, "Save anyway", "Shown as …" and the "It looks like a card from:" hints work as when adding
  ([[add-card]]). Switching to a suggested store moves the card to that store.
- **Saving writes only what the form edits:** the store, label, number, scanned format and `updatedAt` (now). Room's
  partial `@Update(entity = LoyaltyCardEntity::class)` with `LoyaltyCardEdit` writes those columns and no others, so
  `createdAt`, the use count, `lastUsedAt`, the note and the photo paths stay as stored even if they changed after the
  form loaded the card. A blank label is saved as `null`.
- **After saving**, `CardFormDirections.toCard` opens the card in place of the card and its form
  (`popUpTo<CardList>()`, `Detail.countUse = false`), the same direction as after adding. The new `Detail` route carries
  the store's current colour, which matters when the store was switched. Back returns to the list.
- A failed save keeps the form open with "The card couldn't be saved. Try again.", as when adding (#49).
- A card that no longer exists (deleted, or a stale route) leaves the form empty and it can't be saved.

## Tests

- `CardFormViewModelTest`: `editingATypedCard_…`, `editingAScannedCard_…`, `savingAnEdit_…`,
  `editingAScannedCardsNumberByHand_…`, `savingAScannedCardWithOnlyItsLabelEdited_…`, `anEdit_isRestoredAfterProcessDeath`,
  `editingACardThatIsGone_cantBeSaved`, `aFailedEdit_…`. `CardFormScreenTest.editingACard_saysSo_andSavingOpensTheCard`.
- `LoyaltyCardDaoTest.updateEdit_writesTheEditedColumns_andLeavesTheRestAsStored`,
  `RoomLoyaltyCardRepositoryTest.update_…`, `LoyaltyCardMapperTest.toEdit_…`.
- `DetailScreenTest.editAction_opensTheCardsForm`; `AddCardDirectionsTest.editOnACard_…`, `savingAnEdit_…`.
- `AppNavigationTest.editingACardsLabel_savesIt_showsTheCard_andBackReturnsToTheList`.

## Not implemented

From #8: picking another store for a card (only a suggested store for a scan can be switched to), deleting several
cards at once (needs the list's multi-select, #7), and deleting photo files (needs photos, #10). A custom store's
barcode format belongs to the store, so changing it is editing the store (#53).
