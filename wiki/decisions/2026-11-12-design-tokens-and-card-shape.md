# Design tokens: a spacing object, M3 shapes, and one card shape

**Status:** Active · amends [[2026-11-04-home-screen-widget]] (tile shape and layout) · **Date:** 2026-11-12 ·
**Issue:** [#108](https://github.com/shockrifle/loyscan/issues/108)

Before the first release the UI gets a polish pass. It is built on shared values, not a `.dp` literal on every
screen. Spacing is a plain Kotlin object. The theme sets M3's `Shapes` explicitly. Everything that depicts the
physical card (list tiles, photo preview, thumbnails, crop frame, scanner cut-out, widget tiles) uses one
`CardShape`, whose corners are in proportion to a real ID-1 card. The scanner gets a viewfinder that is purely
visual, and the widget gets card-ratio tiles in one or two columns. Decided in a grilling session with the user on
2026-11-12. The token values themselves are on [[design-system]].

## Context

- `LoyScanTheme` set only colours and typography. There was no `Shapes`, no spacing object and no `dimens.xml`.
  Layout values were literals, mostly 8/12/16/24dp. The card grid had no side padding, so tiles touched the
  screen edges.
- The scanner had no drawn viewfinder. The crop screen clipped the photo to a full-width card-ratio box on black,
  with no outline, so nothing outside the frame was visible.
- The widget showed each card as a full-width strip with only its label. Resizing it only made longer strips,
  down to 110×40dp.
- `minSdk` is 29. Glance's `cornerRadius` only takes effect from API 31.

## Options considered

1. **Spacing as a CompositionLocal (`MaterialTheme.spacing`)**, the usual Compose pattern. It can be overridden
   per subtree, but nothing here needs that, and Glance code can't read the app's `MaterialTheme`.
2. **Spacing as a plain `object Spacing`** (chosen). One set of constants that the app and the widget both read.
3. **`dimens.xml`**. Resource lookups in Compose are noisier, and there's no per-configuration override to gain.

For the card's corners:

1. **Theme `medium` (12dp) everywhere.** This is the default M3 `Card` look. A fixed radius looks rounder on a
   small tile than on a large one.
2. **`CardShape = RoundedCornerShape(6%)`** (chosen). An ID-1 card has 3.18mm corners on a 53.98mm short side
   (≈ 5.9%), and a percent corner scales with the tile, the same way the tile's ratio does.

For the scanner, the choice was a **visual-only** viewfinder or one that **restricts decoding** to the cut-out.

For the widget, the choices were **square tiles** or **card-ratio tiles**.

## Decision

- `object Spacing` in `ui/theme`: `xs 4`, `s 8`, `m 12`, `l 16`, `xl 24`, `xxl 32` (dp). One-off sizes (3dp
  selection borders, the space above the Add button, the 200dp detail header) are named constants next to their
  screen, not scale steps.
- `LoyScanTheme` passes an explicit `Shapes` with M3's default values (4/8/12/16/28). Surfaces that aren't the
  card (the barcode's white box, dialogs) use `small`/`medium`.
- `CardShape = RoundedCornerShape(6%)` is used for every depiction of the card.
- Typography stays M3's defaults. Each screen only picks the right role. Buttons keep M3's default height. A
  screen's main action is full width at the bottom, and secondary actions are `TextButton`s.
- **Scanner viewfinder:** a dim scrim over the preview with a clear card-ratio `CardShape` cut-out and a thin
  white outline, with the hint below it. zxing-cpp still reads the whole frame. There is no moving scan line.
- **Crop frame:** the photo stays visible outside the frame under a scrim, and the frame gets a white `CardShape`
  outline. The pan/zoom maths (`cropTransform`, `photoCrop`) keep their frame-relative contract.
- **Widget:**
  - Tiles have the card ratio and `CardShape`-like corners. Their height is computed from `LocalSize`, because
    Glance has no `aspectRatio`. *Amended by #216 (2026-11-25):* the height comes from the tile's real width through
    a layout of our own ([[2026-11-25-widget-tile-height-from-its-real-width]]). *Implementation note (2026-11-12, on a device):* the sizes come from
    `SizeMode.Responsive` with a fixed set of widths, not `SizeMode.Exact`, and a tile fills its cell's width. With
    `Exact` a launcher's reported sizes decided the layout, and one that reported sizes the widget didn't have got a
    layout cut in half ([[card-widget]]).
  - There is one column below 240dp of widget width and two from 240dp (`LazyVerticalGrid`).
  - A tile shows the store's initials and the card's label, like the list's plain tile.
  - The widget background uses `appWidgetBackground()`, and its padding comes from `Spacing`.
  - `minResizeHeight` goes from 40dp to 90dp, so one tile fits at the minimum width.
  - Corners are square on API 29–30.
  - *Amended by [#182](https://github.com/shockrifle/loyscan/issues/182) (2026-11-24):* from Android 12
    the widget rounds its background with a fixed 12dp and the tiles with 8dp (12dp less the 4dp edge), so they nest
    in the widget's corners, not `CardShape`'s proportion; the widget's edge and the gap between tiles are 4dp, not 8dp ([[card-widget]]).

## Why

- **Plain object:** the widget must share the values, and Glance can't read a Compose `CompositionLocal` from the
  app theme. No screen needs a different scale, so a local would add only indirection.
- **Proportional card corners:** a tile is meant to read as "your card". The list, crop frame, viewfinder and widget
  all show the same object at different sizes, and a percent corner keeps it looking the same at every size. (#182
  later gave the widget's tiles corners that nest in the widget's rounded background instead: inside a widget, the
  tiles read as part of it.)
- **Visual-only viewfinder:** restricting decoding to the box would only make scanning fail more often. A barcode
  just outside the box still scanning is a feature. A scan line counts as animation, which #108 leaves out.
- **Card-ratio widget tiles:** they match the rest of the app. Square tiles would fit more text but look like app
  icons. The 240dp threshold keeps two-column tiles at least ~110dp wide, about as wide as a narrow list tile.
- **Square on API 29–30:** Glance can't round corners there. A rounded drawable per brand colour isn't worth it
  for the share of devices on those versions.
- **M3 defaults for shapes and type:** #108 is polish, not a new visual identity. New colours, branding and the
  launcher icon are out of scope (#75).

## Consequences

- New screens use `Spacing`, `MaterialTheme.shapes` and `CardShape`, not `.dp` literals, apart from named one-off
  constants. [[design-system]] says which step to use where.
- The widget's tiles are no longer full-width label strips, so the "brand-colour tiles" part of
  [[2026-11-04-home-screen-widget]] now reads as card-ratio tiles. Photo tiles and an empty-state message are still
  follow-ups.
- There is no screenshot testing. The before/after screenshots for #108 are manual and go in the PR, with made-up
  cards only. Tests pin the theme values and the geometry the criteria name: grid side padding, barcode inset, no
  clipping at fontScale 2 on a 320dp-wide screen, card-ratio cut-out, crop frame and widget tiles, and the widget's
  column count by width. Pure value tweaks get no tests.
