# Barcode scanner

A full-screen camera view that reads a card's barcode and hands on the first scan: its content exactly as decoded,
and its format. Offline, without Play Services ([[2026-10-16-zxing-cpp-for-scanning]], [[zxing-cpp]]). Until the
add-card flow ([#6](https://github.com/shockrifle/loyscan/issues/6)) exists, the list's "Add card" button opens it
directly and a scan is only shown in a dialog: [[scanner-result-dialog-until-add-card-flow]].

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `ScannerScreen.kt:46`, `ScannerScreen`; state in `ScannerViewModel.kt`.
- Route `Scanner`, `ScannerDirections` and `scannerDirections()` in `Navigation.kt:41-63`; registered in
  `MainActivity.kt:69-74`.
- `ui/scanner/`: `CameraPermission.kt` (permission state), `CameraScanner.kt` (CameraX, `ScanAnalyzer`),
  `ZxingCppFrameReader.kt` and `ZxingCppFormats.kt` (decoding).

## Behaviour

- **Permission.** On entry, if the camera permission was never asked for on this visit, the system dialog opens
  (`ScannerScreen.kt:54`). While it isn't granted, the screen explains why the camera is needed, with "Allow camera"
  (asks again) or, once the system won't ask again, "Open settings" (the app's details page). The status is read
  again on every resume, so allowing it in the settings and coming back shows the camera. Which denial is which
  comes from `shouldShowRequestPermissionRationale` after a request (`cameraPermissionStatus`).
- **Camera.** The back camera, bound to the destination's lifecycle: it stops on pause and when the screen is left.
  If it can't be opened (no back camera, CameraX failure, or zxing-cpp's native library missing for the ABI), the
  screen says so and keeps "Enter manually".
- **Scan.** Each analysis frame (about 1280×720) is read on a background thread; the first barcode found is handed
  to `directions.onScanned` on the main thread. Later frames keep being read, but `ScannerViewModel.onScanned`
  drops them, so one scan navigates once.
- **Torch.** A toggle in the top bar, only when the camera has a flash unit. Its state is in the ViewModel, so it
  survives rotation.
- **Hint.** 8 s after the camera first starts (`ScannerViewModel.HINT_DELAY`), a card says "Card won't scan?" with
  tips. A camera restart (rotation) doesn't reset the timer.
- **Enter manually** is always at the bottom, with or without the camera.

## Tests

- `ScannerScreenTest`: the screen with a fake camera (a `ScannerCamera` lambda that records torch and events) and
  `FakeCameraPermission`. Covers each permission state, the first-scan-only rule, torch, hint, camera failure.
- `ScannerViewModelTest`: hint timing with virtual time (`ScannerViewModel(hintDelay)`), torch, one scan.
- `CameraXScannerTest`: the camera lifecycle with a fake `CameraOpener` (open, torch, close on leave, failures).
- `ScanAnalyzerTest`: frames closed, barcodes delivered on the callback executor, unreadable frames skipped.
- `CameraPermissionTest`: the real permission state, with the system dialog replaced by an `ActivityResultRegistry`
  that answers at once and Robolectric's permission and rationale shadows.
- `ZxingCppFormatsTest`, `NoPlayServicesTest`: see [[zxing-cpp]].
- `ScannerDirectionsTest`, `AppNavigationTest.addCard_opensTheScanner_andBackReturnsToTheList`: navigation.

Not covered by JVM tests: `ProcessCameraOpener` and `ZxingCppFrameReader.read`, which need a camera and the native
library. Reading real cards is checked on a device ([#5](https://github.com/shockrifle/loyscan/issues/5)).

## Not implemented

- Saving a scan as a card, and "Enter manually" leading to a form: [#6](https://github.com/shockrifle/loyscan/issues/6).
- Tap to focus and zoom. CameraX's continuous autofocus is used.
