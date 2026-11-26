# How a store's logo is drawn on its brand colour

**Status:** Active (slice 1 of 3) · **Date:** 2026-11-26 · **Issue:** [#240](https://github.com/shockrifle/loyscan/issues/240)

A logo found online is often in the store's own brand colour, so on a tile of that colour it disappears. The user
picks, per store, how its logo is drawn: **As is**, **Single colour** or **Recolour** (slice 2), plus an **Outline**
(slice 3). This page records the choices made in slice 1; [[store-logo]] describes the behaviour.

## Context

The app ships no logos ([[2026-11-12-no-bundled-store-logos]]); the user brings one ([[2026-11-13-user-supplied-store-logos]]),
cropped to a PNG, and the same file is drawn on four surfaces: the My cards tile (and the logo screen's preview, which
draws the tile), the card screen's band, the store picker's badge and the home-screen widget.

## Options considered

- **Store the treatment, or work it out on every draw.** Working it out from the logo and the brand colour would change
  how a logo looks when the colour changes, and couldn't hold the user's own choice.
- **Where the pixels are changed.** A Compose `ColorFilter` would cover three surfaces, but not the widget (Glance
  tints only by alpha), and a colour matrix can't weigh a pixel by the whole logo's brightness. A stored, re-encoded
  second file would double the files to keep in step with the logo.
- **Which end of Single colour is solid.** Dark → solid (the issue's literal wording) loses a white-on-transparent logo;
  light → solid loses a black wordmark.

## Decision

- Three nullable columns on `stores` (`logoStyle`, `logoTint`, `logoOutline`; null = As is), schema v5
  ([[database-schema-changes]]), read as `Store.logoTreatment` (`domain/LogoTreatment.kt`). They belong to the user: the
  catalogue sync never writes them ([[store-catalogue]]); a new logo, or none, resets them (`StoreDao.setLogoReturningDropped`);
  editing a custom store's colour keeps them. Setting the same logo again isn't a new logo and keeps them.
- The pixels are changed in one pure function, `inSingleColour` (`app/src/main/java/com/bdaniel/loyscan/ui/LogoTreatments.kt`),
  on the decoded bitmap: by a Coil `Transformation` (`LogoTreatmentTransformation`, in `StoreLogoImage`) on the three
  Compose surfaces, and after decoding in `WidgetPictures` for the widget. The logo file is never changed.
- Single colour: every pixel in the tint, its opacity from its WCAG relative luminance. **Which end is solid follows the
  logo** (the owner's choice, 2026-11-26): on a mostly dark logo the dark parts, on a mostly light one the light parts.
  "Mostly" is by area, the mean CIE lightness L* (weighed by each pixel's opacity) against mid-grey: linear luminance
  called a logo a third white "light" (seen while writing `LogoTreatmentsTest`). Opacity is scaled to the logo's own
  darkest (or lightest) pixel, so a logo in one colour, the blending case, comes out solid.
- Choosing Single colour saves white or black, whichever contrasts more with the brand colour (`contrastingTextColor`),
  unless a colour is already saved; the swatch row offers white, black, then the custom store palette.

## Why

The treatment is the user's, so it is stored, and stored on the store, since all its cards share the logo. One pixel
function behind both image paths keeps the four surfaces from drifting apart, as `CardTileFace` does for the tile and
the preview (#192). Choosing the solid end per logo keeps both kinds of downloaded logo (dark wordmark, white mark for
dark backgrounds) visible without asking the user a question they can't easily answer.

## Consequences

- Coil caches a treated logo under a key with its style and tint, so a change draws again; the widget's bitmap is
  reused only when its size and its treatment are unchanged.
- A treated bitmap is a new ARGB_8888 copy: the image loader's cached input is never changed.
- Recolour, the contrast check with its pre-selection, and Outline are not implemented yet (slices 2 and 3 of #240).
