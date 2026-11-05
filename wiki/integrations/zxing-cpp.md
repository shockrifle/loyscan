# zxing-cpp (barcode scanning)

The scanner reads camera frames with **zxing-cpp 3.1.1** (`io.github.zxing-cpp:android`, Apache-2.0): a native C++
decoder with a small Kotlin wrapper, `zxingcpp.BarcodeReader`, that reads a CameraX `ImageProxy`. Offline, no
Play Services. Why this library: [[2026-10-16-zxing-cpp-for-scanning]]. Drawing is ZXing's Java core:
[[zxing-core]].

Code: `app/src/main/java/com/bdaniel/loyscan/ui/scanner/ZxingCppFrameReader.kt` (the reader and its options),
`ZxingCppFormats.kt` (format and content mapping) in the same folder.

## Contract

- `BarcodeReader(options)` loads `libzxingcpp_android.so` in its constructor. On an ABI the AAR doesn't ship it
  throws `UnsatisfiedLinkError`. `CameraXScannerContent` creates the reader before opening the camera and reports
  the screen's "camera couldn't be opened" state instead of crashing.
- `read(ImageProxy)` takes the Y plane of a YUV frame, the crop rect and the frame's rotation, and returns a list of
  `Result`s. It doesn't close the frame; `ScanAnalyzer` does, also when reading throws.
