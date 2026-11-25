# The card screen's top is one brand-colour band

**Status:** Active · **Date:** 2026-11-24 (landed 2026-11-25) · **Commits:** see #210

## Context

The card screen ([[card-detail]]) stacked a 64dp top bar titled with the store's name over a 200dp brand-colour
header with the store's logo, or its name when it had none. That is about 264dp above the card sheet, and the
barcode, the reason to open the screen at a till, came below it. The store's name was on screen three times: the
title, the header and the sheet.

## Options considered

- **a) The logo in the top bar's title slot, no header.** Saves about 200dp, but the logo is small, squeezed between
  the back arrow and the Edit/Delete icons.
- **b) One brand-colour band, about 140dp tall including the status bar's area.** Back and Edit/Delete in its top
  corners, the logo centred across the band below them. Saves about 120dp.
- **c) A collapsing header**: the large band shrinks into the bar as the sheet scrolls. More work, and the barcode
  gains its room only after a scroll.

## Decision

b), chosen by the user while shaping the issue (2026-11-24, #210), and refined on a device the next day: the logo
sits **beside** the buttons, not below them. Built first with the logo in a row of its own under the buttons, a
140dp band left it about 50dp tall, too small. Now the logo's box is `BAND_LOGO_HEIGHT` (128dp) tall, its top in line
with the top of the Back, Edit and Delete icons, centred across the band between the end corner's two buttons and the
same inset at the start (`BAND_LOGO_SIDE_INSET`), with 16dp of band below it. The band is the status bar plus 156dp,
about 180dp on a phone; the issue's "about 140dp" was reworded to this layout. A store without a logo shows its
initials as its tile does (`StoreInitials`, `app/src/main/java/com/bdaniel/loyscan/ui/CardTileFace.kt`), never the
name: the sheet below keeps the label and the store's name. TalkBack reads the logo or initials as a heading with the
store's name. The buttons are black or white, whichever contrasts more with the brand colour (`contrastingTextColor()`),
as the initials are; the top bar drew them in its own `onSurfaceVariant`.

## Why

- The barcode moves up by about 110dp without a scroll (the old top bar and header were 264dp plus the status bar),
  and the logo stays large enough to check at the till that it is the right store's card. a) gains more room but makes
  the logo too small for that; c) gains the room only after a scroll, when the barcode is wanted at once.
- Beside the buttons rather than below them: the band's height then goes to the logo, not to an empty strip beside
  the buttons. Centred with equal insets rather than using all the room between the buttons, so it sits on the
  screen's centre line like the sheet below.
- Initials, not the name, because the band is the store's mark, as on its tile ([[card-list]], #183); the name is text
  on the sheet anyway.

## Consequences

- No `TopAppBar` on the card screen: `StoreBand` in `app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt` keeps
  clear of the system bars a top app bar would (`TopAppBarDefaults.windowInsets`).
- The band is still drawn in the route's colour until the card loads, then the stored one
  ([[2025-12-12-brand-color-in-route]], #226); the `Scaffold` is still the shared element ([[card-transition]]).
- Before the card loads, the band has no heading: the store's name is empty then.
