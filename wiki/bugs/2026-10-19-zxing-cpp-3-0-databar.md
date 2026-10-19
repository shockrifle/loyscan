# zxing-cpp 3.0.2 couldn't hand back a DataBar scan

**Fixed in:** the commit that closes #5 (after `8ed8052`) · **Date:** 2026-10-19 · **Area:** [[barcode-scanner]], [[zxing-cpp]]

## Symptom
On a real phone, the scanner read EAN-13, Code 128, QR and PDF417 but never a GS1 DataBar (the omnidirectional and
stacked examples from gs1.org, shown on a monitor). Nothing happened; the "Card won't scan?" hint appeared. Binary
Eye, another app built on zxing-cpp, read the same images at once. The JVM tests were all green.

## Root cause
A mismatch inside zxing-cpp's Android AAR 3.0.2. Its native core already uses the v3 format list, where DataBar is
reported as a variant (DataBar Omni, Stacked, …). Its Kotlin wrapper still had the old flat `Format` enum
(`DATA_BAR`, `DATA_BAR_EXPANDED`, `DATA_BAR_LIMITED`). Converting a native DataBar result to the Kotlin enum
failed, and `readYBuffer` threw `RuntimeException: Invalid BarcodeFormat` for every frame with a DataBar in it.
`ScanAnalyzer` catches a frame's exception so one bad frame can't crash the app, logs it as a warning
("Couldn't read a camera frame") and moves on, so the failure looked like "nothing found".

How it was found: a diagnostic build that logged every raw result, with all formats and `returnErrors`, showed no
DataBar at all, under any reader option or resolution. The phone log then showed the analyzer's warning 218 times,
starting the moment the DataBar came into view. The native library's strings confirmed it: the v3 variant names and
only three `DATA_BAR*` names on the JNI side.

## Fix
zxing-cpp **3.1.1**, whose Kotlin enum carries every variant and whose JNI maps formats by value; Kotlin to 2.3.21
for its stdlib (`8ed8052`). `ZxingCppFormats.kt` maps each variant to its symbology in a `when` with no `else`, so a
format a later version adds fails the build until it is placed. Checked on a device: both DataBar examples, EAN-13,
QR and PDF417 read.

## Lessons
- **A swallowed per-frame exception hides a systematic failure.** Catching per frame is right, but the warning is
  the only trace. When a symbology "never scans", check the log for `ScanAnalyzer` warnings before tuning
  resolution or options.
- **JVM tests can't see the native boundary.** The mapping tests were green because they only see the Kotlin enum.
  Every zxing-cpp upgrade needs a device check of each symbology family, DataBar included.
- **Compare with a known-good reader built on the same library** (Binary Eye): it settles "the source is
  unreadable" against "our pipeline" in one step.
