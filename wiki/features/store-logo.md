# Store logo

The user gives a store a logo of their own, and every card of that store shows it on the brand colour: on its tile
(unless the card has a front photo) and in the card screen's header. The app ships no logos and holds no links to
logo files ([[2026-11-12-no-bundled-store-logos]]); why it is stored the way it is:
[[2026-11-13-user-supplied-store-logos]]. Issue [#151](https://github.com/shockrifle/loyscan/issues/151).

**Done so far:** a store's logo screen, reached from the store picker for a catalogue store and from the edit form's
logo slot for a custom store (slice 3, [[edit-store]]), set from an image on the device (slice 1) or from a crop of
the front photo of one of the store's cards (slice 2). The new-store form has no slot (see the decision). **Not
implemented yet** (#151 stays open for it): an image shared to LoyScan as the source.

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `StorePickerScreen.kt`: a catalogue store's row has a trailing image icon, "Logo of <name>", in every section
  (Suggested, Frequently added, All stores) → `StorePickerDirections.toStoreLogo(storeId)` → `StoreLogo(storeId)`
  (`Navigation.kt`). A custom store's row keeps its Edit button ([[edit-store]]) and has no logo button: its edit
  form's **Change logo** → `CustomStoreDirections.toStoreLogo(storeId)` → the same `StoreLogo(storeId)`, over the form.
- `StoreLogoScreen.kt`, `StoreLogoViewModel.kt`: the screen and its state.
- `ui/LogoSearch.kt`: "Find a logo online".
- `data/repository/StoreRepository.kt` (`setLogo`), `data/photos/CardPhotos.kt` (`cropLogo`): storage, see the decision.

## Behaviour

- **The screen** ("Store logo"): a card-ratio preview of the store's tile, its logo at 60% on the brand colour, or its
  name when it has none or the logo can't load; "Every card of this store shows this logo."; **Choose image**,
  **Use a card's photo** while a card of the store has a front photo, **Find a logo online**, and **Remove logo** while
  it has one.
- **Choose image** opens the system photo picker (images only, no permission; the same `PicturePicker` as the card
  photos, [[card-photos]]). The pick is copied in at once (staged) and the crop screen opens on it with a square frame
  (`PhotoCropScreen(aspectRatio = 1f, zoomOutToFit = true)`). The picture can be zoomed out until it fits the square
  whole, so a wide wordmark is kept whole and the rest of the square is transparent; **Use whole image** keeps it
  uncropped; **Cancel** drops it. **Crop** stores the logo (`cropLogo`, a PNG) and sets it in one non-cancellable step,
  so leaving the screen meanwhile still sets it; the logo it replaces is deleted.
- While a picture is copied in or stored, or the logo saved or removed, a progress bar shows and the buttons wait. A
  picture that can't be copied in or stored, or a logo that can't be saved or removed, says "The picture couldn't be
  used. Try again."; a logo stored but not saved is deleted. The staged picture's name is in the `SavedStateHandle`
  (`cropLogo`), so the crop screen survives process death; one never framed is swept with the photo orphans.
- **Use a card's photo** lists the front photos of the store's cards whose files are there (`StoreLogoState.cardPhotos`,
  each titled with the card's label, or its number). With one, the button opens the crop on it at once; with several,
  "Which card's photo?" lists them with a thumbnail, and the one tapped opens the crop; **Cancel** closes it. The photo
  is copied in (`CardPhotos.stagePhoto`), so the crop works on the copy and the card keeps its own photo. The dialog
  closes when its list empties meanwhile; a photo no longer on the list, or one that can't be copied, says "The
  picture couldn't be used. Try again.". Its open state is `rememberSaveable`, and it closes for an empty list only once
  the list has loaded (`cardPhotosLoaded`), so after process death it doesn't close before the cards come.
- **Find a logo online** opens a Google Images search for "<store name> logo" in the browser (`logoSearchUri`), the
  user's choice on 2026-11-13; a setting for the search engine is not implemented. With no app to open it, a toast
  says "No app can open the web search.". The user saves an image there and picks it with Choose image.
- **A picture in the route** (`StoreLogo.picture`, a staged name; for a picture shared to LoyScan): the screen opens
  the square crop on it once (`routePictureTaken` in the `SavedStateHandle`), so Cancel, Crop or process death
  doesn't bring it back. For a store that is gone it is discarded; one whose file is no longer there opens nothing.
- **Remove logo** clears it; the name on colour comes back and its file is deleted.
- **A store that is gone** says "This store is no longer available." and offers nothing.
- **A logo whose file is missing** is no logo, here, on the tiles and in the header (`CardPhotos.withoutMissing`).
- **Language:** every text is a string resource in English and Hungarian ("Bolt logója", "Kép kiválasztása",
  "Logó keresése az interneten", "Az egész kép").

## Tests

- `StoreLogoViewModelTest`: starts as the store with its logo; a missing file is no logo; a store that is gone; pick →
  crop → logo; a cancelled pick; cancelling the crop; working while copying in; a copy, a crop or a save that fails
  (the stored logo deleted); a save still running when the screen closes; the crop restored after process death;
  removing, and a failed remove; another store left alone; the card photos offered (this store's front photos only, a
  missing file left out, a card's number for one without a label); a card's photo → a copy → crop → logo; a photo
  not on the list refused; a copy that fails; working while copying it; a picture in the route → the crop → the logo,
  cancelled and not offered again, still in the crop after process death, not offered again once cropped, discarded
  for a store that is gone, nothing for a missing file.
- `StoreLogoScreenTest`: the preview with and without a logo; Choose image → a square frame → Crop sets it; a wide
  picture (`writeWidePng`, 2×1) pinched out is cropped whole (`top` −0.5, `bottom` 1.5); Use whole image and Cancel;
  Find a logo online; Remove logo; a failure; the buttons waiting; a store that is gone; Back; Hungarian (the "Which
  card's photo?" dialog open); font scale 2 (the screen with the card photo button; the dialog's own layout isn't
  measured, as `assertTextFitsAndDoesNotOverlap` reads one window). Use a card's photo: no button without one; one card →
  straight to the square crop; several → the dialog, the choice cropped; Cancel; the dialog closing when the list empties;
  the dialog still open after process death when the store loads before the cards (`StateRestorationTester`); a
  picture in the route opens the square crop.
- From a custom store's form: `CustomStoreViewModelTest` (`editingAStore_showsItsLogo`, `aLogoWhoseFileIsMissing_…`,
  `aLogoSetWhileTheFormIsOpen_isShown`, `aNewStore_hasNoLogo`, `savingAnEdit_keepsTheStoresLogo`),
  `CustomStoreScreenTest` (the slot with and without a logo, Change logo, none on the new-store form or for a store
  that is gone, Hungarian, font scale 2), `AddCardDirectionsTest.aCustomStoresLogoButton_…`,
  `aSecondTapOnTheFormsLogoButton_…`, `AppNavigationTest.aCustomStoresLogoScreen_opensFromItsForm_…`.
- `StorePickerScreenTest.aCatalogueStoresLogoButton_…`, `aFrequentlyAddedStore_hasItsLogoButtonInItsSectionToo`;
  `AddCardDirectionsTest.aStoresLogoButton_opensItsLogoScreenOverThePicker_andBackReturnsToIt`;
  end to end `AppNavigationTest.aCatalogueStoresLogoScreen_opensFromThePicker_andBackReturnsToIt`.
- `ui/LogoSearchTest`; the storage, sync, tile and header tests are listed on [[card-photos]], [[store-catalogue]],
  [[card-list]] and [[card-detail]].
