# Store logo

The user gives a store a logo of their own, and every card of that store shows it on the brand colour: on its tile
(unless the card shows its front photo there, a per-card choice since #184, [[card-photos]]) and in the card screen's band (#210); the store's row in the store picker and the
card form's "Change store" dialog shows it too, in a round badge ([[add-card]], #186). A home-screen widget tile of the store shows it as well
([[card-widget]], #181). The app ships no logos and holds no links to
logo files ([[2026-11-12-no-bundled-store-logos]]); why it is stored the way it is:
[[2026-11-13-user-supplied-store-logos]]. Issue [#151](https://github.com/shockrifle/loyscan/issues/151).

A store's logo screen is reached from the store picker for a catalogue store and from the edit form's logo slot for a
custom store ([[edit-store]]; the new-store form has none, see the decision). The logo comes from an image on the
device, a crop of the front photo of one of the store's cards, or a picture shared to LoyScan through "Set as store
logo". Done in four slices, 2026-11-13 to 2026-11-16.

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `StorePickerScreen.kt`: a catalogue store's row has a trailing image icon, "Logo of <name>", in every section
  (Suggested, Frequently added, All stores) → `StorePickerDirections.toStoreLogo(storeId)` → `StoreLogo(storeId)`
  (`Navigation.kt`). A custom store's row keeps its Edit button ([[edit-store]]) and has no logo button: its edit
  form's **Change logo** → `CustomStoreDirections.toStoreLogo(storeId)` → the same `StoreLogo(storeId)`, over the form.
- Share → "Set as store logo": the `.ShareAsStoreLogo` activity-alias in `app/src/main/AndroidManifest.xml`,
  `Intent.sharesAsStoreLogo()` and the `sharedLogos` channel in `MainActivity.kt`; `SharedLogoScreen.kt`,
  `SharedLogoViewModel.kt`; `SharedLogo(uri)`, `openSharedLogo`, `sharedLogoDirections` in `Navigation.kt`; then the
  store picker's logo mode ([[add-card]]) and `StoreLogo(storeId, picture)`.
- `StoreLogoScreen.kt`, `StoreLogoViewModel.kt`: the screen and its state. Its preview, `LogoPreview`, is in
  `ui/LogoPreview.kt` and draws the list tile's face, `ui/CardTileFace.kt` (#192, #162), shared with the custom store form's logo slot.
- `ui/LogoSearch.kt`: "Find a logo online".
- How the logo is drawn (#240, [[2026-11-26-logo-treatments]]): `LogoStyleControls` and `LOGO_COLOURS` in
  `StoreLogoScreen.kt`, `StoreLogoViewModel.onLogoStyle` / `onLogoTint` / `onLogoOutline`, `StoreRepository.setLogoTreatment`; drawn by
  `ui/StoreLogoImage.kt` and `ui/LogoTreatments.kt` (`inSingleColour`, `recoloured`, `outlined`), and on the widget by `WidgetPictures`.
- `data/repository/StoreRepository.kt` (`setLogo`), `data/photos/CardPhotos.kt` (`cropLogo`, `logoPixels`): storage, see the decision.
- The contrast check on a new logo (#240): `StoreLogoViewModel.confirmCrop`, `preselectedTreatment` in `ui/LogoTreatments.kt`.

## Behaviour

- **The screen** ("Store logo"): a preview of the store's unlabelled tile, drawn by the My cards tile's own code
  (`CardTileFace`, #192), so the two can't drift apart: the logo at 60% of its height, centred, on the brand colour, or
  the store's initials when it has none or the logo can't load; never the store name, which a tile doesn't draw since
  #183 ([[card-list]]). It is full width, so the initials, which stop growing at a tile's size, look smaller on it than
  on a tile. TalkBack reads it as the store's name, as it reads a tile; "Every card of this store shows this logo."; **Choose image**,
  **Use a card's photo** while a card of the store has a front photo, **Find a logo online**, and **Remove logo** while
  it has one.
- **Choose image** opens the system photo picker (images only, no permission; the same `PicturePicker` as the card
  photos, [[card-photos]]). The pick is copied in at once (staged) and the crop screen opens on it with a square frame
  (`PhotoCropScreen(aspectRatio = 1f, zoomOutToFit = true)`). The picture can be zoomed out until it fits the square
  whole, so a wide wordmark is kept whole and the rest of the square is transparent; **Use whole image** keeps it
  uncropped; **Cancel** drops it. **Crop** stores the logo (`cropLogo`, a PNG) and sets it in one non-cancellable step,
  so leaving the screen meanwhile still sets it; the logo it replaces is deleted.
- **An SVG** (#211), where the picker offers one or shared through "Set as store logo", is drawn once as it is copied
  in (`CardPhotos.stageLogo`): a transparent PNG, 512 px on its long side, which the crop then frames like any picture.
  One that can't be drawn, or one over 5 MiB, says the picture couldn't be used. Nothing outside the file is read.
  How: [[androidsvg]]. No picker is pushed to list SVGs (the photo picker usually doesn't), and a card photo still
  can't be one.
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
- **A picture in the route** (`StoreLogo.picture`, a staged name; for a picture shared to LoyScan, chosen for a store in
  the store picker's logo mode, [[add-card]]): the screen opens
  the square crop on it once (`routePictureTaken` in the `SavedStateHandle`), so Cancel, Crop or process death
  doesn't bring it back. For a store that is gone it is discarded; one whose file is no longer there opens nothing; a
  picture the user picks while its file is checked keeps the crop, and the route's is discarded (PR #165 review).
- **A picture shared to LoyScan** (#151): the share sheet lists two LoyScan targets for one image, LoyScan itself
  (adds a card, [[add-card]], unchanged) and **Set as store logo**, an `activity-alias` of `ShareActivity` (#189) with the
  same `SEND image/*` filter, the owner's choice on 2026-11-16. The alias is the intent's component
  (`sharesAsStoreLogo`); a share forwarded to a LoyScan of its own (#83) keeps it. `SharedLogo(uri)` opens over the
  list ("Copying the picture…") and copies the picture in at once (`CardPhotos.stage`), while the share's read grant
  is surely held; the copy's name is in the `SavedStateHandle`, so it isn't made again after process death. A picture
  that can't be copied says "The picture couldn't be used.". The copy then goes to the store picker in logo mode,
  "Logo for which store?", any store, in place of `SharedLogo`; the store tapped opens its logo screen with the crop on
  the copy, in place of the picker, so Back returns to the list. A copy abandoned on the way (Back from the picker or
  the crop) stays in the photos' directory, which backup carries, until the photo orphan sweep deletes it, more than a
  day later ([[card-photos]]). Checked on an Android 14 device (2026-11-25, #189): the share sheet groups both targets
  under one "LoyScan" tile, which offers "LoyScan" and "Set as store logo" (a target used before also shows on its own
  in the direct row); the alias starts in a task of its own, and Back after the crop returns to the list, then to the
  sender. Reopening from recents is not checked.
- **How the logo is drawn** (#240): under the preview while the store has a logo, "How the logo is drawn"
  offers **As is** / **Single colour** / **Recolour** (segmented buttons), and unless As is, "Logo colour": a row of swatches, white,
  black, then the custom store palette, each read out by its colour's name as a choice; then an **Outline** switch
  ("Körvonal"), with any style, the whole row read out as a switch, on or off. Every tap is saved at once
  (`StoreRepository.setLogoTreatment`) and shows in the preview, the tiles, the card screen's band, the picker's badge,
  the custom store form's logo slot and the widget. Single colour and Recolour start in white or black, whichever contrasts more
  with the brand colour; As is keeps the Outline. Single colour draws every pixel in the colour,
  its opacity from its brightness, the solid end following the logo (dark parts of a mostly dark logo, light parts of a
  mostly light one), so the detail inside it stays visible ([[2026-11-26-logo-treatments]]). Recolour draws the pixels
  near the brand colour (CIE76 ΔE ≤ 25 in CIELAB: its shades, not another hue) in the colour, at their own opacity, and
  leaves the other colours alone; it compares with the colour the logo is drawn on, so a new brand colour redraws it. A change that can't be
  saved says "Couldn't save how the logo is drawn. Try again.". Removing the logo draws it As is again; editing a
  custom store's colour keeps the choice, and the colour chosen with it. The Outline is a ring around the logo's shape,
  4% of its longer side wide, in white or black, whichever contrasts more with the brand colour, so it follows a new
  one; an outlined logo draws about 8% smaller in its box, to fit its ring. A new logo has none.
- **A new logo is checked** (#240) as Crop saves it, in the same non-cancellable step: when more than half of it,
  each pixel weighed by its opacity and judged as it shows on the brand colour, has less than 3:1 contrast with the
  brand colour, it is saved Recoloured in white or black (whichever contrasts more), or in Single colour in that colour
  when the recoloured logo still blends in; As is otherwise. The screen then shows the saved choice. A logo whose
  pixels can't be read, or a brand colour that isn't one, is saved As is. Logos saved before are not checked again.
- **Remove logo** clears it; the initials on colour come back and its file is deleted.
- **A store that is gone** says "This store is no longer available." and offers nothing.
- **A logo whose file is missing** is no logo, here, on the tiles and in the card screen's band, which show the initials (`CardPhotos.withoutMissing`). The
  store picker's badge isn't checked that way: a missing file fails to load, and the badge shows the initials (#186).
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
  for a store that is gone, nothing for a missing file, a pick made meanwhile kept; a picked SVG
  (`aPickedSvg_opensTheCrop_andTheCropBecomesTheStoresLogo`, #211).
- The SVG drawing (#211): the `stageLogo_` tests in `FileCardPhotosTest` (the size, a viewBox only, no size at all, a very thin one,
  unreadable files, the size limit, a linked image not drawn beside an embedded one that is, entities drawn, an entity
  from another file, nested entities and the same in UTF-16 refused, a gzipped one drawn and checked unpacked, the IO dispatcher's thread),
  `cropLogo_ofAStagedSvg_storesTheUsualPng`, `stage_ofAnSvg_throws_andLeavesNoFile`;
  `SharedLogoViewModelTest.aSharedSvg_isCopiedIn_asALogo`, `CardFormViewModelTest.aPickedSvg_isNotACardPhoto_andSaysSo`;
  end to end `ShareToLoyScanTest.sharingAnSvgAsAStoreLogo_theCropMakesItItsLogo`.
- `StoreLogoScreenTest`: the preview with and without a logo (initials, not the name; no text with a logo; a logo that
  can't load shows the initials; the logo centred at 3/5 of its height; read out as the store's name; rounded corners,
  pixels, #192); Choose image → a square frame → Crop sets it; a wide
  picture (`writeWidePng`, 2×1) pinched out is cropped whole (`top` −0.5, `bottom` 1.5); Use whole image and Cancel;
  Find a logo online; Remove logo; a failure; the buttons waiting; a store that is gone; Back; Hungarian (the "Which
  card's photo?" dialog open); font scale 2 (the screen with the card photo button; the dialog's own layout isn't
  measured, as `assertTextFitsAndDoesNotOverlap` reads one window). Use a card's photo: no button without one; one card →
  straight to the square crop; several → the dialog, the choice cropped; Cancel; the dialog closing when the list empties;
  the dialog still open after process death when the store loads before the cards (`StateRestorationTester`); a
  picture in the route opens the square crop.
- The shared picture: `ShareIntentTest` (the alias resolves for one image and not for text; its intent shares as a
  store logo, the plain one doesn't; a forwarded one stays one), `SharedLogoViewModelTest` (copied once; nothing
  staged while copying; a failure; kept after process death without a second copy), `SharedLogoScreenTest` (copying,
  handed on once, a failure, Back, Hungarian), `AddCardDirectionsTest.aSharedLogo_…`, `aSharedLogoCopiedAfterBack_…`,
  end to end `ShareToLoyScanTest.sharingAPictureAsAStoreLogo_asksWhichStore_andTheCropMakesItItsLogo` and
  `recreatingTheActivity_keepsTheStoreQuestion_withoutCopyingThePictureAgain` (a real PNG served with
  `ShadowContentResolver.registerInputStream`, `@GraphicsMode(NATIVE)` on those methods only).
- From a custom store's form: `CustomStoreViewModelTest` (`editingAStore_showsItsLogo`, `aLogoWhoseFileIsMissing_…`,
  `aLogoSetWhileTheFormIsOpen_isShown`, `aNewStore_hasNoLogo`, `savingAnEdit_keepsTheStoresLogo`),
  `CustomStoreScreenTest` (the slot with and without a logo, Change logo, none on the new-store form or for a store
  that is gone, Hungarian, font scale 2), `AddCardDirectionsTest.aCustomStoresLogoButton_…`,
  `aSecondTapOnTheFormsLogoButton_…`, `AppNavigationTest.aCustomStoresLogoScreen_opensFromItsForm_…`.
- `StorePickerScreenTest.aCatalogueStoresLogoButton_…`, `aFrequentlyAddedStore_hasItsLogoButtonInItsSectionToo`;
  `AddCardDirectionsTest.aStoresLogoButton_opensItsLogoScreenOverThePicker_andBackReturnsToIt`;
  end to end `AppNavigationTest.aCatalogueStoresLogoScreen_opensFromThePicker_andBackReturnsToIt`.
- How the logo is drawn (#240): `StoreLogoViewModelTest` (Single colour white on a dark brand colour, black on a light
  one; a colour; As is keeping the outline; the Outline on and off, with a style and As is; a colour while As is, and a
  style or an Outline without a logo, not saved; a save that fails), `StoreLogoScreenTest` (`withoutALogo_offersNoWayToDrawIt`, `withALogo_drawnAsIs_offersTheStyles_butNoColours`,
  `choosingSingleColour_drawsTheLogoWhiteOnADarkColour_andOffersTheColours` (the swatches in order, read out as choices),
  `choosingRecolour_drawsItsBrandColouredPartWhiteOnADarkColour_andOffersTheColours`, `choosingAColour_drawsTheLogoInIt`, `choosingAsIs_hidesTheColours`, `aStyleThatCantBeSaved_saysSo`,
  `thePreview_drawsTheLogoAsChosen` (pixels), `withALogo_offersAnOutlineSwitch_readOutAsASwitch_off`,
  `turningOnTheOutline_savesIt_withAnyStyle`, `thePreview_drawsTheOutline` (its ring's pixel), and the Hungarian test), `ui/LogoTreatmentsTest` (the pixels, worked out by
  hand), `ui/LogoTreatmentBitmapTest`; storage in `RoomStoreRepositoryTest` and `CatalogueDatabaseCallbackTest`.
- The contrast check (#240): `ui/LogoTreatmentsTest` (`aLogoLegibleOnItsBrandColour_isDrawnAsIs`,
  `aLogoMostlyInItsBrandColour_isRecoloured_inTheContrastingColour`, `aLogoMostlyInAnotherColourJustAsDark_isInSingleColour`,
  `aLogoExactlyHalfInItsBrandColour_isDrawnAsIs`, `theCheck_weighsEachPixelByItsOpacity`,
  `aHalfClearWhitePixel_isJudgedAsItShowsOnTheBrandColour`, contrast ratios worked out by hand), `StoreLogoViewModelTest`
  (`aNewLogoInItsBrandColour_isSavedRecoloured`, `aNewLogoThatBlendsIn_evenRecoloured_isSavedInSingleColour`,
  `aNewLogoThatShows_replacingOneDrawnAnotherWay_isSavedAsIs`, `aNewLogoWhosePixelsCantBeRead_orOnABrandColourThatIsNotOne_isSavedAsIs`),
  `StoreLogoScreenTest.aNewLogoInTheBrandColour_isShownRecoloured`, `RoomStoreRepositoryTest.setLogo_aNewLogoWithATreatment_savesBoth`,
  `setLogo_aTreatmentForNoLogo_orForTheSameLogoAgain_isNotSaved`, and the `logoPixels_` tests ([[card-photos]]).
- `ui/LogoSearchTest`; the storage, sync, tile and band tests are listed on [[card-photos]], [[store-catalogue]],
  [[card-list]] and [[card-detail]].
