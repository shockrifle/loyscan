# Card photos

A card can carry photos of the physical card, front and back (#10). They are copied into app-private storage and
deleted with the card. Storage and deletion: [[2026-11-03-card-photos-as-private-files]].

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `data/photos/CardPhotos.kt`: `CardPhotos` (`import(uri)`, `delete(names)`), implemented by `FileCardPhotos` over
  `files/card_photos/` (`di/CardPhotosModule.kt`).
- `data/photos/CardPhotoEncoding.kt`: `encodeCardPhoto`, how an imported picture is stored (#88).
- `DetailScreen.kt`: the photos under the note, `PhotoViewer`.
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
- **What is stored** (#88): not the picked file itself but a JPEG at most 2048 px on its long side (a smaller
  picture keeps its size), turned the way its EXIF orientation says, a transparent picture on white, and with no EXIF
  (no location). A picked file that isn't a picture can't be added. Why: [[2026-11-03-card-photos-as-private-files]].
  Orientation of a HEIF picture is read through the platform `ExifInterface` too; not checked on a device.
- **What the form keeps.** A pick is copied in at once (the picker's read grant ends with the process). Its name goes
  into the `SavedStateHandle` (keys `frontPhoto` / `backPhoto`; `""` means removed), so it survives process death.
  Replacing or removing a photo picked in this form deletes that copy at once. The edited card's own photo stays
  until the edit is saved, then the repository deletes it. A form closed without saving deletes the copies picked in
  it (`onCleared`, through `CardPhotos.discard`, since `viewModelScope` is already cancelled). A form closed while
  its save runs leaves them to the save, and a save that then fails deletes them.
- **On the list** ([[card-list]]): the front photo is the card's tile, with the label on a dark scrim; a card with only
  a back photo, or a front photo that can't be loaded, keeps the initials.
- **On the card** ([[card-detail]]): under the note, the photos it has, front first; a tap shows one full screen
  on black (`PhotoViewer`, `PHOTO_VIEWER_TAG`), and a tap or Back closes it.
- **Orphans:** a process killed with a form open (swiped from Recents) skips `onCleared`. On every database open
  `CardPhotoSweepCallback` (`data/local/db/`, added in `di/DatabaseModule.kt`) deletes photo files that no card names
  and that are over a day old; newer ones may belong to a form still open or about to be restored. A save drops a
  picked photo whose file has gone since, and keeps the card's own photo for that side ([[2026-11-03-card-photos-as-private-files]]).
- **A photo whose file is missing** (a cloud restore brings back the database but not the photos, see Backup) is
  treated as no photo (#87). `CardPhotos.withoutMissing(card)` (`data/photos/CardPhotos.kt`) drops such a name from
  the card `DetailViewModel` shows, from the cards `ListViewModel` lists, and from the card `CardFormViewModel` edits. So
  the card screen shows no thumbnail or viewer for it, the tile shows the initials (#89), and the form shows that side
  as "Add … photo". Saving the edit writes the side as `null`, which
  clears the name; until then the row keeps it.
- **Backup:** `files/card_photos/` is excluded from cloud backup; device-to-device transfer takes it on Android 12+
  ([[android-app]], [[2026-11-03-card-photos-as-private-files]]).
- Images load through `hiltImageLoader()`, never Coil's default ([[coil-image-loading]]).
- **Files are deleted on every path:**
  - replacing or removing a photo in an edit ([[edit-card]]);
  - deleting a card from its screen ([[card-detail]]);
  - deleting cards by multi-select ([[card-list]]);
  - deleting a custom store with its cards ([[edit-store]]).

  A catalogue store can't be deleted, so its cards' photos stay.

## Tests

- `DetailScreenTest`: `aCardWithPhotos_showsThemUnderTheNumber`, `aCardWithOnlyABackPhoto_showsOnlyThat`,
  `aCardWithoutPhotos_showsNone`, `tappingAPhoto_showsItFullScreen_andATapOrBackClosesIt`,
  `aPhotoWhoseFileIsMissing_showsNoThumbnail` (the thumbnail is the viewer's only way in), `aCardWhosePhotoFilesAreAllMissing_showsNoPhotos` (#87),
  `theOpenPhoto_staysOpenWhenThePhoneTurns`. End to end: `AppNavigationTest.aCardsPhoto_isShownOnTheCard`,
  `deletingACardFromTheList_deletesItsPhotoFiles`.
- `CardFormViewModelTest`: `aPickedFrontPhoto_…`, `editingACard_startsWithItsPhotos_…`, `removingACardsPhoto_…`,
  `aPhotoPickedInTheForm_isDeleted_whenAnotherReplacesIt_orItIsRemoved`, `aFormClosedWithoutSaving_…`,
  `aSavedForm_keepsItsPhotos_…`, `aSaveStillRunningWhenTheFormCloses_keepsItsPhoto`,
  `aSaveThatFailsAfterTheFormClosed_deletesItsPhoto`, `aFailedSave_keepsThePhotos_…`, `whileAPhotoIsCopiedIn_…`,
  `aPictureThatCantBeCopiedIn_…`, `aCancelledPick_…`, `noPhotoCanBePickedOrRemoved_onceSavingHasStarted`,
  `aPickedPhoto_isRestoredAfterProcessDeath`. `CardFormScreenTest`: `addingAFrontPhoto_…`,
  `editingACard_showsItsPhoto_whichCanBeReplacedOrRemoved`, `aPictureThatCantBeCopiedIn_saysSo`.
- A missing file (#87): `DetailViewModelTest.aPhotoWhoseFileIsMissing_isLeftOffTheCard`,
  `CardFormViewModelTest.editingACardWhosePhotoFileIsMissing_startsWithThatSideEmpty_andSavingClearsItsName`,
  `CardFormScreenTest.editingACardWhosePhotoFileIsMissing_offersToAddThatPhoto`.
- `ui/CardPhotoTest`: the app's loader maps a `CardPhoto` to its file.
- The list tile: `ListScreenTest.aCardWithAFrontPhoto_showsItOnItsTile_inPlaceOfTheInitials`,
  `aCardWithOnlyABackPhoto_showsTheInitialsOnItsTile`, `aTileWhoseFrontPhotoFileIsMissing_showsTheInitials`,
  `aFrontPhotoThatCantBeLoaded_showsTheInitials`,
  `ListViewModelTest.aFrontPhotoWhoseFileIsMissing_isLeftOffTheTile` (#89). End to end:
  `AppNavigationTest.aCardsFrontPhoto_isLoadedOntoItsTile_byTheAppsImageLoader`.
- `CardPhotoSweepCallbackTest` (on-disk database, closed and reopened), `DatabaseModuleTest.opening_sweepsAnOldPhotoNoCardNames`,
  `FileCardPhotosTest.deleteUnnamed_…`, `exists_…` (a name that isn't a photo name is `false`, not a throw, #87), `delete_skipsANameThatIsntAPhotoName_…`; `CardFormViewModelTest.aPickedPhotoWhoseFileIsGone_isNotSaved`.
- `BackupRulesTest`: cloud backup excludes `card_photos/` on Android 12+ and 10–11; device transfer doesn't.
- `data/photos/FileCardPhotosTest` (native graphics, real pixels, #88): `import_dropsTheLocationAndTheOtherExif` (a
  fixture JPEG with GPS, make and date tags), `import_scalesALargePictureDown_toItsLongSideLimit`,
  `import_keepsTheSizeOfASmallerPicture`, `import_turnsThePictureTheWayItsExifOrientationSays` (all eight
  orientations), `import_putsATransparentPictureOnWhite`, `import_ofAFileThatIsntAPicture_throws_andLeavesNoFile`.
  Also: a picked picture is stored under a new name; an unreadable one, one that fails
  partway and a copy cancelled midway (a blocking stream through the `openPicture` lambda) leave no file;
  delete skips missing names, discard deletes in the background, a name can't leave the directory.
- `RoomLoyaltyCardRepositoryTest`: `update_deletesAReplacedOrRemovedPhoto_andKeepsTheOthers`,
  `update_keepingThePhotos_deletesNone`, `delete_deletesTheCardsPhotos`, `deleteAll_deletesThoseCardsPhotos_only`.
  `RoomStoreRepositoryTest.delete_deletesThePhotosOfTheStoresCards`, `delete_ofACatalogueStore_keepsItsCardsPhotos`.
  `LoyaltyCardDaoTest.updateReturningDroppedPhotos_…`, `deleteReturningPhotos_…`.

## Not implemented

Taking a photo with the camera, cropping to the card's ratio, and the rest of the backup rules (#71) are later parts of
#10.
