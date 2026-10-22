# Barcode scanner

A full-screen camera view that reads a card's barcode and hands on the first scan: its content exactly as decoded,
its format, and whether it holds GS1 data (a Code 128, Data Matrix or QR marked GS1 is handed on as `GS1_128`, `GS1_DATA_MATRIX` or
`GS1_QR_CODE`: [[zxing-cpp]] → Contract). Offline, without Play Services ([[2026-10-16-zxing-cpp-for-scanning]], [[zxing-cpp]]). It is the step
after the store picker of [[add-card]]; until the card form exists, a scan is only shown in a dialog:
[[scanner-result-dialog-until-add-card-flow]].

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `ScannerScreen.kt:46`, `ScannerScreen`; state in `ScannerViewModel.kt`.
- Route `Scanner(storeId)`, `ScannerDirections` and `scannerDirections()` in `Navigation.kt:41-63`; registered in
  `MainActivity.kt:69-74`.
- `ui/scanner/`: `CameraPermission.kt` (permission state), `CameraScanner.kt` (CameraX, `ScanAnalyzer`),
  `ZxingCppFrameReader.kt` and `ZxingCppFormats.kt` (decoding).

## Behaviour

- **Permission.** On entry, if the camera permission was never asked for on this visit, the system dialog opens
  (`ScannerScreen.kt:54`). While it isn't granted, the screen explains why the camera is needed, with "Allow camera"
  (asks again) or, once the system won't ask again, "Open settings" (the app's details page). The status is read
  again on every resume, so allowing it in the settings and coming back shows the camera. Which denial is which
  (`cameraPermissionStatus`): the first denial on a visit always offers "Allow camera", because on Android 11+
  closing the dialog unanswered also leaves `shouldShowRequestPermissionRationale` false. A later denial without a
  rationale means the system won't ask again. A request still open over a rotation isn't sent twice (the pending
  flag is saved).
- **Camera.** The back camera, bound to the destination's lifecycle: it stops on pause and when the screen is left.
  If binding fails (no back camera, CameraX can't initialise) or zxing-cpp's native library is missing for the ABI,
  the screen says the camera couldn't be opened and keeps "Enter manually".
- **Camera errors after binding** (#40). CameraX reports them through `cameraInfo.cameraState` (camera in use by
  another app, disconnected, too many cameras open, disabled by policy). Any state with an error makes the screen say
  the camera isn't available, over the preview, with "Enter manually"; the torch and the hint are hidden. The camera
  stays bound, so when CameraX opens it again (a recoverable error cleared, e.g. the other app let it go), the state
  is `OPEN` and the preview is back without leaving the screen; the torch is set again, since the camera reopens with
  it off. Other states without an error (closing on pause,
  opening on resume) change nothing (`cameraAvailability` in `ui/scanner/CameraScanner.kt`). A critical error that
  never clears leaves the message up. The LiveData is read through a small bridge, `observeAsFlow`, not
  lifecycle-livedata's `asFlow`, which the app has only as a transitive dependency of CameraX. Checked on a device
  (2026-10-22): with Chrome in split screen taking the camera (a webcam test page), the message showed; closing it
  brought the preview and the torch back. The system Camera app can't be used for this: it doesn't run in split
  screen.
- **Scan.** Each analysis frame (about 1280×720) is read on a background thread; the first barcode found is handed
  to `directions.onScanned` on the main thread. Later frames keep being read, but `ScannerViewModel.onScanned`
  drops them, so one scan navigates once.
- **Torch.** A toggle in the top bar, only when the camera has a flash unit. Its state is in the ViewModel, so it
  survives rotation, and it is set again on every resume: CameraX turns the torch off when it closes the camera on
  pause.
- **Hint.** 8 s after the camera first starts (`ScannerViewModel.HINT_DELAY`), a card says "Card won't scan?" with
  tips. A camera restart (rotation) doesn't reset the timer.
- **Enter manually** is always at the bottom, with or without the camera.

## Tests

- `ScannerScreenTest`: the screen with a fake camera (a `ScannerCamera` lambda that records torch and events) and
  `FakeCameraPermission`. Covers each permission state, the first-scan-only rule, torch, hint, camera failure.
- `ScannerViewModelTest`: hint timing with virtual time (`ScannerViewModel(hintDelay)`), torch, one scan.
- `CameraXScannerTest`: the camera lifecycle with a fake `CameraOpener` (open, a frame's barcode reaching
  `onScanned`, an error after opening and its recovery, torch also after a pause, close on leave, failures) and the
  analysis settings (`scannerUseCases`).
- `CameraAvailabilityTest`: which `CameraState`s mean unavailable / available again; `LiveDataFlowTest`: the bridge,
  and the state pipeline (`availability()`, only changes reach the screen).
- `ScanAnalyzerTest`: frames closed, barcodes delivered on the callback executor, unreadable frames skipped.
- `CameraPermissionTest`: the real permission state, with the system dialog replaced by an `ActivityResultRegistry`
  that answers at once (or leaves the request open, for the rotation test) and Robolectric's permission and rationale shadows.
- `ZxingCppFormatsTest`, `ZxingCppResultsTest`, `NoPlayServicesTest`: see [[zxing-cpp]].
- `AddCardDirectionsTest`, `AppNavigationTest.addCard_opensTheStorePicker_pickingAStoreOpensTheScanner_andBackReturnsToThePicker`: navigation.

Not covered by JVM tests: `ProcessCameraOpener` and `ZxingCppFrameReader.read`, which need a camera and the native
library. Checked on a device (Android 15, 2026-10-19, final build): EAN-13, Code 128, QR, PDF417, GS1 DataBar and
DataBar Stacked (gs1.org samples on a screen; content `01` + digits, no brackets), Code 39 (also a PZN-shaped one,
read as plain Code 39); GS1 marking (2026-10-20: a GS1-128 barcode marked GS1, plain Code 128 not); torch after a pause; the permission flow; the hint; "Enter manually"; the list's bottom padding. The first
device check found DataBar never scanned: [[2026-10-19-zxing-cpp-3-0-databar]].

## Not implemented

- Saving a scan as a card, and "Enter manually" leading to a form: [#6](https://github.com/shockrifle/loyscan/issues/6).
- Tap to focus and zoom. CameraX's continuous autofocus is used.
