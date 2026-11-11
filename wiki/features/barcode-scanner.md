# Barcode scanner

A full-screen camera view that reads a card's barcode, or reads it from a picked picture, and hands on the first scan: its content exactly as decoded,
its format, and whether it holds GS1 data (a Code 128, Data Matrix or QR marked GS1 is handed on as `GS1_128`, `GS1_DATA_MATRIX` or
`GS1_QR_CODE`: [[zxing-cpp]] → Contract). Offline, without Play Services ([[2026-10-16-zxing-cpp-for-scanning]], [[zxing-cpp]]). It is the step
after the store picker of [[add-card]]; a scan or "Enter manually" replaces it with the card form.

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `ScannerScreen.kt:51`, `ScannerScreen`; state in `ScannerViewModel.kt`.
- Route `Scanner(storeId)`, `ScannerDirections` and `scannerDirections()` in `Navigation.kt:142-172`; registered in
  `MainActivity.kt:106-108`.
- `ui/scanner/`: `CameraPermission.kt` (permission state), `CameraScanner.kt` (CameraX, `ScanAnalyzer`),
  `ZxingCppFrameReader.kt` and `ZxingCppFormats.kt` (decoding), `PicturePicker.kt` and `PictureReader.kt` (reading a
  picture, #76).

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
  drops them, so one scan navigates once. A frame decoded while the scanner animates out after Back doesn't open the
  form: `onScanned` and `toManualEntry` navigate only while the scanner's own entry is current (#68,
  `AddCardDirectionsTest.aScanDecodedAfterBackFromTheScanner_…`).
- **Torch.** A toggle in the top bar, only when the camera has a flash unit. Its state is in the ViewModel, so it
  survives rotation, and it is set again on every resume: CameraX turns the torch off when it closes the camera on
  pause.
- **Hint.** 8 s after the camera first starts (`ScannerViewModel.HINT_DELAY`), a card says "Card won't scan?" with
  tips. A camera restart (rotation) doesn't reset the timer.
- **Scan from image** (#76) is always at the bottom, above "Enter manually": with the camera, without the permission,
  after a camera failure, while the camera is unavailable. It opens the system photo picker for images only
  (`rememberPicturePicker`, `PickVisualMedia.ImageOnly`; no storage permission, the app gets a read grant for the one
  picture; where the photo picker isn't available, androidx falls back to the document picker). The picture is read
  by `ZxingCppPictureReader` off the main thread with the camera's formats and options ([[zxing-cpp]] → Reading a
  picture), and only held in memory while it is read. A barcode found goes through `ScannerViewModel.onScanned`, the
  same first-scan-only rule as the camera (`ScannerViewModel.onPicturePicked`, `ScannerViewModel.kt:90`), then to
  `directions.onScanned` from a `LaunchedEffect` collecting `pictureScans` (`ScannerScreen.kt:66`): the card form
  opens with its content and format exactly as after a camera scan. No barcode, or a picture that can't be opened,
  keeps the scanner open with a message in place of the hint; a cancelled picker changes nothing. While a picture is
  read, the button says "Reading the picture…" and is disabled, so one picture is read at a time. Opening a picture
  that takes longer than 30 s (a cloud photo on a slow network) is reported as "couldn't be opened" and the button is
  usable again; the late picture is dropped (#79, [[zxing-cpp]] → Reading a picture). A read in progress isn't kept
  over process death; the user picks the picture again.
- **Enter manually** is always at the bottom, with or without the camera.
- **Language** (#72): every text on the scanner is a string resource, in English or Hungarian by the phone's language
  ([[add-a-string]]). A picture's problem is one whole string per case ("No barcode was found in that picture. Try
  another picture, …"), not a reason with a shared tail added. Tests: `ScannerScreenTest.inHungarian_…`, one per
  state (the camera running with its torch, hint and a picture being read; each permission state; a camera failure;
  an unavailable camera; each picture problem).

## Tests

- `ScannerScreenTest`: the screen with a fake camera (a `ScannerCamera` lambda that records torch and events),
  `FakeCameraPermission`, a fake `PicturePicker` that hands a URI to the ViewModel, and `FakePictureReader`. Covers
  each permission state, the first-scan-only rule (also between a camera scan and a picture), torch, hint, camera
  failure, and "Scan from image" in every camera state with each reading outcome.
- `ScannerViewModelTest` (Robolectric, for `Uri`): hint timing with virtual time (`ScannerViewModel(hintDelay, …)`),
  torch, one scan, reading a picture (found, none, unreadable, cancelled, one at a time, and a timed-out read over
  the real `ZxingCppPictureReader`: picked again, its late result ignored).
- `PicturePickerTest`: the real picker over a test `ActivityResultRegistry`: images only, a pick and a cancel.
- `ZxingCppPictureReaderTest`: see [[zxing-cpp]].
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

Not covered by JVM tests: `ProcessCameraOpener`, `ZxingCppFrameReader.read`, `ZxingCppBitmapReader` and
`decodePicture`, which need a camera, the native library or a real image decoder. Reading a picture, checked on a
device (Android 15, 2026-11-03, #76, pictures generated with ZXing core, not screenshots of a store's app): 1080×2400 screenshots of an EAN-13, a
Code 128 and a QR card each opened the form with their number and format; a 5400×12000 (65 MP) JPEG of the EAN-13 card,
scaled to 8 MP, read too; a picture without a barcode said so and kept the scanner open. The Android 15 photo picker
(Google Photos tab) shows a "Done" bar even for this single-picture request, so picking takes a tap on the picture and
one on "Done". Opening a cloud photo that times out is not checked (#80). Checked on a device (Android 15, 2026-10-19, final build): EAN-13, Code 128, QR, PDF417, GS1 DataBar and
DataBar Stacked (gs1.org samples on a screen; content `01` + digits, no brackets), Code 39 (also a PZN-shaped one,
read as plain Code 39); GS1 marking (2026-10-20: a GS1-128 barcode marked GS1, plain Code 128 not); torch after a pause; the permission flow; the hint; "Enter manually"; the list's bottom padding. The first
device check found DataBar never scanned: [[2026-10-19-zxing-cpp-3-0-databar]].

## Not implemented

- Tap to focus and zoom. CameraX's continuous autofocus is used.
