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
  brackets; the separator itself needs a DataBar Expanded and is not yet checked (#42). Test:
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
  Stacked, all from screens or labels, not loyalty cards); after every zxing-cpp upgrade or format-list change,
  check each family again. JVM tests cover the mapping, the options, `ScanAnalyzer` (with `FakeImageProxy`) and the camera
  lifecycle (with a fake `CameraOpener`).
- The AAR's consumer ProGuard rule keeps `zxingcpp.**`, which the JNI side needs. Release builds aren't minified yet
  ([[android-app]]).