- **Text mode is `PLAIN`.** The default, `HRI`, writes GS1 data the way it is printed under the barcode, e.g.
  `(01)09012345678901`, which would change the card's number. `PLAIN` gives the barcode's own characters. The GS1
  FNC1 at the start is not part of the text; later FNC1 separators come through as the GS character (U+001D)
  (zxing-cpp behaviour as documented upstream). On a device, DataBar and DataBar Stacked read as `01` + digits, no
  brackets; the separator itself needs a DataBar Expanded and is not yet checked on a device. The GS stays in the
  stored scan; it is drawn as FNC1 ([[zxing-core]] → Quirks) and shown as a GS1 field boundary
  ([[2026-10-21-gs1-scans-shown-as-hri]], #42). Test:
  `ZxingCppFormatsTest.theReader_looksForTheAppsFormatsAndReturnsPlainText`.
- **Formats.** In 3.1 a format value's low byte is the symbology and its high byte the variant; high byte `' '`
  (0x20) is the family, and asking for a family asks for every variant of it (`members()`). Results come back as
  variants (`DATA_BAR_OMNI`, `QR_CODE_MODEL_2`, `CODE_39_STD`, `ISBN`, …), each mapped to its symbology; the mapping
  `when` has no `else`, so a format a new version adds fails the build.
- **What is asked for** (`SCANNED_FORMATS`, an explicit list): by variant where the family has a variant the app
  drops (Code 39 → `CODE_39_STD`/`EXT`, not Code 32 / PZN, whose readings give other text for the same bars, so
  such a card would never scan; QR → models 1 and 2; Aztec → `AZTEC_CODE`; EAN/UPC one by one); by family where all
  members map (Code 128, Codabar, Code 93, Data Matrix, DataBar, ITF). PDF417 must be asked for as the family, since
  plain PDF417 is reported as the family value, so Micro PDF417 comes along and is dropped. Tests:
  `ZxingCppFormatsTest` (every requested value stands only for mappable formats, except that one).
- **GS1 data is marked** (`ScannedBarcode.gs1`, #41). In plain text mode a GS1-128 barcode's text is the same as a
  plain Code 128's, since the leading FNC1 isn't in it; zxing-cpp says it is GS1 through the content type (`GS1`) and
  the symbology identifier's modifier (`]C1`). Both come from one flag: `Content::type()` returns `GS1` when
  `symbology.aiFlag == AIFlag::GS1`, unless the content is empty (`TEXT`; dropped anyway) or has an ECI it can't
  process (`UNKNOWN_ECI`; GS1-128 carries no ECIs) (`core/src/Content.cpp:432-442` in 3.1.1). The app reads the
  content type. A Code 128 marked GS1 becomes `BarcodeFormat.GS1_128` (`scannedBarcode`, #33), a Data Matrix or QR
  `GS1_DATA_MATRIX` / `GS1_QR_CODE` (#45, [[2026-10-21-gs1-mark-as-barcode-format]]), so they are drawn back with
  their FNC1 ([[zxing-core]]). Aztec and PDF417 keep their own format with the mark, which a saved card loses (ZXing
  core can't write them in GS1 mode). DataBar is always GS1, so a DataBar
  scan is marked too; the interim dialog doesn't repeat "GS1" after a name that already starts with it (DataBar,
  GS1-128). Tests: `ZxingCppResultsTest.aGs1Code128Result_isAGs1_128Scan`, `aPlainCode128Result_isNotGs1`,
  `ZxingCppFormatsTest.scannedBarcode_code128MarkedGs1_isGs1_128`, `scannedBarcode_dataMatrixAndQrMarkedGs1_areTheirGs1Formats`. Checked on a device (2026-10-20): a GS1-128 barcode reads as Code 128 marked GS1, a
  plain Code 128 unmarked, DataBar marked (shown in the interim dialog).
- **Reading a picture** (#76, `ui/scanner/PictureReader.kt`). `read(Bitmap)` takes the same `ZXING_CPP_OPTIONS` and
  goes through the same `firstScannedBarcode`, so a picture's scan is exactly a camera scan (content, format, GS1
  mark). The JNI side locks the bitmap's pixels, so a hardware bitmap can't be read, and the upstream Android
  wrapper takes only 8-bit RGBA and alpha bitmaps (not verified against 3.1.1's native source, which the sources jar
  doesn't include, nor on a device). So `decodePicture` decodes with `ImageDecoder.ALLOCATOR_SOFTWARE` and
  `toReadableConfig` copies anything that isn't `ARGB_8888` (a wide-gamut picture comes back as `RGBA_F16`). A picture
  over `MAX_PICTURE_PIXELS` (8 MP) is scaled down, keeping its aspect ratio; any phone or tablet screenshot is under
  it, so a store app's card is read at its own size. A first version capped the long side at 2048 px, which
  resampled nearly every phone screenshot to about 0.64× and could blur a dense barcode's modules (review of #76).
  `ZxingCppPictureReader` decodes on `Dispatchers.IO` (the photo picker also lists cloud photos, downloaded when
  opened) and reads on `Dispatchers.Default`, recycles the bitmap, and turns any failure to open or decode the
  picture (I/O, a revoked grant, any provider exception), a reader exception or a missing native library into
  `PictureScan.Unreadable`; a cancellation (the screen left mid-read) is rethrown, since `CancellationException` is
  an `IllegalStateException` and the broad catch would otherwise swallow it. **Opening times out** after
  `PICTURE_DECODE_TIMEOUT` (30 s) as `Unreadable` (#79): a cloud photo on a slow or missing network would otherwise
  keep the button on "Reading the picture…" until the download ends. The decode blocks inside the provider, which a
  coroutine can't interrupt, so it runs in the reader's own scope, outside the caller's job (`decodeInTime`), and the
  caller stops waiting for it at the timeout or when it is cancelled. `decodePicture` opens the file as
  `ImageDecoder`'s own content source does, `openTypedAssetFileDescriptor(uri, "image/*", null, signal)`, but through
  `ImageDecoder.createSource(Callable)` so it can pass a `CancellationSignal`, which is cancelled when the read is
  abandoned: a cloud provider can stop its download (review of #79; whether the picker's cloud provider honours it
  is not checked on a device). A provider that has no file for it (`FileNotFoundException`) is read through
  `createSource(resolver, uri)`, whose stream fallback has no signal, unless the read was already abandoned. A picture that still arrives later is recycled
  and never read. Tests:
  `ZxingCppPictureReaderTest` (with the decoder and the native reader replaced;
  Robolectric's `ImageDecoder` can't decode a picture: its legacy graphics mode has no decoder, and its native mode
  doesn't run on Windows; `toReadableConfig` and the target size are tested directly). The decoder is a `suspend`
  lambda, so a test holds a "download" open with a `CompletableDeferred` on the test scheduler and the timeout runs
  in virtual time; a test that decodes on a real thread uses `runBlocking`, since under `runTest` virtual time jumps
  to the timeout as soon as the test thread is idle, before the other thread has decoded.
- **Dropped results are logged** (`firstScannedBarcode`, `Timber.d` "Dropped a … result", format only, never the
  content). Test: `ZxingCppResultsTest`.
- Options: `tryHarder`, `tryRotate` (a card held sideways), `tryInvert` (light bars on a dark card), one symbol per
  frame.

## Quirks

- **3.0.2 can't return DataBar.** Its native core and Kotlin enum don't match, and every DataBar frame throws
  `Invalid BarcodeFormat`: [[2026-10-19-zxing-cpp-3-0-databar]]. Don't go below 3.1.
- **3.1.x needs Kotlin 2.3.20+** (its stdlib); the app is on 2.3.21.
- **A reader exception is per frame and only logged.** `ScanAnalyzer` logs "Couldn't read a camera frame" (tag
  `ScanAnalyzer`) and skips the frame. If a symbology never scans, look for that warning first, then for "Dropped a
  … result" (a format the app doesn't store).
- **No JVM tests of reading.** The native library is built for Android ABIs only. Reading real barcodes is checked
  on a device (2026-10-19, on 3.1.1 with the final format list: EAN-13, Code 128, QR, PDF417, DataBar and DataBar
  Stacked, Code 39 including a PZN-shaped `-12345678` read as plain Code 39; all from screens or labels, not
  loyalty cards); after every zxing-cpp upgrade or format-list change,
  check each family again. JVM tests cover the mapping, the options, `ScanAnalyzer` (with `FakeImageProxy`) and the camera
  lifecycle (with a fake `CameraOpener`). Reading a picture: `ZxingCppBitmapReader` and `decodePicture` are untested
  on the JVM; `ZxingCppPictureReader` around them is.
- The AAR's consumer ProGuard rule keeps `zxingcpp.**`, which the JNI side needs. We add no rule of our own. On
  the R8 build, the smoke test keeps the scanner's camera running for 3 s, so frames go through the analyzer and
  zxing-cpp's JNI read without crashing ([[2026-11-05-r8-smoke-test-on-a-device]]). No barcode is in view, though:
  decoding one and building its result under R8 isn't covered.
