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
  reaches that activity through `onNewIntent` instead of stacking a second one.
- **`singleTask`**: every share brings LoyScan's own task forward and starts the flow there, replacing whatever was
  open in LoyScan.

## Decision

`android:launchMode="singleTop"` on `MainActivity` (`app/src/main/AndroidManifest.xml`), chosen by the product owner on
2026-11-03. A share that reaches the open activity starts the flow over the card list, dropping whatever was open
above it (`openSharedPicture`, `popUpTo<CardList>`).

## Why

A share is a detour from the sharing app: after the card is saved, Back returns to the gallery the user came from.
`singleTask` would pull LoyScan's task forward and lose its open screen; `standard` would leave `onNewIntent`
unreachable.

## Consequences

- Two LoyScan activities can exist at once: the launcher's, in LoyScan's own task, and one in the sharing app's task.
  They share the database, so a card saved in one shows in the other.
- The intent is handled only on a fresh `onCreate` (`savedInstanceState == null`) and in `onNewIntent`, and an intent
  reopened from recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) is ignored, so a recreated activity doesn't start the
  flow again ([[add-card]] → Share → LoyScan).
