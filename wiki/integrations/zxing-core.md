# ZXing core (barcode drawing)

Barcodes on the detail screen are encoded with **ZXing core 3.5.3** (`com.google.zxing:core`, Apache-2.0): pure
Java, no Android or network code. Only its writers are used, in `BarcodeEncoder`
(`app/src/main/java/com/bdaniel/loyscan/ui/barcode/BarcodeEncoder.kt`); Compose draws the result
(`app/src/main/java/com/bdaniel/loyscan/ui/barcode/Barcode.kt`). Scanning is a separate library: [[zxing-cpp]].

## Contract

- `BarcodeEncoder.encode(content, format)` returns an `EncodedBarcode` (module grid + the format actually used) or
  `null` for empty content. It tries the card's format, then `CODE_128`, then `QR_CODE` (#4); for a GS1 format
  (`BarcodeFormat.isGs1`) `GS1_128`, then `GS1_QR_CODE`, so GS1 data keeps its mark (#45). The returned `format`
  says which one worked.
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
  Limited at all. `DATABAR*` cards are always drawn as `GS1_128` with the same content (as `CODE_128` until #45), and
  the detail screen says so under the barcode ([[card-detail]] → Fallback note). Whether tills accept that is unknown.
- **GS1-128 needs an escape character, not a hint.** ZXing has no GS1-128 format: it is Code 128 with a leading FNC1.
  `EncodeHintType.GS1_FORMAT` doesn't add that FNC1 to Code 128. In 3.5.3 `Code128Writer` reads only `CODE128_COMPACT`
  and `FORCE_CODE_SET` (checked in its bytecode, 2026-10-20), and the GS1 hint is read only by 2D writers. Instead,
  the Code 128 writer writes FNC1–FNC4 for the characters U+00F1–U+00F4 (`ñ` `ò` `ó` `ô`), so `BarcodeEncoder` puts
  U+00F1 in front of `GS1_128` content (`withFnc1For`). Test: `BarcodeEncoderTest.gs1_128_isCode128WithALeadingFnc1`.
  ZXing's reader reports symbology identifier `]C1` for GS1-128 and `]C0` for plain Code 128. *Correction 2026-10-20:* this
  page used to say `GS1_FORMAT` adds the FNC1.
  The FNC1 that ends a variable-length GS1 field comes from a scan as GS (U+001D, [[zxing-cpp]] → Contract), and is
  drawn as FNC1 too, as the GS1 spec asks. As itself, GS would be a literal character in code set A, which also needs a
  code set switch (#42). Test: `BarcodeEncoderTest.gs1_128_separatorIsDrawnAsFnc1`. ZXing's own reader returns such an
  FNC1 as GS only with `DecodeHintType.ASSUME_GS1`, and then puts `]C1` in front of the text. A DataBar card falls back to
  GS1-128, so the same applies to it (#45).
- **GS1 Data Matrix and GS1 QR come from hints** (#45). `EncodeHintType.GS1_FORMAT` makes the 2D writers start with
  FNC1, but `DataMatrixWriter` reads it only together with `DATA_MATRIX_COMPACT` (its minimal encoder, which also
  writes each GS as FNC1); its default encoder ignores it (3.5.3 source). In a QR code a GS in byte mode already is
  GS1's FNC1. In alphanumeric mode, which ZXing picks when every character fits it, a single `%` means FNC1 and the
  `%` sign is `%%`; ZXing doesn't escape it, so `BarcodeEncoder` doubles `%` for such content (`withFnc1For`). ZXing's
  reader reports `]d2` / `]Q3` for them (`]d1` / `]Q1` plain). Its Data Matrix reader returns every FNC1 as GS, the
  leading one too, so the read-back test drops that one. Tests: `BarcodeEncoderTest.gs1DataMatrixAndQr_areDrawnInGs1Mode`,
  `gs1DataMatrixAndQr_separatorReadsBackAsASeparator`, `gs1Qr_percentSignIsNotReadAsASeparator`. Aztec and PDF417
  have no GS1 mode in ZXing core.
- **Those four characters are never letters in Code 128.** Because of the same escape mapping, Code 128 content that
  contains `ñ`, `ò`, `ó` or `ô` would be written with a function character in that place, not the letter, and no error
  is raised. So `BarcodeEncoder` skips Code 128 (and GS1-128) for such content (`CODE_128_ESCAPES`), and the chain
  moves on to QR, in ISO-8859-1 without an ECI header. GS1-128, and a DataBar card's GS1-128 fallback, move on to GS1 QR instead (#45). Test:
  `BarcodeEncoderTest.code128ContentWithAnEscapeCharacter_fallsBackToQr` (#46).
- **Rejection is an exception.** 1D writers throw `IllegalArgumentException` for content they can't hold (EAN-13 with
  the wrong length or check digit, a non-ASCII character in Code 128, an odd-length ITF); the 2D writers throw
  `WriterException` when content is too long. `BarcodeEncoder` catches both and moves to the next format.
- **ZXing's quiet zones are too narrow or missing.** At size 0 the 1D writers split a 9–10-module margin over both
  sides (4–5 each; EAN-13 needs 11 on the left, Code 128 10 a side), QR gets 4, Aztec and Data Matrix none. A fixed
  screen padding doesn't fix it: a 2D code drawn at full width has modules wider than 16dp. So `BarcodeEncoder` asks
  for `MARGIN = 0` and `EncodedBarcode` adds each symbology's own minimum in modules (`quietZoneModules`: 11 for 1D,
  4 for QR, 2 for Aztec, Data Matrix and PDF417; the same for their GS1 forms). Test: `BarcodeEncoderTest.everyBarcode_carriesItsSymbologysQuietZone`;
  the read-back test draws barcodes with no extra border, and UPC-E and Data Matrix fail to read without it.
- Barcodes are black on white in both themes: scanners need dark bars on a light background.
