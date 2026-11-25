# Widget tiles get their height from their real width

**Status:** Active · amends [[2026-11-12-design-tokens-and-card-shape]] (how the tile height is computed; square tiles
below 120dp) ·
**Date:** 2026-11-25 · **Issue:** [#216](https://github.com/shockrifle/loyscan/issues/216)

## Context

A home-screen widget's RemoteViews can't measure their real size, and Glance has no `aspectRatio`. Since #108 a tile
filled its cell's real width, but its height was worked out from the width of the layout the launcher picked
(`widgetTileSize(LocalSize.current.width)`). A launcher doesn't always pick the layout for the widget's real width:
Nova reported sizes the widget didn't have (#108, 2026-11-12), and the Android 12L launcher kept a 410dp layout in a
~215dp widget. A layout shown in a narrower widget then drew taller tiles than a card. #216 wants square tiles below
120dp; a first attempt made the square from the layout's width, and on a phone one-column tiles came out taller than
wide. The render harness (`widget/WidgetRender.kt`) showed the same with card tiles: the 187dp layout in a 110dp
widget gave 102 × 113dp tiles.

## Options considered

1. **Height from the layout's width, with finer layout steps** — what #108 did. It keeps the tile near the card's
   ratio only while the launcher picks the layout for the real width; nothing bounds the error when it doesn't.
2. **A Glance `Image` at the card's ratio that fills the cell** — tried in #108; Glance's `Image` doesn't set
   `adjustViewBounds`, so it kept its intrinsic height.
3. **A layout of our own whose height follows the real width** (chosen) — an `ImageView` with `adjustViewBounds`, as
   wide as the cell, showing a transparent shape at the tile's ratio that is larger than any tile.

## Decision

Each tile is `res/layout/widget_tile.xml`, embedded with `AndroidRemoteViews`: a `RelativeLayout` whose sizer
`ImageView` (`match_parent` × `wrap_content`, `adjustViewBounds`) shows a transparent shape, and a face container
aligned to the sizer's top and bottom that Glance fills with the tile's content. The tile's height is the shape's ratio
at its real width, whatever layout the launcher shows.

The shape is square below 120dp of tile width and the card's above (#216): `widgetTileShape` picks
`widget_tile_square.xml` or `widget_tile_card.xml` from the layout's width. `WIDGET_SIZES` are the four switch points
(110, 128, 240, 252dp); the 16 finer steps existed only to keep a layout-computed height near the card's ratio.
`minResizeHeight` is 110dp, one square tile at the 110dp minimum width.

## Why

- The platform's `ImageView.onMeasure` scales the height with the width when `adjustViewBounds` is on and the height is
  `wrap_content`, capped by the picture's intrinsic height; a picture larger than any tile never hits the cap (the
  earlier failure in option 2 was such a cap, inferred from `ImageView.onMeasure`).
- The face is aligned to the sizer rather than stacked with it in a `FrameLayout`: in a `FrameLayout` every child
  counts toward the height, and a logo (its bitmap plus the 8dp margin) made the tile taller than the sizer.
- Shown by `widget/CardWidgetRenderTest` on the real RemoteViews (Robolectric, Android 12+ collection path).

## Consequences

- The shape still comes from the layout, the only width a RemoteViews layout knows: a tile is square below 120dp only
  where the launcher picks the layout for the widget's real width. Shown another width's layout, it takes that layout's
  shape, never taller than wide (#216's first criterion holds for such a launcher only; agreed with the owner,
  2026-11-25).
- A launcher sizes a widget in whole cells of its own grid; where one cell is 128dp or wider (the Android 14 handheld's
  is 160dp, 2026-11-25), a one-column widget never has square tiles.
- The logo box stays 200 × 120.2dp, a 216dp card tile less the margins, kept from the 16-size layouts; on the widest
  one-column tiles (up to 232dp) a logo is drawn up to 7% larger than it was decoded.
- A tile is never taller than its shape allows, on any launcher, for any layout it picks.
- The layout's width now chooses only the columns and the shape. Where a launcher shows a layout made for another width,
  the columns can be the other width's (two columns in a widget that would show one).
- The tile's height is a view of our own, not a Glance modifier, so Glance's unit test can't see it; the render test
  does.
