# Edit store

A custom store ("Other card", [[add-card]]) has an Edit button in the store picker. It opens the custom-store form
for that store, which saves its new name, colour and barcode format for typed numbers and returns to the picker. The
form's Delete action deletes the store and its cards after a confirmation that says how many cards go with it.
Catalogue stores have no Edit button and can't be changed or deleted. Issue [#53](https://github.com/shockrifle/loyscan/issues/53).

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `StorePickerScreen.kt`: the trailing Edit icon on a custom store's row ("Edit <name>") →
  `StorePickerDirections.toEditStore(storeId)` → `CustomStore(storeId)` (`Navigation.kt`).
- `CustomStoreViewModel.kt`, `CustomStoreScreen.kt`: the same form as creating, in edit mode
  (`CustomStoreState.editing`, title "Edit store", button "Save").
- `StoreRepository.update(store)` → `StoreDao.updateCustom(…)`; `StoreRepository.delete(id)` → `StoreDao.deleteCustom(id)`
  (`data/local/db/dao/StoreDao.kt`); the card count from `LoyaltyCardRepository.countForStore(storeId)` →
  `LoyaltyCardDao.countForStore`.

## Behaviour

- **The form starts as the store.** The ViewModel finds the routed id among the custom stores (`StoreRepository.getAll`).
  Until it has loaded, the form shows nothing (`CustomStoreState.ready`). The saved-state keys for the name, colour and
  format stay `null` until the user changes them, so a change survives process death and the store's own values aren't
  copied into them, as for [[edit-card]].
- **Saving writes only what the form edits:** the name (trimmed, required), colour, `defaultBarcodeFormat` and
  `updatedAt` (now). `StoreDao.updateCustom` is an `UPDATE … WHERE id = :id AND isCustom = 1`, so a catalogue store
  is never changed through it, whatever the caller passes; the catalogue loader owns those rows ([[store-catalogue]]).
  Then the form goes back to the picker (`CustomStoreDirections.toBack`), which lists the store under its new name.
- **What a new format changes.** A card has no format of its own: a typed number is drawn in the store's
  `defaultBarcodeFormat` (a custom store has no rule), a scan in its `scannedFormat` ([[2026-10-12-data-model-v2]]).
  So after the edit the store's typed cards are drawn in the new format and its scanned cards stay as scanned. A typed
  number the new format can't hold is drawn in a fallback, and the detail screen says "Shown as …" ([[card-detail]]).
- A failed save keeps the form open with "The store couldn't be saved. Try again.", as when creating.
- **Deleting.** The top bar's Delete icon (edit mode only, once the store has loaded) asks "Delete <name>?", naming
  the store as saved, not an unsaved rename in the field. The text says what goes with it: "It has no cards.", "Its
  card is deleted with it." or "Its N cards are deleted with it." (`CustomStoreState.cardCount`, live from
  `countForStore`). Delete removes the store with `DELETE … WHERE id = :id AND isCustom = 1`; the cards go through the
  foreign key's `ON DELETE CASCADE` ([[architecture]] → Worked example, step 1), in the same statement. Then the form
  goes back to the picker, which no longer lists the store. A failed delete says "The store couldn't be deleted. Try
  again." in the dialog, and Delete tries again; closing the dialog forgets the failure, and a delete under way or
  done isn't repeated. Room may re-emit the store list without the store before the delete returns, so a store gone
  while its delete is under way isn't reported as gone (found in review; `aStoreGoneBeforeItsDeleteReturns_…`). This
  is the same lifecycle as deleting a card ([[card-detail]]): both use the `Deletion` enum
  (`Deletion.kt`). This also removes a store abandoned before its first card ([[2026-10-22-custom-stores]]).
