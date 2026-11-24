# Design system

The shared values the screens and the widget are built from (#108): a spacing scale, the theme's shapes, and one
shape for the card. Why they look like this: [[2026-11-12-design-tokens-and-card-shape]]. Code:
`app/src/main/java/com/bdaniel/loyscan/ui/theme/Spacing.kt` and `Shapes.kt`, pinned by `ui/theme/ThemeTest`.

## Spacing

`Spacing` is a plain object, so the Glance widget reads the same values as the app.

| Step | dp | Use it for |
|---|---|---|
| `xs` | 4 | Space inside a group: a caption under the thing it explains |
| `s` | 8 | Space between closely related items: the label and the store name, small padding inside a tile |
| `m` | 12 | Gaps between tiles and thumbnails, between a row's items |
| `l` | 16 | Screen side padding, padding inside a sheet or card, gaps between form fields |
| `xl` | 24 | Space between groups on a screen (label and store / barcode and number / note and photos) |
| `xxl` | 32 | Padding around an empty-state message |

A size that is no step of the scale (a 3dp selection border, the room left for the Add button, the 200dp card header)
is a named constant next to its screen (`val UPPER_CASE = 3.dp`), not a `.dp` literal. `testing/DesignValuesTest`
fails on a non-zero `.dp` literal or a `RoundedCornerShape` outside `ui/theme/` ([[tdd]]).

## Shapes

`LoyScanTheme` passes `LoyScanShapes`: Material 3's default values (`extraSmall` 4, `small` 8, `medium` 12, `large`
16, `extraLarge` 28 dp), set explicitly. Surfaces that aren't the card, such as the barcode's white box, use
`MaterialTheme.shapes`.

## The card's shape

`CardShape` is `RoundedCornerShape(6%)`: an ID-1 card has 3.18 mm corners on a 53.98 mm short side
(`CARD_CORNER_FRACTION`). Every depiction of the physical card in the app uses it with the card's ratio (`CARD_ASPECT_RATIO`,
`ui/CardPhoto.kt`), so the card looks the same at every size. The widget's tiles keep the ratio but take their
corners from the widget's background (#182, below).

## Card cut-outs

The scanner's viewfinder and the crop frame are a card cut-out (`ui/CardCutOut.kt`): `scrimAround` dims a node's
area with `CUT_OUT_SCRIM` (black at 60%) except a `CardShape` hole where `cardCutOut` lays out the cut-out, which gets a
2dp white outline (`CUT_OUT_OUTLINE`). The two nodes record their place in the root in a shared `CardCutOutState`, so
the hole follows the layout. Robolectric doesn't run draw code, so the scrim and the outline are checked by hand; the
cut-out's size and place are tested through its node ([[barcode-scanner]]).

## The widget

The widget reads `Spacing` too, since it can't read the app's theme. Its edge and the gap between its tiles are both
`xs` (4dp, `WIDGET_SPACING`), on purpose tighter than the `m` gap between the list's tiles: a widget is small, and the
tiles nest in its rounded background (#182). Glance has no grid spacing, so half of it pads the grid and half each tile.
Its tiles have the card ratio. Their corners are not `CardShape`'s: from Android 12 they are the widget background's
radius (`system_app_widget_background_radius`) less the 4dp edge, so they nest concentrically; before Android 12 the
widget and its tiles are square (`widget/WidgetLayout.kt`, [[card-widget]]).

## Type and buttons

Typography and button heights are Material 3's defaults. A screen's main action is a full-width button at the end of the
screen (on a form, the last item of the scroll, not pinned); secondary actions are `TextButton`s.

## Checking a layout

There is no screenshot testing. Every screen has a test at font scale 2 on a 320dp-wide screen
(`assertTextFitsAndDoesNotOverlap`, [[tdd]]), and geometry the design names is asserted through nodes' bounds: the list
grid's insets and gaps, the barcode surface's group gap, the viewfinder's and crop frame's ratio, the widget's columns
and tile size. Colours, the scrim and corner rounding (the widget's concentric corners too) are checked by hand (light and dark, a 360×640 and a large phone,
font scale 2), since Robolectric doesn't draw.
