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
is a named constant next to its screen, not a `.dp` literal.

## Shapes

`LoyScanTheme` passes `LoyScanShapes`: Material 3's default values (`extraSmall` 4, `small` 8, `medium` 12, `large`
16, `extraLarge` 28 dp), set explicitly. Surfaces that aren't the card, such as the barcode's white box, use
`MaterialTheme.shapes`.

## The card's shape

`CardShape` is `RoundedCornerShape(6%)`: an ID-1 card has 3.18 mm corners on a 53.98 mm short side
(`CARD_CORNER_FRACTION`). Every depiction of the physical card uses it with the card's ratio (`CARD_ASPECT_RATIO`,
`ui/CardPhoto.kt`), so the card looks the same at every size.

## Type and buttons

Typography and button heights are Material 3's defaults. A screen's main action is a full-width button at the end of the
screen; secondary actions are `TextButton`s.
