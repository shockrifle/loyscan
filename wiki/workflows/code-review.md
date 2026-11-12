# Code review

Every change lands through a **pull request**: a branch off `master`, a PR that closes the issue, a review, and a
rebase merge after CI is green on the PR ([[2026-11-09-pull-request-workflow]]; until 2026-11-09 work was committed
straight to `master`). The review runs through project Claude Code commands in `.claude/commands/`. The command
files hold the steps; this page holds the loop and why it looks this way.

## The loop

1. Branch off `master` (`<issue>-<slug>`), commit there, push the branch, open a PR whose body says `Fixes #N`.
2. `/review-changes` — reviews the branch's commits that aren't on `master` plus uncommitted changes (or a given
   commit, range, PR or issue) against the linked issue's acceptance criteria, the test-first rules, [[architecture]], the open
   [[hacks/_index|hacks]] and [[tech-debt/_index|tech-debt]] items, and the wiki checklist in [[CLAUDE]]. It only
   reports; it does not change code.
3. `/review-fix <review text>` — takes a review (from step 2, or from a human or another tool), verifies each
   claim and each proposed fix, and stops with a plan. After approval it implements the plan, commits locally and
   drafts a reply to the reviewer. Push the fixes to the branch.
4. Merge once the review has no blockers or majors and CI is green on the PR ([[ci]]):
   `gh pr merge <p> --rebase --delete-branch`. Each commit lands on `master` as it is, so each must pass the gate
   on its own. CI runs again on `master`; a red run there means `master` is broken and comes first.

GitHub doesn't enforce any of this: branch protection needs a paid plan for a private repo. The commands follow it.

Dependabot's PRs have no issue and go through `/dependency-pr` instead ([[ci]] → Dependabot).

## The end-to-end loop: `/next-task`

`/next-task [issue #n]` runs the whole cycle for one issue: it picks the next open issue (milestone order, then
dependencies and size; offers a choice when several are close; skips one an open PR works on) and plans it. A
**plan critic** checks the plan before any code exists (below), and the user confirms the plan with the seam of
each test. Then it implements it test-first on a branch, opens the PR, and has `/review-changes` run by an
**independent reviewer**. That reviewer is a fresh subagent on a different
model (Sonnet, high effort), not a fork. The main session then fixes the findings with the `/review-fix` method, talking to the
reviewer: it rebuts claims it thinks are invalid, and disputes that survive one exchange go to the user. The
reviewer re-reviews the fixes (at most 3 rounds). Once the gates are green (no blocker/major findings, tests,
coverage and build pass locally, CI green on the PR), it merges the PR, watches CI on `master`, comments on the
issue and offers the review's follow-ups as new issues.

Why it is set up this way:
- **The reviewer gets no context from the implementing session.** Its prompt is fixed in the command and holds
  no plan or rationale, so it judges the code rather than the author's explanation of it. A different model
  adds variety in what it notices, but it is still a Claude model, not a second vendor.
- **Sonnet at high effort, not Fable or Opus.** Until 2026-11-03 the command asked for Fable. This account has no
  Fable access, and Claude Code fell back to Opus without any notice. The subagent was even told it was Fable,
  but every reviewer transcript since 2026-10-08 shows `claude-opus-5-5` as the responding model. An Opus
  reviewer is the same model as the main session. Sonnet is a different model and uses less of the plan's
  allowance, and high effort makes up some of the depth it lacks. If findings get noticeably shallower, use
  `opus`.
