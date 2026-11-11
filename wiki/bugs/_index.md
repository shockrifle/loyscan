# Bugs (postmortems)

One postmortem per notable bug: `YYYY-MM-DD-<slug>.md` (date = when it was fixed). Skeleton: Symptom / Root
cause / Fix / Lessons — see [[CLAUDE]]. Only for surprising root causes or bug classes likely to recur; skip
anything the diff fully explains.

## Open (understood but not yet fixed)

_(none — suspected issues live in [[tech-debt/_index|tech-debt]] until confirmed)_

## Resolved

- [[2026-11-11-restored-form-saved-a-duplicate]] — a card form restored after its activity was destroyed mid-save
  could add the card a second time; the new card's id is now kept in the saved state from the start (#99)
- [[2026-10-28-save-navigates-after-back]] — a card save that ended after Back still opened the card, and leaving the
  form could cancel the write (#64)
- [[2026-10-19-zxing-cpp-3-0-databar]] — zxing-cpp 3.0.2 threw for every DataBar result; the scanner swallowed it
  per frame (#5)
- [[2026-10-09-missing-card-throws-from-room]] — a non-null single-row Room query threw for a missing card
  instead of emitting `null` (#30)
- [[2025-12-12-detail-color-flash-on-transition]] — async ViewModel state made the transition's first frames
  colorless (`45a33e1`)
