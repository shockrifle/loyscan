# AndroidSVG (SVG store logos)

An SVG picked or shared as a store's logo is drawn once, when it is copied in, by **AndroidSVG 1.4**
(`com.caverock:androidsvg-aar`, Apache-2.0), onto a transparent PNG. From then on it is a PNG like any other logo: the
crop screen, `encodeStoreLogo`, storage, the tiles, the card screen and the widget never see an SVG
([[store-logo]], #211). Card photos and the scanner don't accept SVG.

Code: `drawSvgLogo` in `app/src/main/java/com/bdaniel/loyscan/data/photos/SvgLogos.kt`, called by
`FileCardPhotos.stageLogo` (`CardPhotos.kt`, same folder) for a copy `BitmapFactory` can't read.

## Contract

- **Size.** A PNG of `MAX_LOGO_SIDE` (512) pixels on its long side, the stored logo's limit; a higher resolution would be
  wasted, as `encodePicture` decodes a 1024 px picture at half size anyway (`sampleSize`). The shape is the SVG's
  `width` × `height` when both are set, else its `viewBox`, else a browser's 300 × 150. AndroidSVG reads a width or
  height of zero or in percent as not set. Each side is at least 1 px. The SVG is scaled by giving it a viewBox of its
  own units when it has none, and the PNG's pixels as its width and height.
- **Failure.** A file over `MAX_SVG_BYTES` (5 MiB), one that isn't an SVG, and any failure to draw (a
  `StackOverflowError` from deep nesting too) is an `IOException`, so the logo screen says the picture couldn't be used.
  The cap is checked on the copy before parsing; the copy itself isn't capped, as for any pick. A gzipped SVG (an
  `.svgz`, gzip's `1f 8b` first) is unpacked by `drawSvgLogo` itself, up to the same cap, and the checks below read the
  unpacked text: AndroidSVG would unpack it too, after them (PR #242 review).
- **Nothing outside the file is read.** No `SVGExternalFileResolver` is registered (it is process-wide and static), so
  AndroidSVG skips an `<image>` or a font that isn't embedded as a `data:` URI. Its SAX parser, used once a DOCTYPE
  declares entities, turns external entities off.
- **Entities.** AndroidSVG expands the entities a DOCTYPE declares (`SVG.isInternalEntitiesEnabled`, left on): some
  vector editors export `<!ENTITY ns_svg "http://…">`, and with entities off such a file drew in the wrong colour
  (spike on 2026-11-26). `checkEntities` refuses a declaration that isn't a name and a quoted value without `&` or `%`
  (a nested entity, the shape of an expansion bomb; `SYSTEM` / `PUBLIC`; a parameter entity), and a file with a NUL
  byte (UTF-16), whose declarations that check couldn't read. The owner's choice on 2026-11-26, over turning entities
  off.

## Quirks

- Under Robolectric's `NATIVE` graphics AndroidSVG draws real pixels, so `FileCardPhotosTest` checks the drawn PNG. On
  the JVM, the SAX parser refuses a billion-laughs file by itself; Android's own parser's limit on API 29–31 wasn't
  checked, which is why `checkEntities` doesn't rely on it.
- Drawing can't be stopped midway. A small file that is slow to draw (long `<use>` or `<pattern>` chains) holds the IO
  thread, and the logo screen shows its progress bar, until it ends. Accepted (PR #242 review): the user picks or shares
  the file, and nothing bounds the drawing's time or element count.
- The release (R8) build with an SVG pick hasn't been run on a device. AndroidSVG uses no reflection that its consumer
  rules would need to keep (not checked).

## Why not `coil-svg`

`coil-svg` would draw the SVG on every display, through Coil, and the widget decodes logos with `BitmapFactory`
([[card-widget]]). Drawing once on import keeps every image the app shows a PNG or JPEG it wrote
([[coil-image-loading]]), which is why `coil-svg` was removed in #157. AndroidSVG is what `coil-svg` uses underneath.
