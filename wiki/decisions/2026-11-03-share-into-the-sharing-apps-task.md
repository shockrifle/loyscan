# A share opens LoyScan in the sharing app's task (`singleTop`)

**Status:** Active · **Date:** 2026-11-03 · **Commits:** see `git log --grep "#77"`

## Context

"Share → LoyScan" (#77) needs `MainActivity` to take an `ACTION_SEND` intent. With the default `standard` launch mode,
each share starts a new LoyScan activity on top of the sharing app (the gallery), and `onNewIntent` never runs. The
issue asks that a share received while LoyScan is already open (`onNewIntent`) starts the same flow, which only
happens with another launch mode.

## Options considered

- **`standard`** (no change): a new LoyScan on every share; the `onNewIntent` handling would almost never run.
- **`singleTop`**: the share still opens over the sharing app, but when LoyScan is already on top of the task the share
  goes to (see Consequences), the share reaches that activity through `onNewIntent` instead of stacking a second one.
- **`singleTask`**: every share brings LoyScan's own task forward and starts the flow there, replacing whatever was
  open in LoyScan.

## Decision

`android:launchMode="singleTop"` on `MainActivity` (`app/src/main/AndroidManifest.xml`), chosen by the product owner on
2026-11-03. A share that reaches the open activity starts the flow over the card list, dropping whatever was open
above it (`openSharedPicture`, `popUpTo<CardList>`). Confirmed the same day after review, knowing that such a share
drops an open card form (Consequences).

## Why

A share is a detour from the sharing app: for a chooser share, Back after the card is saved returns to the gallery the
user came from.
`singleTask` would pull LoyScan's task forward for every share and lose its open screen. Of the two modes that keep a
plain share in the sharing app's task, `singleTop` is the one where `onNewIntent`, which #77 asks for, can run at all.

Correction (review of #77, same day): the first version of this page said `singleTop` keeps every share in the sharing
app's task and that `onNewIntent` runs "when LoyScan is already open". Neither is true for every share; see
Consequences. Inferred from Android's task rules, not yet checked on a device (#77's device check).

## Consequences

- **Which task a share lands in depends on the sender.** A share from the system chooser without
  `FLAG_ACTIVITY_NEW_TASK` starts LoyScan in the sharing app's task, on top of the sharing app. LoyScan can't already be
  on top there, so `onNewIntent` doesn't run. A share sent with `FLAG_ACTIVITY_NEW_TASK` (some apps and system surfaces
  send it) goes to LoyScan's own task and brings it forward. If `MainActivity` is on top of that task, the share
  arrives through `onNewIntent`.
- **A share through `onNewIntent` drops LoyScan's open screen.** The flow opens over the card list, in place of
  whatever was open above it (`openSharedPicture`, `popUpTo<CardList>`). That can be a half-filled card form, which
  is lost without a warning. Back from the picker then returns to the list, and from there LoyScan closes. #77
  requires that Back from the picker leaves the card list, so the form can't stay beneath the picker.
  Under `standard` mode, the same share would have stacked a second activity instead, and the form would have stayed.
- Two LoyScan activities can exist at once: the launcher's, in LoyScan's own task, and one in the sharing app's task.
  They share the database, so a card saved in one shows in the other.
- The intent is handled only on a fresh `onCreate` (`savedInstanceState == null`) and in `onNewIntent`, and an intent
  reopened from recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) is ignored, so a recreated activity doesn't start the
  flow again ([[add-card]] → Share → LoyScan).
