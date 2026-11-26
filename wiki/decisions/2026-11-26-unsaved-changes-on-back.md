# Back from a changed form asks first; the opening state is derived, not saved

**Status:** Active · **Date:** 2026-11-26 · **Commits:** see #209's PR

## Context

The card form dropped an edit without a word on Back (#209). A form with changes now asks "Save changes?"
(editing) or "Discard this card?" (adding), with **Discard** and the form's own
**Save** (or **Save anyway**), or **Discard** and **Keep editing** when it can't be saved. "Changed" means a value
differs from what the form opened with, so typing and then deleting is not a change, and that opening state has to
survive rotation and process death.

## Options considered

1. **Save a snapshot of the opening values** in the `SavedStateHandle` when the form first loads, and compare with it.
2. **Derive the opening values** from what the form already has: the route (a new card's store, scan or shared
   barcode) or the repository (the edited card).
3. **Count any input that was touched** (the inputs are `null` until changed), without comparing values.

## Decision

Option 2. `CardFormState.hasChanges` compares the values as a save would store them with the opening ones: the store,
the number and its scanned format, the label and note (trimmed, as save trims them), the photos and the two "Show
photo on …" switches (they are saved with the card). It is `false` until the form has loaded and once saving has
started: Back during a save then leaves and the save still ends, as before ([[2026-10-28-save-navigates-after-back]],
#64).

Discard (`CardFormViewModel.discard`) deletes the photos picked in the form, except those a save was handed (#141); the
edited card's own photos stay. It is the real-close signal [[2026-11-03-card-photos-as-private-files]] said the
ViewModel lacked; `onCleared` still deletes nothing (#95).

## Why

The ViewModel already keeps each input in the `SavedStateHandle` as `null` until changed, and reads the rest from the
route and the repository, so the opening values outlast process death with no new keys and nothing to keep in step.
A snapshot (option 1) would be one more piece of state to restore, and it would have to be taken at the right moment:
before the edited card loads there is nothing to snapshot. Option 3 fails "typed then reverted is no change".

Trimmed comparison and the switches were confirmed with the project owner when the plan was agreed (2026-11-26).

## Consequences

- A form restored after its save stored the card (#99, #141) compares an edit with the stored card, so Back leaves
  without asking when the edit is already saved; a restored add form still asks, and Discard keeps the photos that
  save was handed.
- The crop screen's own `BackHandler` still takes Back while it is open; the form's is disabled while cropping, so a
  restore with the crop open (both composed at once, the form's first) can't reverse that.
- Tests reach the dialog's window through Robolectric (`testing/SystemBack.kt`, [[tdd]]).
