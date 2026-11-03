# Card photos

A card can carry photos of the physical card, front and back (#10). They are copied into app-private storage and
deleted with the card. Storage and deletion: [[2026-11-03-card-photos-as-private-files]].

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `data/photos/CardPhotos.kt`: `CardPhotos` (`import(uri)`, `delete(names)`), implemented by `FileCardPhotos` over
  `files/card_photos/` (`di/CardPhotosModule.kt`).
- `CardFormScreen.kt` (`PhotoSlot`), `CardFormViewModel.kt` (`onPhotoPicked`, `removePhoto`, `onCleared`): adding,
  replacing and removing photos while adding or editing a card.
- `ui/CardPhoto.kt`: `CardPhoto(name)`, the image model; `CardPhotoMapper` on the app's Coil loader
  (`di/ImageLoaderModule.kt`) maps it to its file; `CARD_ASPECT_RATIO`.
- `data/repository/RoomLoyaltyCardRepository.kt` (`update`, `delete`, `deleteAll`) and
  `data/repository/RoomStoreRepository.kt` (`delete`): delete the files whose names the row write dropped.

## Behaviour

- **In the card form** ([[add-card]], [[edit-card]]), under the note: a slot for the front and one for the back. An
  empty slot says "Add front photo" / "Add back photo" and opens the system photo picker (images only, no storage
  permission; the same `PicturePicker` as the scanner's "Scan from image", [[barcode-scanner]]). A slot with a photo
  shows it in the card's proportions (ID-1, cropped to fill) with Replace and Remove icons ("Replace front photo",
  "Remove front photo"). While a picked picture is copied in, the slot shows a progress indicator, and neither Save
  nor another pick is possible. A picture that can't be copied (e.g. a cloud photo that won't download) says "The
  photo couldn't be added. Try again."; the next pick clears it. Once saving has started, photos can't be changed.
- **What the form keeps.** A pick is copied in at once (the picker's read grant ends with the process). Its name goes
  into the `SavedStateHandle` (keys `frontPhoto` / `backPhoto`; `""` means removed), so it survives process death.
  Replacing or removing a photo picked in this form deletes that copy at once. The edited card's own photo stays
  until the edit is saved, then the repository deletes it. A form closed without saving deletes the copies picked in
  it (`onCleared`, through `CardPhotos.discard`, since `viewModelScope` is already cancelled). A form closed while
  its save runs leaves them to the save, and a save that then fails deletes them.
- Images load through `hiltImageLoader()`, never Coil's default ([[coil-image-loading]]).
- **Files are deleted on every path:**
  - replacing or removing a photo in an edit ([[edit-card]]);
  - deleting a card from its screen ([[card-detail]]);
  - deleting cards by multi-select ([[card-list]]);
  - deleting a custom store with its cards ([[edit-store]]).

  A catalogue store can't be deleted, so its cards' photos stay.

## Tests

- `CardFormViewModelTest`: `aPickedFrontPhoto_…`, `editingACard_startsWithItsPhotos_…`, `removingACardsPhoto_…`,
  `aPhotoPickedInTheForm_isDeleted_whenAnotherReplacesIt_orItIsRemoved`, `aFormClosedWithoutSaving_…`,
  `aSavedForm_keepsItsPhotos_…`, `aSaveStillRunningWhenTheFormCloses_keepsItsPhoto`,
  `aSaveThatFailsAfterTheFormClosed_deletesItsPhoto`, `aFailedSave_keepsThePhotos_…`, `whileAPhotoIsCopiedIn_…`,
  `aPictureThatCantBeCopiedIn_…`, `aCancelledPick_…`, `noPhotoCanBePickedOrRemoved_onceSavingHasStarted`,
  `aPickedPhoto_isRestoredAfterProcessDeath`. `CardFormScreenTest`: `addingAFrontPhoto_…`,
  `editingACard_showsItsPhoto_whichCanBeReplacedOrRemoved`, `aPictureThatCantBeCopiedIn_saysSo`.
- `ui/CardPhotoTest`: the app's loader maps a `CardPhoto` to its file.
- `data/photos/FileCardPhotosTest`: a picked picture is copied under a new name, an unreadable one leaves no file,
  delete skips missing names, discard deletes in the background, a name can't leave the directory.
- `RoomLoyaltyCardRepositoryTest`: `update_deletesAReplacedOrRemovedPhoto_andKeepsTheOthers`,
  `update_keepingThePhotos_deletesNone`, `delete_deletesTheCardsPhotos`, `deleteAll_deletesThoseCardsPhotos_only`.
  `RoomStoreRepositoryTest.delete_deletesThePhotosOfTheStoresCards`, `delete_ofACatalogueStore_keepsItsCardsPhotos`.
  `LoyaltyCardDaoTest.updateReturningDroppedPhotos_…`, `deleteReturningPhotos_…`.

## Not implemented

- A form whose task is removed after process death never comes back to delete the copies picked in it. They stay
  as orphan files: no sweep looks for files no card names.

Taking a photo with the camera, cropping to the card's ratio, the front photo as the list tile, and backup (#71) are
later parts of #10.
