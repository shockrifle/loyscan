# Card photos

A card can carry photos of the physical card, front and back (#10). They are copied into app-private storage and
deleted with the card. Storage and deletion: [[2026-11-03-card-photos-as-private-files]].

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `data/photos/CardPhotos.kt`: `CardPhotos` (`stage(uri)`, `crop(name, crop)`, `delete(names)`, `newCapture()`),
  implemented by `FileCardPhotos` over `files/card_photos/` (`di/CardPhotosModule.kt`, which hands a capture to the
  camera app through the app's `FileProvider`).
- `data/photos/CardPhotoEncoding.kt`: `encodeCardPhoto` and `PhotoCrop`, how an imported picture is stored, framed to
  the card's ratio (#88, #10).
- `ui/PhotoCropScreen.kt`: `PhotoCropScreen`, the crop screen; `photoCrop`, `clampCropOffset`, `cropTransform`, its
  pure maths ([[2026-11-04-card-photos-interactive-crop]]).
- `DetailScreen.kt`: the photos under the note, `PhotoViewer`.
- `ui/PhotoCamera.kt`: `PhotoCamera`, `rememberPhotoCamera`, the system camera app and the camera permission it needs.
- `CardFormScreen.kt` (`PhotoSlot`, its Take photo / Choose from gallery menu, the crop overlay), `CardFormViewModel.kt`
  (`onPhotoPicked`, `startCapture`, `onPhotoTaken`, `confirmCrop`, `cancelCrop`, `removePhoto`, `onCleared`): adding,
  framing, replacing and removing photos while adding or editing a card.
- `ui/CardPhoto.kt`: `CardPhoto(name)`, the image model; `CardPhotoMapper` on the app's Coil loader
  (`di/ImageLoaderModule.kt`) maps it to its file; `CARD_ASPECT_RATIO`.
- `data/repository/RoomLoyaltyCardRepository.kt` (`update`, `delete`, `deleteAll`) and
  `data/repository/RoomStoreRepository.kt` (`delete`): delete the files whose names the row write dropped.

## Behaviour

- **In the card form** ([[add-card]], [[edit-card]]), under the note: a slot for the front and one for the back. An
  empty slot says "Add front photo" / "Add back photo". With a camera app on the phone it opens a menu: "Take photo"
  (the system camera app, below) or "Choose from gallery"; without one it opens the gallery at once. The gallery is the
  system photo picker (images only, no storage permission; the same `PicturePicker` as the scanner's "Scan from
  image", [[barcode-scanner]]). A slot with a photo shows it in the card's proportions (ID-1, cropped to fill) with
  Replace and Remove icons ("Replace front photo", "Remove front photo"); Replace opens the same menu. While a picture
  is copied in or framed, the slot shows a progress indicator, and neither Save nor another photo is possible. A picture that can't be copied (e.g. a cloud photo that won't download) says "The
  photo couldn't be added. Try again."; the next pick clears it. Once saving has started, photos can't be changed.
- **Framing a photo** ([[2026-11-04-card-photos-interactive-crop]]): every picked or taken picture opens full screen in
  the crop screen (`PhotoCropScreen`, `PHOTO_CROP_TAG`) behind a fixed card-ratio frame. The user pans and pinch-zooms
  the picture within it (the frame stays covered; up to 8× zoom). **Crop** stores the framed part, **Use whole photo**
  stores all of it, **Cancel** drops it and adds nothing. The picture is requested at `Size.ORIGINAL`, so the crop is
  framed from full resolution.
- **What is stored** (#88, #10): not the picked file itself but a JPEG at most 2048 px on its long side (a smaller
  picture keeps its size), cropped to the frame (or whole), turned the way its EXIF orientation says, a transparent
  picture on white, and with no EXIF (no location). A picked file that isn't a picture can't be added. Why:
  [[2026-11-03-card-photos-as-private-files]], [[2026-11-04-card-photos-interactive-crop]].
  The orientation is read with androidx's `ExifInterface` (`androidx.exifinterface`), not the platform's, which reads
  a PNG's or WebP's EXIF only from API 30: on Android 10 such a picture was stored unrotated (#92). Orientation of a
  HEIF picture is read through it too; not checked on a device (#93).
- **What the form keeps.** A pick is copied in at once (the picker's read grant ends with the process) and staged for
  the crop screen; the staged name and its side go into the `SavedStateHandle` (keys `cropPhoto` / `cropSide`), so the
  crop screen survives process death as a capture does. Once framed, the stored photo's name goes into the
  `SavedStateHandle` (keys `frontPhoto` / `backPhoto`; `""` means removed). A photo staged for the crop screen but never
  framed is left to the sweep, like an abandoned pick (#95). Replacing or removing a photo picked in this form deletes that copy at once. The edited card's own photo stays
  until the edit is saved, then the repository deletes it. A form closed without saving leaves the copies picked in
  it to the orphan sweep (Orphans): `onCleared` deletes nothing, because it also runs when the activity is destroyed
  with the form still on the back stack and its process alive ("Don't keep activities", a background kill), and the
  form restored from the same saved state still names those copies, so their files must survive (#95; the unanswered
  capture, below, has the same reason). A form closed while its save runs leaves them to the save; if that save then
  fails it keeps the picks too (for the same reason — the clear may be an activity destroy), and they go to the sweep.
- **Taking a photo** opens the system camera app (`ACTION_IMAGE_CAPTURE` through `ActivityResultContracts.TakePicture`,
  `ui/PhotoCamera.kt`, `rememberPhotoCamera`), not a camera screen of LoyScan's own. Because the manifest declares the
  camera permission for the scanner, Android refuses that intent until the permission is granted, so "Take photo" asks
  for it first and opens the camera once it is allowed ([[2026-11-03-card-photos-from-the-system-camera-app]]) (the target waiting on the dialog is saved over a rotation). A
  denial says "LoyScan needs the camera to take a photo of your card."; one for good (told apart as the scanner does,
  `cameraPermissionStatus`) says "Camera access is turned off for LoyScan. You can allow it in the app's settings." with
  "Open settings". The next photo taken or picked clears either. Whether there is a camera app is read with
  `resolveActivity`, which on Android 11+ sees it only through the manifest's `<queries>` ([[android-app]]).
- **A photo taken with the camera** goes through a capture file: `startCapture(side)` makes `<uuid>.jpg` in
  `files/card_photos/` (`CardPhotos.newCapture`) and returns its `content://<applicationId>.photos/…` Uri for the camera
  app to write. `onPhotoTaken(taken)` then opens the crop screen on the capture (it is a staged file already, so it is
  not copied again), which frames and stores it like a pick; a camera that took nothing deletes the capture. The
  capture's name, Uri and side are in the `SavedStateHandle` (`capture`, `captureUri`, `captureSide`), since the process
  may die while the camera app is in front. The form's `onCleared` leaves an unanswered capture alone: it also runs when
  the activity is destroyed behind the camera app while the process lives ("Don't keep activities"), and the restored
  form then opens the crop screen on it (`aPhotoTaken_opensTheCropScreen_whenTheFormWasClearedBehindTheCameraApp`). One
  never answered is swept (Orphans). The `.jpg` name gives the camera app an `image/jpeg` target. No capture starts
  while a photo is copied in or framed, or once saving has started (`startCapture` returns `null`).
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
- `CardFormViewModelTest`: `aPickedFrontPhoto_…` (pick → frame → store), `editingACard_startsWithItsPhotos_…`,
  `removingACardsPhoto_…`, `aPhotoPickedInTheForm_isDeleted_whenAnotherReplacesIt_orItIsRemoved`,
  `aFormClosedWithoutSaving_leavesItsPicksToTheSweep_…`, `aFormClearedWithoutClosing_keepsItsPicks_…` (#95),
  `aPhotoStagedForTheCropScreen_isRestoredAfterProcessDeath`, `cancellingTheCropScreen_dropsTheStagedPhoto_andAddsNone`,
  `aSavedForm_keepsItsPhotos_…`, `aSaveStillRunningWhenTheFormCloses_keepsItsPhoto`,
  `aSaveThatFailsAfterTheFormClosed_leavesItsPickToTheSweep_…` (#95), `aFailedSave_keepsThePhotos_…`, `whileAPhotoIsCopiedIn_…`,
  `aPictureThatCantBeCopiedIn_…`, `aCancelledPick_…`, `noPhotoCanBePickedOrRemoved_onceSavingHasStarted`,
  `aPickedPhoto_isRestoredAfterProcessDeath`. Camera captures: `aPhotoTaken_opensTheCropScreen_andIsStoredFramed_…`,
  `aCameraThatTookNoPhoto_…`, `aPhotoTakenThatCantBeStored_…`, `aPhotoTaken_opensTheCropScreen_afterProcessDeathWhileTheCameraWasOpen`,
  `noCaptureStarts_…`, `aPhotoTaken_opensTheCropScreen_whenTheFormWasClearedBehindTheCameraApp`. `CardFormScreenTest`: `addingAFrontPhoto_…`,
  `theCropScreen_framesAPhoto_toTheCardRatio` (a real staged file, so Crop runs over a loaded picture),
  `theCropScreen_canBeCancelled_withoutAddingAPhoto`, `editingACard_showsItsPhoto_whichCanBeReplacedOrRemoved`,
  `aPictureThatCantBeCopiedIn_saysSo`; the camera:
  `withACameraApp_addingAPhoto_offersTheCameraOrTheGallery`, `withoutACameraApp_addingAPhoto_opensTheGalleryAtOnce`,
  `takingAPhoto_opensTheCameraAppOnACapture_andShowsThePhotoTaken`, `replacingAPhoto_offersTheCameraToo`,
  `aCameraDenied_saysWhyItIsNeeded_untilTheNextPhoto`, `aCameraDeniedForGood_offersTheSettings`.
- The crop maths: `ui/PhotoCropTest` (`photoCrop`, `clampCropOffset`, `cropTransform`) — the framed rectangle, the pan
  limits and the zoom clamp, pure (the gesture itself isn't tested; Robolectric runs no Compose gestures).
- `ui/PhotoCameraTest` (the real glue, a test `ActivityResultRegistry` answering the dialog and the camera app):
  allowed → the camera app on the target; asked first, then opened; a denial, then one for good; a dialog answered
  after a rotation still opens the camera; available only with an `IMAGE_CAPTURE` activity. Not checked on a device:
  which camera apps answer, and a capture after the process died behind the camera app.
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
- `data/photos/FileCardPhotosTest` (native graphics, real pixels, #88). A stored photo goes through `stage` then `crop`
  (`importWhole` helper): `stored_dropsTheLocationAndTheOtherExif` (a fixture JPEG with GPS, make and date tags),
  `stored_scalesALargePictureDown_toItsLongSideLimit`, `stored_keepsTheSizeOfASmallerPicture`,
  `stored_turnsThePictureTheWayItsExifOrientationSays` (all eight orientations),
  `stored_onAndroid10_turnsAPngOrWebpTheWayItsExifOrientationSays` (`@Config(sdk = [29])`, #92),
  `stored_putsATransparentPictureOnWhite`. Cropping (#10): `crop_storesOnlyTheFramedPartOfTheUprightPicture_andDeletesTheStagedSource`,
  `crop_isInTheUprightPicture_evenWhenTheSourceIsRotated`, `crop_ofAStagedCapture_storesIt_andDeletesTheCapture`,
  `crop_ofANonPictureStagedFile_throws_andLeavesNoFile`. Staging: `stage_storesTheRawPickedBytes_…`,
  `stage_ofAFileThatIsntAPicture_…`, `stage_ofAPictureThatCantBeRead_…`, `stage_cancelledWhileCopying_…`,
  `stage_ofAPictureThatFailsPartway_…`. `newCapture_isAFileInThePhotoDirectory_…`, `aCapture_isDeletedByItsName`;
  through the real `FileProvider`: `di/CardPhotosModuleTest` (the write runs on CI only, see [[tdd]]).
  Also: delete skips missing names, discard deletes in the background, a name can't leave the directory.
- `RoomLoyaltyCardRepositoryTest`: `update_deletesAReplacedOrRemovedPhoto_andKeepsTheOthers`,
  `update_keepingThePhotos_deletesNone`, `delete_deletesTheCardsPhotos`, `deleteAll_deletesThoseCardsPhotos_only`.
  `RoomStoreRepositoryTest.delete_deletesThePhotosOfTheStoresCards`, `delete_ofACatalogueStore_keepsItsCardsPhotos`.
  `LoyaltyCardDaoTest.updateReturningDroppedPhotos_…`, `deleteReturningPhotos_…`.

## Not implemented

The rest of the backup rules — carrying the photos on Android 10–11 device-to-device transfer (#71) — are the last
part of #10. Cropping to the card's ratio is done (interactive crop screen, [[2026-11-04-card-photos-interactive-crop]]).
