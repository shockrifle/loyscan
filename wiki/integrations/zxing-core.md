# ZXing core (barcode drawing)

Barcodes on the detail screen are encoded with **ZXing core 3.5.3** (`com.google.zxing:core`, Apache-2.0): pure
Java, no Android or network code. Only its writers are used, in `BarcodeEncoder`
(`app/src/main/java/com/bdaniel/loyscan/ui/barcode/BarcodeEncoder.kt`); Compose draws the result
(`app/src/main/java/com/bdaniel/loyscan/ui/barcode/Barcode.kt`). Scanning is a separate library (#5).

## Contract

- `BarcodeEncoder.encode(content, format)` returns an `EncodedBarcode` (module grid + the format actually used) or
  `null` for empty content. It tries the card's format, then `CODE_128`, then `QR_CODE` (#4); the returned
  `format` says which one worked.
- Encoding is at size 0×0: ZXing then returns the smallest matrix, one pixel per module, and a single row for 1D
  formats. The screen scales it (`barcodeRects`): whole-pixel modules when there's room, so all bars of one width
  match; 1D barcodes are stretched to the full height, 2D ones keep square modules.
- **No charset hint unless needed.** With `EncodeHintType.CHARACTER_SET` set, ZXing prepends an ECI header to Aztec,
  PDF417 and byte-mode QR codes, even for ASCII; the card's own barcode has none, and a till may pass it on as a
  prefix or reject the code. ZXing's own reader strips it, so a read-back test can't see it. `BarcodeEncoder` passes
  UTF-8 only for content ISO-8859-1 can't hold (`hintsFor`); test:
  `BarcodeEncoderTest.latin1Content_isEncodedWithoutACharsetHeader`.
- `BarcodeEncoderTest` decodes every format with ZXing's own `MultiFormatReader`, so a format that stops encoding
  correctly fails the build.

## Quirks

- **No DataBar writers.** ZXing core reads `RSS_14` / `RSS_EXPANDED` but can't write them, and has no DataBar
  Limited at all. `DATABAR*` cards are always drawn as `CODE_128` with the same content. Whether tills accept that
  is unknown.
- **No GS1-128.** It isn't a separate format; GS1-128 is Code 128 with a leading FNC1 (`EncodeHintType.GS1_FORMAT`),
  which isn't used: [[gs1-128-drawn-as-code-128]].
- **Rejection is an exception.** 1D writers throw `IllegalArgumentException` for content they can't hold (EAN-13 with
  the wrong length or check digit, a non-ASCII character in Code 128, an odd-length ITF); the 2D writers throw
  `WriterException` when content is too long. `BarcodeEncoder` catches both and moves to the next format.
- **Quiet zones differ.** 1D writers add a 10-module margin and QR a 4-module one; Aztec and Data Matrix come with
  none. The detail screen puts every barcode on a white card with 16dp padding, which is their quiet zone. Without
  a margin, ZXing's own reader can't find an Aztec or Data Matrix symbol (seen while writing the test).
- Barcodes are black on white in both themes: scanners need dark bars on a light background.
