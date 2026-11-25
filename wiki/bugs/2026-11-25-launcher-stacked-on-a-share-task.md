# The launcher reopened a share left open, and Back walked its flow

**Fixed in:** #189 · **Date:** 2026-11-25 · **Area:** [[add-card]], [[store-logo]], [[card-widget]]

## Symptom
After a picture was shared to LoyScan and the user went Home (on the saved card, or partway through the flow), opening
LoyScan from the launcher showed the card list, but Back from it went to the old share's screens (the store picker,
then that task's list) before leaving: 3 Backs from the list instead of 1. A widget tap in the same state opened its
card inside the share's task, replacing the share flow, and left the main LoyScan task untouched (#189).

## Root cause
A share started `MainActivity` (or the "Set as store logo" alias of it) in a task of its own (the chooser sends
`NEW_TASK | MULTIPLE_TASK | NEW_DOCUMENT`, [[2026-11-03-share-into-the-sharing-apps-task]]). That task had LoyScan's
default task affinity and `MainActivity` as its component, and it was the newest LoyScan task. So:

- the launcher's `MAIN/LAUNCHER` start (flags `0x10200000`) was matched to the share task, and because the task's base
  intent was the share, not the launcher's, Android stacked a second `standard` `MainActivity` on it (dumpsys: the
  share task held `[MainActivity, MainActivity]`);
- the widget's `openCardIntent` (`MainActivity`, `SINGLE_TOP`) reached the share task's activity through `onNewIntent`.

Nothing inside the app's navigation was wrong: every `NavController` back stack ended as the directions tests expect.
Robolectric can't run the platform's task matching, so only a device showed it.

## Fix
Shares run in `ShareActivity` (`MainActivity.kt`, a subclass: the same app), declared with `android:taskAffinity=""`
and `android:documentLaunchMode="always"`, and holding the `SEND image/*` filter; the `.ShareAsStoreLogo` alias targets
it, and `forwardToATaskOfItsOwn` forwards to it. The launcher and the widget still start `MainActivity`, so neither
matches a share task by component or by affinity. `documentLaunchMode="always"` makes every share a task of its own
whatever flags its sender sets: with the affinity change alone, a share sent with `NEW_TASK` only (imitated with adb)
was matched to an open share task by component and stacked on its flow.

Tests (`ShareIntentTest`, the merged manifest through the package manager): a picture share resolves to
`ShareActivity` and never `MainActivity`; the launcher resolves to `MainActivity` and never `ShareActivity`;
`ShareActivity`'s `taskAffinity` is empty while `MainActivity`'s is the package; its `documentLaunchMode` is
`always`; the alias targets it; the forward goes to it. `ShareToLoyScanTest` runs the share flows end to end in
`ShareActivity`. The task-level behaviour itself is a device check, below.

## The walk (Android 14 test device, debug build, 2026-11-25)
Back presses from the last screen until LoyScan left the screen; stacks from `dumpsys activity activities`.

| Path | Ends on | Backs to leave | Before the fix | Test behind the in-app stack |
|---|---|---|---|---|
| Launcher → list | list | 1 | same | — |
| Widget tap, cold (process killed) | card over list | 2 | same | `WidgetTapOpensCardTest`, `WidgetOpenCardDirectionsTest` |
| Widget tap, warm, over an edit form | card over list | 2 | same | `WidgetOpenCardDirectionsTest.openCard_whileACardFormIsOpen_…` |
| Share → add card → save | card over list, then the sender | 2 | same | `AddCardDirectionsTest.savingTheForm_opensTheNewCardOverTheList_…` |
| Share → Set as store logo → crop | logo screen, list, then the sender | 2 | same | `AddCardDirectionsTest.aStoreTappedInLogoMode_…inPlaceOfThePicker` |
| Add card, catalogue store | card over list | 2 | same | `AddCardDirectionsTest.savingTheForm_…` |
| Add card, Other card | card over list | 2 | same | `AddCardDirectionsTest.creatingACustomStore_…`, `savingTheForm_…` |
| Edit from the card / from the list | card over list | 2 | same | `AddCardDirectionsTest.savingAnEdit_…`, `editOnTheList_…` |
| Store logo from the picker | logo → picker → list | 3 | same | `AddCardDirectionsTest.aStoresLogoButton_…` |
| Store logo from a custom store's form | logo → form → picker → list | 4 | same | `AddCardDirectionsTest.aCustomStoresLogoButton_…` |
| **Share left open, Home, launcher** | list | **1** | 3, through the old share flow | device only |
| **Share left open, Home, widget tap** | card over list, in the main task | 2; the share task kept | 2, in the share task, its flow replaced | device only |
| **A second share (chooser) over one left open** | own task; Back to the sender | 2 | — | device only |
| **`NEW_TASK`-only share (adb) over one left open** | own task | — | stacked on the open share's flow | device only |

After the fix, the share task showed as `I=com.bdaniel.loyscan/.ShareActivity` (no affinity) beside the main task's
`A=…:com.bdaniel.loyscan`. Shares started from the adb shell have no sender task, so Back from their root falls
through to whatever task is below, another share task included; a real share returns to its sender.

Seen, not changed: "Find a logo online" opens Chrome in Chrome's own task, and Back from it returns to LoyScan's logo
screen. A picture shared from Chrome back to LoyScan stacks its share task over Chrome, so Back after that flow walks
Chrome's pages and then LoyScan's earlier logo screen: the sender's history, not LoyScan's.

## Lessons
- An activity that external apps start into tasks of their own (shares) shouldn't be the launcher's activity, or
  share its affinity: the launcher and explicit intents find tasks by component and affinity, and a standard activity
  stacks on whatever task they find.
- Back-stack bugs can sit outside `NavController`; walk the paths on a device with `dumpsys activity activities` before
  looking for a navigation test that fails.
