# zxing-cpp (barcode scanning)

The scanner reads camera frames with **zxing-cpp 3.0.2** (`io.github.zxing-cpp:android`, Apache-2.0): a native C++
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
  (zxing-cpp behaviour as documented upstream; not yet checked on a device). Test:
  `ZxingCppFormatsTest.theReader_looksForTheAppsFormatsAndReturnsPlainText`.
- Formats: the reader looks only for `SCANNED_FORMATS`, the zxing-cpp formats that map to a `BarcodeFormat`. They
  map one to one, same names except `DATA_BAR*` → `DATABAR*`. Micro QR, rMQR, MaxiCode and DX film edge are not looked
  for: the app couldn't draw them back. Tests: `ZxingCppFormatsTest`.
- Options: `tryHarder`, `tryRotate` (a card held sideways), `tryInvert` (light bars on a dark card), one symbol per
  frame.

## Quirks

- **Version 3.1.x needs a newer Kotlin.** It depends on kotlin-stdlib 2.3.20; see the decision page. 3.1 also
  replaced the flat `Format` enum with families and variants, so the mapping changes on upgrade.
- **No JVM tests of reading.** The native library is built for Android ABIs only. Reading real barcodes is checked
  on a device; JVM tests cover the mapping, the options, `ScanAnalyzer` (with `FakeImageProxy`) and the camera
  lifecycle (with a fake `CameraOpener`).
- The AAR's consumer ProGuard rule keeps `zxingcpp.**`, which the JNI side needs. Release builds aren't minified yet
  ([[android-app]]).
