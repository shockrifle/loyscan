# Card photos are taken with the system camera app, not a camera screen of LoyScan's own

**Status:** Active · **Date:** 2026-11-03 · **Issue:** [#10](https://github.com/shockrifle/loyscan/issues/10)

## Context
#10 asks for a card photo to be taken with the camera as well as picked from the gallery. The app already uses CameraX
for the [[barcode-scanner]], and already declares the camera permission for it.

## Options considered
- **The system camera app** through `ACTION_IMAGE_CAPTURE` (`ActivityResultContracts.TakePicture`), writing into a file
  the app hands it through a `FileProvider`.
- **A capture screen of LoyScan's own**, CameraX `ImageCapture`. It could draw a card-shaped frame, which would help
  with cropping to the card's ratio (still open in #10), and needs no camera app and no `FileProvider`. But it is a
  new screen whose capture can't run under Robolectric, so it would go untested ([[tdd]]).

## Decision
The system camera app, chosen by the user on 2026-11-03. "Add … photo" and "Replace … photo" open a menu: "Take photo"
or "Choose from gallery" (also the user's choice, over two buttons per slot). How it works: [[card-photos]]; where the
file goes: [[2026-11-03-card-photos-as-private-files]].

## Why
- Less code, and the user gets their own camera's focus, flash and HDR.
- The ViewModel and the file handling stay the same as for a pick: a capture is imported like a picked picture.

## Consequences
- Because the app declares `CAMERA`, Android refuses `ACTION_IMAGE_CAPTURE` until it is granted, so "Take photo" asks
  for the permission like the scanner does ([[android-app]]).
- Without a camera app, or on Android 11+ without the `<queries>` entry, only the gallery is offered.
- Nothing guides the user to frame the card; cropping to the card's ratio is still open in #10.
- The process can die while the camera app is in front, so the capture is kept in the form's saved state.
