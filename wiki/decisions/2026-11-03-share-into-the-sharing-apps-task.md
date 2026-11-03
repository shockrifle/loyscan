# A share opens LoyScan through the chooser (`singleTop`)

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

Correction (device check, #84, same day): on the device checked, a chooser share lands in neither the sharing app's
task nor LoyScan's own: it starts a new LoyScan task of its own, and `onNewIntent` never ran. See Device check. The
page's title said "in the sharing app's task" until then; the file name keeps that wording so links resolve.

## Device check

2026-11-03, Android 15 (an ASUS phone), debug build of `33180ca`, read from `adb shell dumpsys activity activities`. A
half-filled card form was open in LoyScan's own task during each share.

| Sender | Intent flags LoyScan got | Task |
|---|---|---|
| Google Photos → Share → chooser | `0x1b080001` | new LoyScan task |
| ASUS Gallery → Share → chooser | `0x1b080001` | new LoyScan task |
| Screenshot preview → Share → chooser (`com.android.systemui`), over Google Photos | `0x1b080003` | new LoyScan task |
| Same, taken while a LoyScan task was in front | `0x1b080003` | new LoyScan task |

- `0x1b080001` is `NEW_TASK | MULTIPLE_TASK | NEW_DOCUMENT | FORWARD_RESULT | PREVIOUS_IS_TOP | GRANT_READ_URI_PERMISSION`
  (`…03` adds the write grant). With `MULTIPLE_TASK`, every share starts a new task, so no share reached an open
  `MainActivity` through `onNewIntent`, and the open card form in LoyScan's own task kept its input. Three different
  senders gave the same flags, so they most likely come from the system sharesheet, not from the senders
  *(inferred; not checked in the platform source)*.
- Back from the card list in a share's task closes that task and returns to the sharing app (Google Photos), as the
  Why above wanted. A share task left open stays in Recents, so several LoyScan tasks can be listed at once.
- `adb shell am start` with LoyScan's launcher intent (`MAIN`/`LAUNCHER`, `NEW_TASK | RESET_TASK_IF_NEEDED`) brought
  the most recently used LoyScan task forward, which after a share was the share's task, not the one with the form.
  Not tried with a real launcher tap.
- A sender that skips the chooser, imitated with `adb shell am start -a android.intent.action.SEND -t image/png
  --eu android.intent.extra.STREAM <content uri> -f 0x10000001 -n com.bdaniel.loyscan/.MainActivity` (`NEW_TASK` and the
  read grant only) while LoyScan's own task showed a half-filled card form: the share went to that activity through
  `onNewIntent` ("delivered to currently running top-most instance"), the form was replaced without a warning, and
  Back showed the card list. (The picture itself said "couldn't be opened": the shell can't grant a MediaStore
  picture.) So the drop in Consequences happens; whether any real app sends a share this way is not known (#83).

## Consequences

- **Which task a share lands in depends on the flags it arrives with.** Every share checked on a device arrived with
  `MULTIPLE_TASK | NEW_DOCUMENT` and started a new LoyScan task (Device check). By Android's task rules, a share
  without `NEW_TASK` would start LoyScan in the sharing app's task, and one with `NEW_TASK` alone would go to
  LoyScan's own task and, if `MainActivity` is on top there, arrive through `onNewIntent` (seen with a share sent
  through adb, Device check).
- **A share through `onNewIntent` drops LoyScan's open screen** (seen with a share sent through adb, not with any app checked; #83). The flow opens over the card list, in place of
  whatever was open above it (`openSharedPicture`, `popUpTo<CardList>`). That can be a half-filled card form, which
  is lost without a warning. Back from the picker then returns to the list, and from there LoyScan closes. #77
  requires that Back from the picker leaves the card list, so the form can't stay beneath the picker.
  Under `standard` mode, the same share would have stacked a second activity instead, and the form would have stayed.
- Several LoyScan activities can exist at once: the launcher's, in LoyScan's own task, and one per share, each in its
  own task. They share the database, so a card saved in one shows in the others.
- The intent is handled only on a fresh `onCreate` (`savedInstanceState == null`) and in `onNewIntent`, and an intent
  reopened from recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) is ignored, so a recreated activity doesn't start the
  flow again ([[add-card]] → Share → LoyScan).
