# A share gets a LoyScan of its own (standard launch mode)

**Status:** Active · **Date:** 2026-11-03 · **Commits:** see `git log --grep "#83"` · Supersedes
[[2026-11-03-share-into-the-sharing-apps-task]]

`MainActivity` is back to the default `standard` launch mode, and a share that still reaches an open LoyScan through
`onNewIntent` is passed on to a new LoyScan task. A share never replaces what LoyScan has open, so a half-filled card
form survives every share (#83).

## Context

#77 made `MainActivity` `singleTop` so that a share sent while LoyScan was open would reach that activity through
`onNewIntent` ([[2026-11-03-share-into-the-sharing-apps-task]]). The flow then opens over the card list
(`openSharedPicture`, `popUpTo<CardList>`), replacing whatever was open. The device check of #84 showed:

- Every share through the system chooser (Google Photos, ASUS Gallery, screenshot preview, Android 15) arrives with
  `NEW_TASK | MULTIPLE_TASK | NEW_DOCUMENT` and starts a new LoyScan task, whatever the launch mode. `onNewIntent`
  never ran for them, so `singleTop` bought nothing there.
- A share sent with `NEW_TASK` alone (imitated with `adb shell am start`) reached the open activity through
  `onNewIntent` under `singleTop`, and a half-filled card form was replaced without a warning.

## Options considered

- **Keep `singleTop`**: its only observed effect was losing the form.
- **`standard`, and ignore a share in `onNewIntent`**: `onNewIntent` still runs for a sender that sets
  `FLAG_ACTIVITY_SINGLE_TOP` itself; ignoring it would bring LoyScan forward and do nothing with the picture.
- **`standard`, and pass a share in `onNewIntent` on to a new task**: the share behaves as a chooser share does, and
  the open screen stays.

## Decision

`standard` launch mode (no `android:launchMode` on `MainActivity`). `onNewIntent` with a shared picture starts the same
intent in a new LoyScan task (`NEW_TASK | MULTIPLE_TASK | NEW_DOCUMENT`, read grant passed on;
`forwardToATaskOfItsOwn` in `app/src/main/java/com/bdaniel/loyscan/MainActivity.kt`). Chosen by the product owner on
2026-11-03 after the device check.

## Why

No share should cost the user what they were typing. With `standard`, a share with `NEW_TASK` alone stacks a new
LoyScan activity on LoyScan's own task, and Back from its flow returns to the form (seen on a device). The
`onNewIntent` path is kept only for senders that ask for `SINGLE_TOP`, and it sends them where chooser shares go.

## Consequences

- Seen on a device (2026-11-03, adb): a `NEW_TASK` share over a half-filled form stacked a second LoyScan in the same
  task; Back twice returned to the form, input intact. A `NEW_TASK | SINGLE_TOP` share reached `onNewIntent` and the
  form stayed.
- Passing on a share LoyScan holds no grant for throws: `startActivity` grants an `ACTION_SEND`'s `EXTRA_STREAM` itself
  (`Intent.migrateExtraStreamToClipData`), whatever flags are set. The first version crashed on such a share and took
  the form with it (seen on a device with the adb share). Now the `SecurityException` is caught and logged, and that
  share is dropped with nothing shown. Only a sender that asks for `SINGLE_TOP` and grants nothing hits this.
- A share task still stays in Recents until it is closed with Back, so several LoyScan tasks can be listed at once
  (one per chooser share, as before).
- The device-check details (flags per sender, the task each share landed in) stay on
  [[2026-11-03-share-into-the-sharing-apps-task]] → Device check.