- The deleted cards' photo files are deleted too ([[card-photos]]), and the store's logo file.
- **Logo** (#151, [[store-logo]]): the edit form, once the store has loaded, has a "Logo" section: the store's tile
  preview (`LogoPreview` in `ui/CardTileFace.kt`, shared with the logo screen, #162) drawn with the form's name and
  colour as typed (the initials of the name, as a tile, `storeInitials`, #192), the store's
  logo on it (`CustomStoreState.logo`; a logo whose file is missing is none, checked once per store-list update), and
  **Change logo**, which opens the store's logo screen over the form (`CustomStoreDirections.toStoreLogo`); Back
  returns to the form with its unsaved edits, and a logo set there shows at once. Save leaves the logo as it is
  (`updateCustom` doesn't write it; `RoomStoreRepositoryTest.update_keepsACustomStoresLogo`). The new-store form has no slot ([[2026-11-13-user-supplied-store-logos]]).
- **Back during a save or delete** (#65): the user stays where Back took them, and the store is still created,
  saved or deleted. `customStoreDirections(entry)` navigates (to the scanner, or back) only while the form's own
  back-stack entry is current, and the writes run in `withContext(NonCancellable)`, as the card form's do
  ([[2026-10-28-save-navigates-after-back]]). Tests: `AddCardDirectionsTest.aNewStoreSavedAfterBackFromItsForm_…`,
  `aStoreSavedOrDeletedAfterBackFromItsForm_…`; `CustomStoreViewModelTest.aNewStoreStillSavingWhenTheFormCloses_…`,
  `anEditStillSavingWhenTheFormCloses_…`, `aDeleteStillRunningWhenTheFormCloses_…`.
- **A store that isn't there** (deleted, or a catalogue store's id in the route): the form says "This store is no
  longer available." and can't be saved. The card form says the same for a deleted store ([[add-card]], #51).
- **Language** (#72): every text on the form and its delete dialog is a string resource, in English or Hungarian by
  the phone's language; the store's name is data. The card count is a plural (`one`, `other`), with its own string for
  no cards, since English and Hungarian say 0 with `other`. The colour swatches' names come from the ViewModel as
  resource ids (`CustomStoreViewModel.Swatch.name`), and the format chips are named by `barcodeFormatName`
  ([[2026-11-09-ui-language]]: only the QR names are translated). Tests: `CustomStoreScreenTest.inHungarian_…` (the
  new form with a failed create, the edit form with its delete dialog and a failed delete, one card, no cards, a store
  that is gone).

## Tests

- `CustomStoreViewModelTest` (Robolectric, since it reads its route): `editingAStore_startsAsTheStore`,
  `savingAnEdit_…`, `aFailedEdit_…`, `editingAStoreThatIsGone_…`, `aCatalogueStore_cantBeEdited`,
  `anEdit_isRestoredAfterProcessDeath`, `editingAStore_saysHowManyCardsItHas`, `delete_…`,
  `aSecondDeleteWhileTheFirstIsDeleting_deletesOnce`, `aFailedDelete_…`, `aNewStore_orOneThatIsGone_isNotDeleted`;
  the logo tests are listed on [[store-logo]].
  `CustomStoreScreenTest`: `editingAStore_…`, `editingAStoreThatIsGone_…`, `deletingAStore…` (the three card-count
  texts, Cancel), `theDeleteDialog_namesTheStoreAsSaved_…`, `aDeleteThatFails_…`, `aNewStore_hasNoDeleteAction`.
- `RoomStoreRepositoryTest`: `update_savesACustomStoresNameColourAndFormat`, `update_leavesACatalogueStoreAsItIs`,
  `aCustomStoresNewFormat_drawsItsTypedCards_butNotItsScannedOnes`, `delete_removesACustomStore_andItsCards`,
  `delete_leavesACatalogueStoreAndItsCards`; `RoomLoyaltyCardRepositoryTest.countForStore_…`.
- `StorePickerScreenTest.aCustomStoresEditButton_…`; `AddCardDirectionsTest.editingACustomStore_opensItsFormOverThePicker`.
- A new format reaching the card: `DetailViewModelTest.aStoreFormatChange_redrawsATypedCard_butNotAScannedOne`, and
  end to end `AppNavigationTest.aCustomStoresNewFormat_redrawsItsTypedCard` (4242 switched to EAN-13 is drawn in the
  fallback, so the detail says "Shown as Code 128").
- `AppNavigationTest.editingACustomStore_fromThePicker_renamesItForItsCards`,
  `deletingACustomStore_fromThePicker_deletesItsCardsToo`.
