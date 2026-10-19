# zxing-cpp + CameraX for scanning, not ML Kit

**Status:** Active · **Date:** 2026-10-16 · **Issue:** [#5](https://github.com/shockrifle/loyscan/issues/5)

The scanner reads barcodes with **zxing-cpp** (`io.github.zxing-cpp:android`, Apache-2.0) on frames from
**CameraX**. No Google Play Services or ML Kit code is in the app. Contract and quirks: [[zxing-cpp]].

## Context
The add-card flow ([#6](https://github.com/shockrifle/loyscan/issues/6)) starts by scanning the card. It must work
offline and without Google Play Services (the app is meant to run on de-Googled phones too), and it must read every
symbology a card can be stored in ([[zxing-core]] draws them), GS1 DataBar included.

## Options considered
- **ML Kit barcode scanning** (bundled or through Play Services). Fast and well tuned, but it can't read GS1 DataBar,
  its libraries depend on Play Services artifacts even in the bundled model, and it is closed source.
- **ZXing's Java reader** (`MultiFormatReader`, already on the classpath for drawing). It reads DataBar and DataBar
  Expanded but not DataBar Limited, it's slow on camera frames, and the Java project is in maintenance mode.
- **zxing-cpp**: an actively maintained C++ port with an official Android wrapper that reads a CameraX `ImageProxy`
  directly. It reads all three DataBar variants. The cost is a native library per ABI (`arm64-v8a`, `armeabi-v7a`,
  `x86`, `x86_64`) in the APK.

## Decision
- zxing-cpp **3.1.1** with CameraX **1.5.3** (`camera-camera2`, `camera-lifecycle`, `camera-compose`); versions in
  `gradle/libs.versions.toml`. CameraX 1.5.x because zxing-cpp 3.1.1 is built against `camera-core` 1.5.2.
- Kotlin **2.3.21**, because zxing-cpp 3.1.x is built on kotlin-stdlib 2.3.20 (`8ed8052`).
- *Correction (2026-10-19):* the first version of this decision chose zxing-cpp 3.0.2, to avoid raising Kotlin. On a
  device, 3.0.2 couldn't return any DataBar result: [[2026-10-19-zxing-cpp-3-0-databar]]. 3.1's `Format` enum
  reports variants (`DATA_BAR_OMNI`, `QR_CODE_MODEL_2`, …), which `ZxingCppFormats.kt` maps to their symbology.

## Why
DataBar is a hard requirement of #5, and only zxing-cpp reads all of it offline without Play Services. Using the
same ZXing family for drawing and reading also keeps format names and content conventions aligned (inferred; not
measured).

## Consequences
- `NoPlayServicesTest` fails the build if a Play Services or ML Kit class reaches the classpath (e.g. through a
  CameraX artifact such as `camera-mlkit-vision`).
- zxing-cpp's native code can't run in JVM tests. Its mapping and options are tested
  (`ZxingCppFormatsTest`); actual reading is checked on a real device (#5's first criterion).
- The camera code sits behind small seams so Robolectric can drive the rest: [[barcode-scanner]] → Tests.
