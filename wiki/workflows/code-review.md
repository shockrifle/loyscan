# Code review

Work lands directly on `master` with no pull requests, so review happens **locally, before the push**, through two
project Claude Code commands in `.claude/commands/`. The command files hold the steps; this page holds the loop
and why it looks this way.

## The loop

1. Commit locally on `master`.
2. `/review-changes` — reviews the unpushed commits plus uncommitted changes (or a given commit, range, PR or
   issue) against the linked issue's acceptance criteria, the unit tests, [[architecture]], the open
   [[hacks/_index|hacks]] and [[tech-debt/_index|tech-debt]] items, and the wiki checklist in [[CLAUDE]]. It only
   reports; it does not change code.
3. `/review-fix <review text>` — takes a review (from step 2, or from a human or another tool), verifies each
   claim and each proposed fix, and stops with a plan. After approval it implements the plan, commits locally and
   drafts a reply to the reviewer.
4. Push once the review has no blockers.

## Why it looks this way

- **No PRs, so the unpushed range is the unit of review.** Both commands run `git fetch origin` first; without it
  the `origin/master..HEAD` range can be stale and include commits that are already pushed (seen 2026-10-09).
- **Reviews are verified, not obeyed.** `/review-fix` gives every claim a verdict before anything changes, because
  reviewers (human or AI) misread code or miss a recorded decision.
- **The commands point at the wiki instead of copying it.** Architecture rules, the schema-change steps, the
  public-safety rule and the list of known traps live in the wiki pages; copies in the commands would drift.
- **The review builds the app, which can rewrite tracked files.** Room exports its schema on every build
  (`room.schemaLocation`, `app/build.gradle.kts:28`), so an entity changed without a version bump rewrites the
  committed `app/schemas/…/1.json` — as happened in `4cbc71d` ([[database-schema-changes]]). `/review-changes`
  compares `git status` before and after the build, reports such a change as a finding and restores the file.
