# Bugs (postmortems)

One postmortem per notable bug: `YYYY-MM-DD-<slug>.md` (date = when it was fixed). Skeleton: Symptom / Root
cause / Fix / Lessons — see [[CLAUDE]]. Only for surprising root causes or bug classes likely to recur; skip
anything the diff fully explains.

## Open (understood but not yet fixed)

_(none — suspected issues live in [[tech-debt/_index|tech-debt]] until confirmed)_

## Resolved

- [[2026-10-09-missing-card-throws-from-room]] — a non-null single-row Room query threw for a missing card
  instead of emitting `null` (#30)
- [[2025-12-12-detail-color-flash-on-transition]] — async ViewModel state made the transition's first frames
  colorless (`45a33e1`)
