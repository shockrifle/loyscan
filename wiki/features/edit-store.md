# Edit store

A custom store ("Other card", [[add-card]]) has an Edit button in the store picker. It opens the custom-store form
for that store, which saves its new name, colour and barcode format for typed numbers and returns to the picker.
Catalogue stores have no Edit button and can't be changed. Issue [#53](https://github.com/shockrifle/loyscan/issues/53).

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `StorePickerScreen.kt`: the trailing Edit icon on a custom store's row ("Edit <name>") →
  `StorePickerDirections.toEditStore(storeId)` → `CustomStore(storeId)` (`Navigation.kt`).
- `CustomStoreViewModel.kt`, `CustomStoreScreen.kt`: the same form as creating, in edit mode
  (`CustomStoreState.editing`, title "Edit store", button "Save").
- `StoreRepository.update(store)` → `StoreDao.updateCustom(…)` (`data/local/db/dao/StoreDao.kt`).

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
- **A store that isn't there** (deleted, or a catalogue store's id in the route): the form says "This store is no
  longer available." and can't be saved.

## Tests

- `CustomStoreViewModelTest` (Robolectric, since it reads its route): `editingAStore_startsAsTheStore`,
  `savingAnEdit_…`, `aFailedEdit_…`, `editingAStoreThatIsGone_…`, `aCatalogueStore_cantBeEdited`,
  `anEdit_isRestoredAfterProcessDeath`. `CustomStoreScreenTest.editingAStore_…`, `editingAStoreThatIsGone_…`.
- `RoomStoreRepositoryTest`: `update_savesACustomStoresNameColourAndFormat`, `update_leavesACatalogueStoreAsItIs`,
  `aCustomStoresNewFormat_drawsItsTypedCards_butNotItsScannedOnes`.
- `StorePickerScreenTest.aCustomStoresEditButton_…`; `AddCardDirectionsTest.editingACustomStore_opensItsFormOverThePicker`.
- `AppNavigationTest.editingACustomStore_fromThePicker_renamesItForItsCards`.

## Not implemented

Deleting a custom store (#53).