- **Check the model from the API traffic, not from labels.** `/next-task` makes it a push gate. Run [[reviewer-model]]
  (`python -I .claude/scripts/reviewer_model.py <agent id> --expect claude-sonnet-5-5 --effort high`). It compares
  what each API request asked for (`params.model`, `params.output_config.effort`) with what answered
  (`message.model`, per message id) in the subagent's transcript (`~/.claude/projects/<project>/<session>/subagents/agent-<id>.jsonl`).
  Not proof of the model:
  - what the agent says about itself;
  - the `.meta.json` next to the transcript, whose `"model"` is the alias it was spawned with. The Fable-era
    reviewers' meta says `fable`, while every request already asked for `claude-opus-5-5`: the fallback happens
    before any request is sent;
  - a text search for `claude-opus-5-5`. It is in every transcript even when Opus never answers: the `advisor` tool
    in the tool list, and the `advisorModel` field on each response. Only an advisor call that ran counts.

  Checked 2026-11-03 (corrected the same day: this said to read the `"model"` field). The camera-slice review of #10
  ran on `claude-sonnet-5-5` at `high` on all 17 requests and responses, with no advisor call. The Fable-era
  reviews of #87 and #65 ran on `claude-opus-5-5` at `medium`: the old command set no effort, so they got the default
  *(inferred)*.
- **The reviewer has the wiki rules but may challenge them.** The prompt tells it that a recorded decision is not
  proof that the code is right.
- **Disagreement is resolved by evidence, then by the user.** Neither side simply obeys the other. This is the
  same "verified, not obeyed" rule as `/review-fix`, plus a way for the reviewer to answer back.
- **Running the command authorizes the branch, its push, the PR, the merge and the issue comment**, for that
  task only. Creating new issues or touching other PRs still needs confirmation.
- **The plan critic** is `.claude/agents/plan-critic.md`: its definition asks for Sonnet at high effort, and gives
  it only Read, Glob and Grep, so it can't change anything. `/next-task` checks the model it really ran on with
  [[reviewer-model]] and reports a mismatch, without blocking on it (not yet checked on a real run, 2026-11-09). Its prompt is the issue and the plan, nothing else, for the same reason as the
  reviewer's. It checks that every acceptance criterion has a test that can fail through a real seam ([[tdd]]), and
  checks the plan against [[architecture]], the wiki checklist and the open hacks and tech debt. The main
  session revises the plan, then shows the user the seams and the findings it rejected. A wrong seam costs a
  rewrite once the code exists, which is why the user confirms the plan before implementation and isn't only
  told about it.

## What the review enforces about tests

The project is test-first ([[2026-10-12-tdd-and-coverage]], [[tdd]]), and `/review-changes` checks it:
- It runs the gate (root `CLAUDE.md`). A failing or newly skipped test, a failing coverage
  gate, or a lint error is a **blocker**, and so is a lowered `minBound`.
- **New logic without tests** is a major finding in every layer: each has a harness. Pure wiring (DI, theme) is
  exempt.
- **A bug fix without a regression test** that fails without the fix is a major finding.
- A fake that no longer matches its interface's contract is a finding, and so is a missed ratchet (coverage rose
  but `minBound` didn't).

`/review-fix` implements fixes test-first too. If the test passes before the fix, the claim was wrong.

## Why it looks this way

- **The branch's range is the unit of review** (`origin/master..HEAD`), the same commits the PR shows. Both
  commands run `git fetch origin` first; without it the range can be stale and include commits that are already on
  `master` (seen 2026-10-09, when the range was the unpushed commits on `master`). A review of a PR by number
  checks the PR out first, so the tests run on its code and not on whatever happens to be checked out.
- **Reviews are verified, not obeyed.** `/review-fix` gives every claim a verdict before anything changes, because
  reviewers (human or AI) misread code or miss a recorded decision.
- **The commands point at the wiki instead of copying it.** Architecture rules, the schema-change steps, the
  public-safety rule and the list of known traps live in the wiki pages; copies in the commands would drift.
- **The review builds the app, which can change the working tree.** Room exports its schema on every build
  (`room.schemaLocation` in `app/build.gradle.kts`). An entity changed without a version bump rewrites the
  committed `app/schemas/…/1.json` — as happened in `4cbc71d` ([[database-schema-changes]]); a bumped version
  whose export wasn't committed creates a new, untracked `<version>.json`. `/review-changes` compares
  `git status` (including untracked files) before and after the build, reports either case as a finding, and
  undoes it: restores the modified file or deletes the new one.
