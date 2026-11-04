# Card photos are framed to the card's ratio in an interactive crop screen

**Status:** Active · **Date:** 2026-11-04 · **Issue:** [#10](https://github.com/shockrifle/loyscan/issues/10)

## Context
#10's last open UI item was "crop to card ratio". Before this, the card form slots, the list tile and the
detail thumbnails already crop a photo to fill a card-ratio box for display ([[card-photos]]), but the stored
JPEG kept the picture's own ratio, and the full-screen viewer ([[card-detail]], `PhotoViewer`) deliberately
showed the whole picture (`ContentScale.Fit`). So "crop to card ratio" meant something more than the display crop:
how the user frames what is stored.

## Options considered
- **Auto centre-crop on import:** `encodeCardPhoto` crops to `CARD_ASPECT_RATIO` centred, no UI. Contained, but
  it throws away everything outside the frame, mis-crops a card that isn't centred in the shot, and makes the
  whole-picture viewer redundant.
- **Interactive crop screen:** after each pick and each capture, the user pans and pinch-zooms the picture within
  a fixed card-ratio frame, then confirms. Truest to "crop to card ratio" and handles an off-centre card; a new
  screen with gestures. **Chosen.**
- **Keep the whole picture, treat the display crop as enough:** almost no code, but stores nothing framed.
- **Mandatory crop vs. offer "Use whole photo":** the crop screen also keeps the whole picture (confirm with the
  full-frame rect), so a user who shot more than the card isn't forced to lose it. **Offered**, at near-zero cost
  since the engine takes a crop rectangle anyway.

## Decision
- A **crop screen** (`ui/PhotoCropScreen.kt`, `PhotoCropScreen`) opens on every pick and every capture, before the
  photo is stored. A fixed `CARD_ASPECT_RATIO` frame; the picture is shown to cover it (`ContentScale.Crop`) and
  the user pans and pinch-zooms it (min zoom keeps the frame covered, max 8×). **Crop** stores the framed part,
  **Use whole photo** stores all of it, **Cancel** drops it.
- **Staging, so the source survives process death** (`CardPhotos.stage` / `CardPhotos.crop`,
  `data/photos/CardPhotos.kt`): a pick is copied into `card_photos/` as it is (a capture is written there by the
  camera app already, so it needs no copy), and its name and side are kept in the form's `SavedStateHandle`
  (`cropPhoto` / `cropSide`), as a capture's are. The crop screen shows the staged file; confirming re-encodes it
  to its framed, downscaled, metadata-free final form (`encodeCardPhoto`, now taking a `PhotoCrop`) and deletes
  the staged file. `onCleared` leaves a staged-but-unframed photo alone, for the same reason it leaves a pick or a
  capture ([[card-photos]], #95); an abandoned one is swept (Orphans).
- **The crop maths is pure** (`photoCrop`, `clampCropOffset`, `cropTransform` in `PhotoCropScreen.kt`): what rectangle
  a pan/zoom selects, the pan limits and the zoom clamp are plain functions, tested without a device
  (`ui/PhotoCropTest`), as the barcode drawing is ([[tdd]]). The gesture → crop path is also driven in a screen test
  (`CardFormScreenTest.theCropScreen_zoomAndPan_storeACardRatioPartOfThePicture`, two pointers through
  `performTouchInput`), though that doesn't reproduce the stale-size bug below, so it isn't a guard for it.
- **The gesture is keyed on the picture's size** (`pointerInput(image, …)`): the gesture lambda captures the picture's
  intrinsic size, which Coil reports only after the picture loads, so without the key the pan would be clamped against
  an unknown (NaN) size and never limited. Found in review. The fix is reasoned, not checked by a test: in Robolectric
  the picture loads before the gesture starts, so the bug doesn't reproduce there.
- The crop screen requests the picture at a bounded size (`MAX_PHOTO_SIDE`, 2048 px), not `Size.ORIGINAL`: a full-
  sensor camera photo decoded whole (hundreds of MB as ARGB) would OOM. The crop maths uses only the picture's ratio,
  so a downscaled display is fine, and the frame is a normalised rectangle `encodeCardPhoto` applies to the stored
  file. That file's own decode picks `inSampleSize` from the whole picture's long side (not the crop region), so a
  deep-zoom crop is sampled from that and loses some resolution; a region decode at full resolution is a possible
  later improvement.

## Why
- The user frames the card, so an off-centre or partly-in-frame card is kept properly, which a centre-crop can't.
- Centre-cropping on import would also make the whole-picture viewer pointless and silently discard the rest of
  the photo; "Use whole photo" keeps that choice for the user at almost no cost.
- Staging rather than cropping from the pick's Uri keeps the #95 process-death guarantee: the picker's read grant
  ends with the process, so a pick that isn't copied in couldn't be re-read after a background kill. A capture is
  already a file, so both sources become a staged file the crop screen and a restart can find.

## Consequences
- The crop maths, the gesture → crop path, and the screen's wiring (Crop / Use whole photo / Cancel) are tested. Not
  reproducible in Robolectric, so reasoned only: the stale-size gesture fix (above). Not checked on a device: how the
  frame feels, and the gesture on a real high-resolution picture.
- The crop screen is laid out for portrait and is not usable in landscape: the frame is sized from the width, so in
  landscape it overflows the height and the stored crop would not match what is shown. The app doesn't lock
  orientation. Left as a follow-up (fit the frame to the region, or lock the screen to portrait).
- A photo staged for the crop screen but never framed lingers like an abandoned pick — up to a day plus the next
  process start — before the sweep deletes it ([[2026-11-03-card-photos-as-private-files]]).
- `CardPhotos.import` (copy-and-encode in one) is gone; the form stages then crops. `encodeCardPhoto` keeps the
  whole picture by default (`PhotoCrop.WHOLE`), so #88's downscale/orientation/metadata behaviour is unchanged for
  an un-cropped store.
