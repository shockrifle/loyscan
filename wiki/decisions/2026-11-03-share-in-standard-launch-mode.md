# A share gets a LoyScan of its own (standard launch mode)

**Status:** Active · **Date:** 2026-11-03 · **Commits:** see `git log --grep "#83"` · Supersedes
[[2026-11-03-share-into-the-sharing-apps-task]]

`MainActivity` is back to the default `standard` launch mode, and a share that still reaches an open LoyScan through
`onNewIntent` is passed on to a new LoyScan task. Since #189 the share runs in `ShareActivity`, a subclass with the
same launch mode and forward (amendment below). A share sent through the chooser, or with `NEW_TASK` and/or
`SINGLE_TOP`, doesn't replace what LoyScan has open, so a half-filled card form survives it (#83). A sender using
`CLEAR_TOP` or `CLEAR_TASK` reset LoyScan's task until #189; since then such a share starts `ShareActivity` in a task of
its own and LoyScan's task is left alone (Consequences, checked on a device 2026-11-25).

**Amended 2026-11-25 (#189):** shares no longer start `MainActivity`. They start `ShareActivity`, a subclass, with no
task affinity and `documentLaunchMode="always"`: every share is a task of its own, which the launcher and the widget
never reach ([[2026-11-25-launcher-stacked-on-a-share-task]]). This reverses one consequence below, chosen by the
product owner on 2026-11-25: a share sent with `NEW_TASK` alone no longer stacks on LoyScan's task, and Back from it
leaves to the sender instead of returning to the half-filled form; the form stays untouched in LoyScan's own task.
The standard launch mode and the `onNewIntent` forward stand: a share sent with `NEW_TASK | SINGLE_TOP` while a
share's activity is on top still reaches it through `onNewIntent` (seen on a device, 2026-11-25), and is forwarded to
a task of its own. Passages below that the amendment changes are marked *(superseded, #189)*.

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

`standard` launch mode (no `android:launchMode` on `MainActivity`, nor since #189 on `ShareActivity`). `onNewIntent` with a shared picture starts the same
intent in a new LoyScan task (`NEW_TASK | MULTIPLE_TASK | NEW_DOCUMENT`, read grant passed on;
`forwardToATaskOfItsOwn` in `app/src/main/java/com/bdaniel/loyscan/MainActivity.kt`). Chosen by the product owner on
2026-11-03 after the device check.

## Why

No share should cost the user what they were typing. With `standard`, a share with `NEW_TASK` alone stacks a new
LoyScan activity on LoyScan's own task, and Back from its flow returns to the form (seen on a device) *(superseded,
#189: such a share now starts a task of its own, and the form stays in LoyScan's task)*. The
`onNewIntent` path is kept only for senders that ask for `SINGLE_TOP`, and it sends them where chooser shares go.

## Consequences

- Seen on a device (2026-11-03, adb): a `NEW_TASK` share over a half-filled form stacked a second LoyScan in the same
  task; Back twice returned to the form, input intact *(superseded, #189: a `NEW_TASK` share now starts its own task)*. A `NEW_TASK | SINGLE_TOP` share reached `onNewIntent` and the
  form stayed. That adb share carries no read grant, so on the device it took the drop path below. A forward with a
  real grant (the new task reading the picture) has run only in Robolectric, whose `startActivity` doesn't check
  grants. It is not checked on a device: no app seen sends a share with `SINGLE_TOP`.
- *(Superseded, #189: a share starts `ShareActivity` in a task of its own, so these flags no longer reach LoyScan's
  task. Seen on a device, 2026-11-25, adb: a `NEW_TASK | CLEAR_TOP` and a `NEW_TASK | CLEAR_TASK` share over a
  half-filled card form each started a task of their own, and the form kept its input.)* By Android's task rules, a sender using `NEW_TASK | CLEAR_TOP` would finish and recreate the `standard`
  `MainActivity` in LoyScan's task, and `CLEAR_TASK` would clear the task. Both lose an open form, and neither goes
  through `onNewIntent`, so the forward can't catch them. No sender seen does this, and `singleTop` had the same
  exposure *(inferred; not seen on a device)*.
- Passing on a share LoyScan holds no grant for throws: `startActivity` grants an `ACTION_SEND`'s `EXTRA_STREAM` itself
  (`Intent.migrateExtraStreamToClipData`), whatever flags are set. The first version crashed on such a share and took
  the form with it (seen on a device with the adb share). Now the `SecurityException` is caught and logged, and that
  share is dropped with nothing shown. Only a sender that asks for `SINGLE_TOP` and grants nothing hits this.
- A share task still stays in Recents until it is closed with Back, so several LoyScan tasks can be listed at once
  (one per chooser share, as before).
- The device-check details (flags per sender, the task each share landed in) stay on
  [[2026-11-03-share-into-the-sharing-apps-task]] → Device check.
