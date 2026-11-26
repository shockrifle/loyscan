# Add card

The list's "Add card" button starts adding a card: pick the store, scan its barcode (or enter the number by hand),
check the number and give the card a label, then save it. The new card opens and offers to add another. Issue
[#6](https://github.com/shockrifle/loyscan/issues/6), done 2026-10-30. A picture shared to LoyScan starts the same flow
with the picture's barcode (#77, below). Everything runs offline on the local database.

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `StorePickerScreen.kt`, `StorePickerViewModel.kt`; the search field and list in `StoreList.kt` (`StoreSearchList`,
  shared with the edit form's "Change store", [[edit-card]]); search and `frequentlyAddedIn` in
  `domain/StoreSearch.kt`; the country in `data/device/UserCountry.kt` (bound in `di/UserCountryModule.kt`).
- `CustomStoreScreen.kt`, `CustomStoreViewModel.kt` ("Other card"); formats in `BarcodeFormat.forCustomStores`.
- [[barcode-scanner]] (`ScannerScreen.kt`), unchanged except for where its directions lead.
- `CardFormScreen.kt`, `CardFormViewModel.kt` (`CardFormState`).
- Routes and directions in `Navigation.kt`: `StorePicker(scannedContent?, scannedFormat?, manualEntry, logoPicture?)`,
  `CustomStore(storeId?, scannedContent?, scannedFormat?, manualEntry)`, `Scanner(storeId)`,
  `CardForm(storeId, scannedContent?, scannedFormat?)`; `ListDirections.toAddCard` starts the flow. Saving uses
  `LoyaltyCardRepository.add`.
- Share → LoyScan (#77): `Intent.sharedPicture()` and the share handling in `MainActivity.kt` (`onCreate`'s
  `sharedPictures`, `forwardToATaskOfItsOwn`);
  `SharedPictureScreen.kt`, `SharedPictureViewModel.kt`; route `SharedPicture(uri)`, `openSharedPicture` and
  `sharedPictureDirections` in `Navigation.kt`.

## Behaviour

- **Store picker.** Every store in the database (catalogue and custom), sorted by
  name. The search field narrows it to stores whose name or an alias contains the text, ignoring case and accents
  (`StoreSearch.search`: NFD, combining marks removed), so "mobelix" finds "Möbelix". Sorting uses the same key. With
  no match the screen says "No store matches “…”". Tapping a store opens the scanner for it. "Other card", first in
  the list and shown whatever the search, opens the custom-store form. Each store's row starts with its **badge**
  (`StoreBadge` in `StoreList.kt`, #186): a 40dp circle in the brand colour with the store's logo fitted inside
  ([[store-logo]]), drawn the way the user chose for the store (#240; test:
  `StorePickerScreenTest.aLogoInSingleColour_isDrawnInItsColour_inTheBadge`), else its initials, as the My cards tile shows a store; the owner's choice on 2026-11-24, in place
  of the colour dot. A logo that can't be loaded or whose file is missing shows the initials (the image loader's error,
  not `CardPhotos.withoutMissing`); while it loads, only the colour. Every row has the badge, so names line up. The
  initials are kept out of the row's merged semantics (`clearAndSetSemantics`), so TalkBack still reads the row as
  the store's name. The rows are shared with the card form's "Change store" dialog ([[edit-card]]), which shows the
  badges too. A custom store's row has an Edit button
  ([[edit-store]]); a catalogue store's, in every section, a logo button ([[store-logo]], #151). While nothing is searched, "Frequently added" (step 1 of #6,
  [[2026-10-30-frequently-added-stores]]) lists the catalogue stores flagged `frequentlyAdded` whose `countryCodes`
  contain the user's country (`UserCountry`: SIM, then network, then locale), by name, between "Other card" and an
  "All stores" heading; they stay in the full list too. With none for the country, neither heading is shown.
- **Logo mode** (#151, [[store-logo]]): `StorePicker(logoPicture = <staged name>)` asks "Logo for which store?" for a
  picture shared to LoyScan as a store logo (`StorePickerViewModel.logoMode`). It lists the same stores and sections,
  without "Other card" and without the rows' Edit and logo buttons. A tap on a store replaces the picker with that
  store's logo screen, `StoreLogo(storeId, picture)` (`storePickerDirections`), so Back from there returns to the list.
  Tests: `StorePickerViewModelTest.aPictureForALogo_…`, `StorePickerScreenTest.inLogoMode_…`,
  `inHungarian_theLogoModesTitle_isTranslated`, `AddCardDirectionsTest.aStoreTappedInLogoMode_…`,
  `aSecondTapOnAStoreInLogoMode_opensOneLogoScreen`.
- **Language** (#72): every text on the store picker, its search, the scanner ([[barcode-scanner]]), the shared
  picture screen (`SharedPictureScreenTest.inHungarian_…`: reading, no barcode, an unreadable picture), the card form
  (its problem messages, photo slots and "Choose store" dialog) and the photo crop screen is a string resource, in
  English or Hungarian by the phone's language; store names are data and aren't translated ([[add-a-string]]). Tests:
  `StorePickerScreenTest.inHungarian_everyTextOnThePicker_isTranslated`, `CardFormScreenTest.inHungarian_…`
  (`everyTextOnTheNewCardForm_isTranslated` walks the number checks, the photo menu, the crop screen and a failed
  save; one small test per other state).
- **Custom store** ([[2026-10-22-custom-stores]]): a name (required, trimmed), one of eight colours (the first picked
  by default) and the barcode format for the store's typed numbers (Code 128 by default; the 13 formats of
  `BarcodeFormat.forCustomStores`, no GS1 or DataBar, #47). A scan keeps its own format. "Create" saves the store once
  (`StoreRepository.add`: UUID id, `isCustom = true`, no rule); a failed save says "The store couldn't be saved. Try
  again." and leaves the form open, as a failed card save does. The scanner for the new store then replaces the form,
  so back returns to the picker, which now lists the store. Back during "Create" leaves the user on the picker, and
  the store is still created ([[edit-store]], #65). The rest of the flow is the same as for any store; with no
  rule, any number is valid and a scan another store's rule reads gets that store as a hint. The fields survive
  process death.
- **Scanner → form.** A scan (from the camera or a picked picture, #76), or "Enter manually", *replaces* the scanner with the form (`popUpTo<Scanner>
  { inclusive = true }`), so back from the form returns to the picker, not to a scanner that has already handed on
  its one scan.
- **Number field.** A scan fills it with the number the card will show (`LoyaltyCard.displayNumber` of the scan:
  the core the store's rule recovers, formatted; otherwise the scanned content as text), and the form says
  "Scanned · <format>". While the field is untouched the card is saved as the scan: `number` = the scanned content
  verbatim, `scannedFormat` = its format ([[2026-10-12-data-model-v2]]: a scan is drawn exactly as scanned). Any edit
  makes it a typed number. That is only safe when the field holds something the user can edit into a number: the
  core the rule reads back, or the content as it is. A scan shown otherwise (GS1 data as printed, `(01)…(10)…`, or
  control characters as spaces) is **read-only**, with "Type the number instead", which empties the field; editing
  that rendering would have saved it, brackets and all, as the barcode (found in review). A typed number: `scannedFormat` is dropped and the barcode is built from the typed number by the store's
  rule, the same rule #8 asks of editing. A typed number is stored as typed (trimmed). For a typed number the form
  says "Barcode: <format>" (the rule's, else the store's default).
- **Live check** against the store's card-number rule ([[2026-10-09-card-number-rule-engine]]), on every change:
  - typed: `CardNumberEngine.validate`; each error has a message (too short, too long, characters the store doesn't
    use, wrong start, check digit). An empty field can't be saved. A store without a rule accepts any number its
    barcode format can hold as it is; one it can't (`BarcodeEncoder.holdsAsIs`: it would fall back, or an EAN/UPC
    number one digit short would get a computed check digit) gets "This isn't a valid <format>
    number." (`NumberCheck.DoesNotFitFormat`; found in review of the custom-store slice).
  - untouched scan: `CardNumberEngine.match`; if the rule can't have produced the content, "This barcode doesn't
    look like a <store> card." `RuleMatch.formatMatches` is ignored, so a scan whose symbology differs from the
    rule's isn't flagged; a GS1 scan and a plain rule of the same symbology agree anyway (#47). A store without a rule
    accepts any scan.
  - **Another store** (step 5 of #6, [[2026-10-22-store-suggestion-trigger]]): for an untouched scan, the form lists
    other stores under "It looks like a card from:": every store whose rule reads the scan when the picked store has
    no rule or its rule rejects the scan; the stores whose rule names the scan's GS1 format when the scan has the
    GS1 mark and the picked rule names the same symbology without it; none otherwise (the picked rule reads it in the
    exact format, without the mark the rule has, or in another symbology), and none for a typed number. Ranked by `matchingScan` in `domain/StoreSearch.kt`: exact format, then the same
    symbology with or without the GS1 mark, then the rest, each by name. The exact format ranks first because
    a loose rule can read another store's barcode by symbology: until 2026-10-30 SuperShop's rule (any 16 digits,
    Code 128) read a Tesco GS1-128 barcode (found in review). Since it requires a `20000101` or `20170001` start (and reads QR codes) it doesn't
    (`ShippedCatalogueTest.aTescoScan_isNotOfferedAsSuperShop`, #52). Every match is offered, so the user chooses
    when several rules read the same number. Stores without a rule are never offered. Tapping one makes the form that store's
    (`CardFormViewModel.switchStore`): the scan and the label stay, the check runs against the new rule, and the card
    is saved for it; not once saving has started ([[edit-card]]). The chosen store is kept in the `SavedStateHandle`,
    so back still returns to the picker and the choice survives process death. The form reads every store for this (`StoreRepository.getAll`).
  - **"Save anyway":** with a problem shown, the button says "Save anyway" and saves the card as it is.
  - The field isn't reformatted while typing. When the card will show the number differently (the rule's display
    pattern), the field says "Shown as 1234 5674" under it.
- **Label** is optional; blank is stored as `null`.
- **Note** (#9) is optional free text under the label, several lines (e.g. a PIN hint, a membership tier, an expiry
  date). It is trimmed, and blank is stored as `null`. The detail screen shows it ([[card-detail]]); the list's search
  finds it ([[card-list]]). Tests: `CardFormViewModelTest.aNote_isSavedWithANewCard_trimmed`, `aBlankNote_isSavedAsNull`.
- **Save** writes the card once (a second tap does nothing): a new UUID, `createdAt` = `updatedAt` = now from the
  injected `Clock`, no use yet. The UUID is fixed when the form is first created and kept in its saved state (key
  `newCardId`), and a new card's save adds it only when no card has that id yet, else updates it (#99): a form restored
  after its activity was destroyed while its save ran would otherwise save the card a second time, since the state
  it is restored from was saved before that save ended ([[2026-11-11-restored-form-saved-a-duplicate]]). A restored
  form looks unsaved; its Save writes any later change to the same card and opens it. Then the card's detail opens in place of the flow (`popUpTo<CardList>()`), so back from it
  returns to the list. That opening isn't counted as a use (`Detail.countUse = false`): it is the end of adding the
  card, not showing it at a till.
- **Add another** (step 6 of #6; a snackbar was the product choice, 2026-10-29): the new card opens with
  `Detail.added = true` (set by `cardFormDirections` when the form's route has no `cardId`, so not after an edit), and
  says "Card added" in a snackbar with an "Add another" action (`SnackbarDuration.Long`; Material's `SnackbarHost`
  lengthens it when accessibility services are on). The action opens the store picker over the card
  (`DetailDirections.toAddCard`, `whileCurrent`), so back from the picker returns to the new card, and saving the next
  card replaces the whole stack above the list as usual. The offer is made once: a `rememberSaveable` flag keeps it
  while it shows, so a rotation shows it again, and drops it when the snackbar ends (timed out or used) or Edit is
  tapped, so it isn't made again on return from the picker or the form (Edit: found in review; leaving composition
  cancels the snackbar before it ends). Scan mode covers the snackbar, so the offer waits while scan mode is open and
  is made afresh when it closes (found in review: it timed out unseen).
- **Back with changes asks first** (#209): Back (system, gesture or the top bar's arrow) on a form whose values differ
  from what it opened with (the routed store and scan, nothing typed) shows "Discard this card?" with **Discard** and
  the form's own **Save** / **Save anyway**, or **Discard** and **Keep editing** when it can't be saved; Back or a tap
  outside keeps editing. Back straight after a scan leaves without asking. Discard deletes the photos picked in the
  form (`CardFormViewModel.discard`). Back while cropping still cancels the crop. Why the opening state is derived:
  [[2026-11-26-unsaved-changes-on-back]]. Tests: `CardFormViewModelTest.anUntouchedForm_hasNoChanges`,
  `addingACard_anythingTypedIsAChange_butTheRoutedScanIsNot`, `aChange_isStillAChange_afterProcessDeath_andARevertedOneIsNot`,
  `discarding_aFormRestoredWhileItsSaveRan_keepsThePhotoThatSaveWasHanded`;
  `CardFormScreenTest.addingACard_theBackArrowWithAChange_asksToDiscardTheCard`, `tappingOutsideTheDialog_keepsEditing`,
  `aValueTypedAndDeleted_orAScanLeftAsItIs_leavesWithoutAsking`, `backWhileCroppingAChangedForm_cancelsTheCrop_withoutAsking`,
  `aNumberThatFailsItsCheck_isSavedAnywayFromTheDialog`, `aFormThatCantBeSaved_offersDiscardAndKeepEditing`,
  `discard_leaves_andDeletesThePhotoPickedInTheForm`.
- **Back during the save** (#64): the user stays where Back took them, and the card is still saved.
  `CardFormDirections.toCard` navigates only while the form's own back-stack entry (`cardFormDirections(entry)`) is
  current, so not after Back, nor over a form opened since (Edit again while the first one animates out; found in
  review). The form's
  `LaunchedEffect` on `savedCardId` is still composed while the popped form animates out, and before the fix it opened
  the card from the picker. The check is on the back stack, not on the entry's lifecycle, so a save that ends while the
  app is in the background still opens the card. `save` writes in `withContext(NonCancellable)`: once the popped
  entry is destroyed its `viewModelScope` is cancelled, which would drop a write still waiting to run.
- **A failed save** (`LoyaltyCardRepository.add` throws, e.g. the database is full or can't be written) leaves the
  form open with "The card couldn't be saved. Try again." above the button, which saves again. Cancellation isn't
  caught. Before #49 the failure crashed the app. Every exception is caught, so a bug that makes every save fail
  shows the message instead of crashing; it is logged with Timber, which logs nothing in release builds.
- **A store deleted while the form is open** (a custom store, [[edit-store]]; #51): once the stores have loaded and
  none has the form's id, the form says "This store is no longer available." with no fields and no Save
  (`CardFormState.storeGone`). Tests: `CardFormViewModelTest.aStoreRemovedWhileTheFormIsOpen_…`,
  `CardFormScreenTest.aStoreRemovedWhileTheFormIsOpen_…`. The store picker sits below the form on the back stack, so
  today only a stale route reaches this.
- Front and back photos of the card can be taken with the camera app or added from the photo picker, then framed to the
  card's ratio in a crop screen ([[card-photos]], #10).
- Label, note, typed number, picked photos, a photo waiting in the crop screen and a switched store are kept in the
  `SavedStateHandle`, so they survive process death.
- `onNumberChange` (the read-only guard) and `save` read `state.value`, so both refuse until the store has loaded:
  the initial state is editable, and the stores come from Room after construction. `state` is started `Eagerly` so it
  stays current when nothing observes it (#50; the pre-load gap was found in review).
- The same form edits a card (`CardForm.cardId`, title "Edit card"): [[edit-card]].

- **Share → LoyScan** (#77). LoyScan is in the system share sheet for one image (`ACTION_SEND`, `image/*`; not
  `SEND_MULTIPLE`). A second entry, "Set as store logo", sets a store's logo instead ([[store-logo]], #151). A share through the chooser, or sent with `NEW_TASK` and/or `SINGLE_TOP`, doesn't replace what
  LoyScan has open: a share starts `ShareActivity` (the same app as `MainActivity`), in a task of its own whatever
  flags its sender sets (`documentLaunchMode="always"`) and with no task affinity, so the launcher and the widget never
  reach it either ([[2026-11-25-launcher-stacked-on-a-share-task]], #189); one that still reaches an open activity
  through `onNewIntent` is passed on to a new task ([[2026-11-03-share-in-standard-launch-mode]], #83). On a device, every share through the system chooser started a
  new LoyScan task, and Back from it returned to the sharing app
  ([[2026-11-03-share-into-the-sharing-apps-task]] → Device check). Only a
  `content://` URI is taken (a `file://` one could name the app's own files), and only the picture granted with the share is read, with the scanner's
  `PictureReader` ([[barcode-scanner]] → Scan from image): not copied, stored or sent. Steps:
  - `SharedPicture(uri)` opens over the card list, in place of anything open above it, and says "Reading the
    picture…". Its barcode replaces it with the store picker holding the scan (`StorePicker(scannedContent,
    scannedFormat)`), so back from the picker returns to the list.
  - No barcode, or a picture that can't be opened, says so with "Enter the number by hand", which opens the picker
    with `manualEntry = true`. Back returns to the list.
  - From that picker, a store opens its card form directly, with the scan or empty (`addCardStep` in
    `Navigation.kt`), not the scanner. While nothing is searched, the picker lists the stores whose card-number rule
    reads the barcode under "Suggested", above "Frequently added", ranked as the card form ranks the stores it offers
    (`matchingScan`: exact format, then the same symbology with or without the GS1 mark, then the rest, each by name;
    stores without a rule never). Nothing is picked for the user, and they stay in the full list; with none, there is
    no "Suggested" heading. SuperShop's and Auchan's rules, once loose enough to show up as
    false suggestions, now require a confirmed start (`20000101`/`20170001`, `2097`; #52, #31). The form after a store is the usual one, so its
    "It looks like a card from:" works as after a camera scan ([[2026-10-22-store-suggestion-trigger]]). "Other card"
    carries the scan on: the created store's form opens instead of its scanner. Editing a custom store from that
    picker doesn't carry it.
  - Rotation and process death: the scan lives in the picker's route, and what the read found is kept in the
    `SharedPicture` entry's `SavedStateHandle`, so the picture isn't read again (a read still running at process death
    is read again). `ShareActivity` (as `MainActivity`) opens the flow only on a fresh `onCreate`, and ignores a share reopened from
    recents, so a recreated activity doesn't start the flow again.
  - Checked on a device (Android 15, 2026-11-03, #84), with card pictures generated with ZXing core (a mock store
    app layout, not a screenshot of a store's own app): an EAN-13 card shared from Google Photos opened the picker with
    Auchan under "Suggested", then the form with the number and "Scanned · EAN-13"; saving opened the card. The same
    from the screenshot preview (a system screenshot of the picture in Google Photos): "Suggested", the form, saved. A
    QR picture shared from the screenshot preview opened the form with "Scanned · QR code" (no "Suggested": no rule
    reads its number). A picture without a barcode, shared from the
    ASUS Gallery, said "No barcode was found in that picture…" with "Enter the number by hand".

## Tests

- `domain/StoreSearchTest`: matching (name, alias, case, accents) and order; stores whose rule reads a scan, incl. a
  GS1 scan against a plain rule and the reverse (#47).
- `StorePickerViewModelTest`, `StorePickerScreenTest` (incl. "Other card" and "Frequently added": the user's
  country's flagged stores by name, hidden while searching or when there are none); `data/device/DeviceUserCountryTest`
  (SIM, network, locale, none; Robolectric's `ShadowTelephonyManager` and locale qualifiers).
- The badge (#186): `StorePickerScreenTest.aStoreWithALogo_showsItInItsRow_inEverySection` (Suggested, Frequently added,
  All stores), `…_showsItInSearchResults`, `inLogoMode_aStoreWithALogo_showsItInItsRow`,
  `aStoreWithoutALogo_showsItsInitials_andNoImage`, `aStoreWhoseLogoFileIsMissing_…`,
  `storeNames_lineUp_whetherOrNotTheStoreHasALogo` (a guard: seen red with the badge dropped from a row without a
  logo), `aRowsTalkBackText_isTheStoreNameOnly_withOrWithoutALogo`; `CardFormScreenTest.theStoreChooser_showsAStoresLogoInItsRow`.
  The initials are in the unmerged tree, so the Hungarian test counts them as data and the font-scale test as
  auto-sized (`fitsByDesign`).
- `CustomStoreViewModelTest` (defaults, a blank name, what is saved, one save only, a failed save, process death),
  `CustomStoreScreenTest`; the format list in `domain/BarcodeFormatTest` and
  `BarcodeEncoderTest.everyFormatACustomStoreCanPick_isDrawnAsItself`; `RoomStoreRepositoryTest.add_…`.
- `CardFormViewModelTest`: checks for typed numbers and scans (incl. the GS1 case), editing a scan, which scans are
  read-only, what is saved (typed, untouched scan, edited scan), one save only, a failed save and saving again, an unknown
  store, restore after process death, the stores offered for a scan, and switching to one (kept after process death). The card saved is built from the same draft the state shows, so the two can't disagree. `CardFormScreenTest`: messages, "Save anyway", the scan line, another store offered and switched to, save → opens the card, a failed save says so.
- `AddCardDirectionsTest`: where each step's directions lead, incl. GS1 separators surviving the route, and a save
  ending after Back from the form (`aSaveThatEndsAfterBackFromTheForm_…`, #64).
- `CardFormViewModelTest.aSaveStillRunningWhenTheFormCloses_isStillSaved` (#64): a save held by
  `FakeLoyaltyCardRepository.addGate` is finished after the ViewModel is cleared.
- `CardFormViewModelTest.aSaveThatEndsAfterTheFormWasCleared_isNotSavedAgainByTheRestoredForm`,
  `aFormRestoredAfterItsCardWasSaved_savesLaterChangesToThatCard`, `twoAddForms_saveTwoCards` (#99): the restored
  form is built from a snapshot of the handle taken before the save, as the activity's saved state is.
- `DetailViewModelTest.opening_aCardJustAdded_countsNoUse`.
- `AppNavigationTest`: picker → scanner → back, adding a card by hand end to end, and for a new custom store;
  "Add another" → picker → back to the new card (`addingACard_offersAnother_…`).
- "Add another": `AddCardDirectionsTest` (`savingTheForm_…_offeringToAddAnother`, `addAnotherOnTheNewCard_…`,
  `aSecondTapOnAddAnother_…`; the edit tests check an edit's card has `added = false`), `DetailScreenTest`
  (`aCardJustAdded_…`, `aCardOpenedOtherwise_…`, `theOffer_…`, incl. after Edit and during scan mode).

- Share → LoyScan (#77): `ShareIntentTest` (the manifest's filter resolves one image and nothing else, `standard`,
  which intents are a shared picture, a share passed on to a new task, one without a grant dropped), `SharedPictureViewModelTest` (reading, each outcome, kept over process death),
  `SharedPictureScreenTest`, `StorePickerViewModelTest` and `StorePickerScreenTest` (`…SharedScan…`, `…Suggest…`:
  ranking, none without a scan or a reading rule, hidden while searching, above "Frequently added"),
  `AddCardDirectionsTest` (`aSharedPicture…`, `…ForASharedScan…`, `…TypedByHand…`), and
  `ShareToLoyScanTest`, end to end with `FakePictureReaderModule`: share → picker → form, back to the list, no barcode
  → by hand, a share through `onNewIntent` keeps a half-filled form and starts a LoyScan of its own (#83), recreating
  the activity.

## Not implemented

A custom store abandoned before its first card stays in the picker until it is deleted there ([[edit-store]]). A GS1 Aztec or PDF417 scan loses its GS1
mark when saved (#48).
