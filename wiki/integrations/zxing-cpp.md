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
  (zxing-cpp behaviour as documented upstream; the separator is not yet checked on a device, #42). Test:
  `ZxingCppFormatsTest.theReader_looksForTheAppsFormatsAndReturnsPlainText`.
- Formats: the reader looks only for `SCANNED_FORMATS`, every zxing-cpp family and variant that maps to a
  `BarcodeFormat`. Results come back as variants (`DATA_BAR_OMNI`, `DATA_BAR_STK`, `QR_CODE_MODEL_2`, `CODE_39_STD`,
  `ISBN`, …), each mapped to its symbology. Not mapped, so not looked for: Micro QR, rMQR, Micro PDF417, Aztec runes,
  Code 32 / PZN, EAN add-ons, MaxiCode, Telepen, DX film edge. The mapping `when` has no `else`, so a format a new
  version adds fails the build. Tests: `ZxingCppFormatsTest`.
- Options: `tryHarder`, `tryRotate` (a card held sideways), `tryInvert` (light bars on a dark card), one symbol per
  frame.

## Quirks

- **3.0.2 can't return DataBar.** Its native core and Kotlin enum don't match, and every DataBar frame throws
  `Invalid BarcodeFormat`: [[2026-10-19-zxing-cpp-3-0-databar]]. Don't go below 3.1.
- **3.1.x needs Kotlin 2.3.20+** (its stdlib); the app is on 2.3.21.
- **A reader exception is per frame and only logged.** `ScanAnalyzer` logs "Couldn't read a camera frame" (tag
  `ScanAnalyzer`) and skips the frame. If a symbology never scans, look for that warning first.
- **No JVM tests of reading.** The native library is built for Android ABIs only. Reading real barcodes is checked
  on a device (2026-10-19: EAN-13, Code 128, QR, PDF417, DataBar and DataBar Stacked); after every zxing-cpp upgrade,
  check each family again. JVM tests cover the mapping, the options, `ScanAnalyzer` (with `FakeImageProxy`) and the camera
  lifecycle (with a fake `CameraOpener`).
- The AAR's consumer ProGuard rule keeps `zxingcpp.**`, which the JNI side needs. Release builds aren't minified yet
  ([[android-app]]).
