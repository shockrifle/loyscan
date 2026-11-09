# Adapted copies of four third-party agent skills

**Status:** Active · **Date:** 2026-11-09 · **Commits:** f77fbee, 3169bbd

## Context

Two public skill collections were reviewed for use here: Matt Pocock's skills (https://github.com/mattpocock/skills,
MIT, ~40 skills) and the `ai-utils` repo of seeds, critic agents and one skill (no licence file). LoyScan already
had the planning, test and review loop as its own commands and wiki pages: `/next-task`, `/review-changes`,
`/review-fix` ([[code-review]]), [[tdd]], and a wiki built from the same LLM-Wiki pattern as `ai-utils`' seed.

## Options considered

1. **Install the whole collection as a plugin** (`claude plugin install mattpocock-skills@…`). Self-updating, but
   every model-invoked skill's description is loaded into every session, the skills assume a `GLOSSARY.md`,
   `docs/adr/`, a TypeScript/web stack and their own tracker setup, several duplicate our commands (`code-review`,
   `tdd`, `triage`, `to-spec`, `implement`), and `git-guardrails` blocks the `git push` that `/next-task` makes.
2. **Copy a few skills unchanged.** Cheap, but they would point at files this repo doesn't have and give
   web-only advice (curl, Playwright).
3. **Copy a few skills and adapt them** to the wiki, the Android test harnesses and the public-safe rule.

## Decision

Option 3, for the four skills that fill a gap: `diagnosing-bugs`, `writing-for-agents`, `grilling`, `retro`
(what each does and what changed: [[agent-skills]]). Nothing from `ai-utils` is copied, because it has no licence.

## Why

- **Each fills a gap the existing loop leaves.** The TDD bug-fix loop assumes the cause is known;
  `diagnosing-bugs` covers finding it. We edit commands and `CLAUDE.md` files often with no style reference.
  Open product questions on issues had no structured way to settle them (`grilling`). Nothing proposed
  improvements to the agent setup after a session (`retro`).
- **Adapted, not raw**, because a skill that points at missing files or the wrong stack steers the agent wrong.
  `diagnosing-bugs` also had to learn that card numbers and barcode pictures are personal data in this repo.
- **Rejected from the shortlist:** `tdd` (would compete with [[tdd]]; its seams-first and tautological-test ideas
  could be merged into that page later), `domain-modeling` and `grill-with-docs` (the wiki schema already owns the
  glossary and decisions), the tracker skills (`to-tickets`, `triage`, `wayfinder`, `to-spec`, which need their
  own tracker setup; we use GitHub Issues directly), and stack-specific or unfinished ones.
- **Copies over a plugin**: the adaptations would be lost on every update, and only four of ~40 skills are wanted.

**Update, same day.** The shortlist was revisited one skill at a time: `to-tickets` was adapted after all (GitHub
sub-issues and blocked-by links make it work without the upstream tracker setup); the `tdd` skill's seams and
tests-that-can't-fail ideas went into [[tdd]] and `review-changes.md` instead of a skill; `wizard` waits for #74;
a plan critic was chosen as our own agent run by `/next-task` (not built yet; it goes in with the move to pull
requests); the lesson loop and
the architecture review were skipped; dependency updates went to Dependabot ([[ci]]).

## Consequences

- The copies don't follow upstream; [[agent-skills]] says how to port an upstream change by hand.
- `diagnosing-bugs`, `writing-for-agents` and `grilling` are model-invoked, so their descriptions are in every
  session's context. `retro` and `to-tickets` are user-invoked only.
- The MIT licence text is kept at `.claude/skills/LICENSE-mattpocock-skills`, and each adapted file ends with a
  credit line.
