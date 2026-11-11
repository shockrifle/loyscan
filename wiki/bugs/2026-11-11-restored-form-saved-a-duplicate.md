# A card form restored mid-save could save the card twice

**Fixed in:** #99 · **Date:** 2026-11-11 · **Area:** [[add-card]], [[card-photos]]

## Symptom

Not seen by a user; found in the review of #95 by reading the code. When the activity was destroyed with the add
form still on the back stack while its save ran (the process alive: "Don't keep activities", a background kill), the
save still finished (`withContext(NonCancellable)`) and added the card. Back in the app, the form was restored and
looked unsaved; tapping Save added the same card a second time, under a new id.

## Root cause

The new card's id was drawn (`UUID.randomUUID()`) inside `CardFormViewModel.save`, and "saved" lived only in the
ViewModel's in-memory `Progress`. A restored ViewModel starts from the state the activity saved in
`onSaveInstanceState`, which runs when the activity stops, before a save still in flight ends. So nothing the save
learned could reach the restored form. Writing `savedCardId` into the `SavedStateHandle` after the insert would not
have fixed it either: that write goes to the old handle, after the snapshot was taken. A test that restores from the
same `SavedStateHandle` object reads that write anyway and would pass.

## Fix

The id is fixed when the add form is first created and written to the saved state at once (key `newCardId`), so
every snapshot carries it. A new card's save adds the card only when no card has that id yet, and updates it
otherwise. The restored form drafts the same card, and its Save updates it, keeping any change made after the
restore. Tests: `CardFormViewModelTest.aSaveThatEndsAfterTheFormWasCleared_isNotSavedAgainByTheRestoredForm`,
`aFormRestoredAfterItsCardWasSaved_savesLaterChangesToThatCard`, `twoAddForms_saveTwoCards`.

Not covered: a restored form whose save starts while the first insert is still running. Both see no card and both
add; Room's primary key rejects the second (`@Insert` aborts on conflict), the form says the card couldn't be saved,
and the retry updates. The window is one insert against an activity being recreated.

Accepted: a restored form's Save writes the form's fields over the card, so an edit made to that card elsewhere in
the meantime (opened from the home-screen widget, then Back to the old form) is overwritten. Before the fix that Save
added a duplicate instead. Removing or re-cropping, in the restored form, a photo the first save stored deletes that
file at once, as it did before the fix; if the form is then left without saving, the card names a missing file and
shows no photo for that side (#87).

## Lessons

- State a restored screen must know goes into the saved state **before** the work it describes starts, not when it
  ends: the activity's snapshot may already have been taken.
- Test a restore from a snapshot of the handle taken where the activity would save its state, not from the same
  handle object ([[tdd]] → Fixtures and doubles).
