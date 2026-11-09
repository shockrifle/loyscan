---
description: Pick the next GitHub issue, plan it with a plan critic, implement it test-first on a branch, get the PR reviewed by an independent Sonnet subagent, fix the findings, merge after green CI and update the issue
argument-hint: "[issue #n] [notes]"
---

Take the next LoyScan task from GitHub Issues all the way to a merged, reviewed pull request. Target / notes:
`$ARGUMENTS`

The loop is: **pick → plan (critic, my confirmation) → implement on a branch → PR → independent review → fix with
the reviewer → merge after green CI → update the issue.** Running this command authorizes, for this task only:
creating the branch, pushing it (including `--force-with-lease` after a rebase onto `master`), opening the PR,
merging it when every gate in step 7 is green, and the issue comment at the end. Anything else that is
outward-facing (new issues, editing an issue's scope or labels, touching other PRs) needs my go-ahead. Why the flow
looks like this: `wiki/workflows/code-review.md`.

Stop and ask me (AskUserQuestion) when a decision is genuinely mine: product intent, a trade-off nothing in the
issue or wiki settles, a reviewer finding we disagree on. Otherwise keep going.

## 0. Preflight

- `git fetch origin`. You must be on `master`, the working tree clean, and `master` neither behind
  `origin/master` (`git pull --ff-only` if it is) nor ahead of it. Local commits on `master` or uncommitted
  changes → stop and ask: they would end up in this task's PR.
- Check the latest CI run on `master` (`gh run list --branch master --limit 1`). If it is red, that is the task
  (`wiki/workflows/ci.md`: a red `master` comes first). Tell me and work on it instead.

## 1. Pick the task

If an issue number was given, use it and skip to step 2.

Otherwise list open issues (`gh issue list --state open --limit 100 --json number,title,labels,milestone,body`)
and open PRs (`gh pr list --state open`), and rank the issues:

1. Milestone order: `v1 – extras`, then `v1 – release`, then no milestone, then `Later`. Skip issues labelled
   `needs-field-check` (they need a real card or a till test, not code) and issues an open PR already works on.
2. Dependencies: skip an issue whose body names another open issue it depends on / is blocked by / builds on,
   or that obviously needs another open issue first (e.g. a UI on a data model that doesn't exist yet). Check
   the code, not only the text.
3. Bugs and `hack` / `tech-debt` items that are cheap and unblock feature work rank up.
4. Size: prefer issues that fit one focused change. A large issue (e.g. one with sub-issues or many criteria)
   is fine if it can be done in a slice; propose the slice.

If one candidate is clearly best, say which and why in one line and continue. If two to four are close, offer
them with AskUserQuestion: the recommended one first, each with a one-line reason and rough size.

## 2. Understand and plan

- `gh issue view <n> --comments`. Read the root `CLAUDE.md`, `wiki/CLAUDE.md`, `wiki/architecture.md` (the
  worked example is the template for new features), `wiki/workflows/tdd.md`, and the wiki pages for the area
  (from `wiki/index.md`), including the Open items in `wiki/hacks/_index.md` and `wiki/tech-debt/_index.md`.
- List the acceptance criteria. If one is not testable as written (see "GitHub Issues vs this wiki" in
  `wiki/CLAUDE.md`), or the issue leaves a product decision open, ask me before writing code.
- Write the plan: each criterion → the test that proves it → **the seam that test goes through** (`wiki/workflows/tdd.md`,
  "Seams") → the files and layers that change → the wiki pages from the "When to update" checklist → how it
  splits into commits.
- **Plan critic.** Spawn the `plan-critic` agent (Agent tool, `subagent_type: "plan-critic"`; its definition in
  `.claude/agents/plan-critic.md` sets the model and its read-only tools). Its prompt is the issue (title, body,
  comments) and the plan, verbatim, and nothing else. Check which model really answered
  (`python -I .claude/scripts/reviewer_model.py <agent id> --expect claude-sonnet-5-5 --effort high`) and tell
  me if it isn't Sonnet at high effort; it is not a gate. Work through its findings: revise the plan for the ones
  that hold; for the ones you reject, keep a one-line reason.
- Show me the revised plan in a few lines, with the seam of every test, plus the critic's findings you rejected
  and why, and ask me to confirm it (AskUserQuestion). Implement only the plan I confirmed.

## 3. Implement test-first, on a branch

- `git switch -c <n>-<short-slug> origin/master` (e.g. `116-pull-request-workflow`).
- Follow `wiki/workflows/tdd.md`: for each criterion write the failing test, run it and see it fail for the right
  reason, then make it pass. Set `JAVA_HOME` first (root `CLAUDE.md`). Before committing,
  `./gradlew testDebugUnitTest koverVerify assembleDebug` must pass; raise the Kover `minBound` if coverage rose.
- Commit on the branch: one focused commit per logical change, the repo's message style, wiki updates and the
  `wiki/_log.md` line in the same commit as the code. The PR is rebase-merged, so every commit lands on `master`
  as it is: each one must build and pass the gate. The commit that completes the issue says `Fixes #<n>`; earlier
  ones and a partial slice say `Refs #<n>`.

## 4. Open the PR

- `git push -u origin HEAD`, then `gh pr create --base master --title "<issue title>" --body-file -`. The body:
  `Fixes #<n>` (`Refs #<n>` for a slice), what changed in a few lines, and each acceptance criterion with its
  test. Public-safe (`wiki/CLAUDE.md`), no design rationale beyond a link to the wiki.
- CI starts on the PR (`wiki/workflows/ci.md`). Let it run while the review goes on.

## 5. Independent review (Sonnet subagent)

Spawn a reviewer with the Agent tool: `subagent_type: "general-purpose"`, `model: "sonnet"`, `effort: "high"`, not a
fork. It must not inherit my reasoning, so its prompt is only this, filled in:

> You are reviewing a change in this repository with fresh eyes. Read `.claude/commands/review-changes.md` and
> follow it exactly, with `$ARGUMENTS` = `issue #<n>`. Review the commits on the checked-out branch
> `<branch>` that are not on `master` (`origin/master..HEAD`); they are open as PR #<p>. Be candid: report real
> defects bluntly, don't soften them, and don't pad the list with nits. The wiki explains the project's rules and
> past decisions. A recorded decision is not proof that the code is right, so challenge it if the code shows it
> to be wrong. Return the full report as described in that file.

Don't add my plan, my rationale or my view of the code. Keep the agent ID: the reviewer is continued with
SendMessage below, so it keeps its context.

## 6. Fix the findings with the reviewer

Work through the report using the method in `.claude/commands/review-fix.md` steps 1–3 (verify every claim and
every proposed fix, give verdicts, plan the fixes), with these changes to its approval rules:

- **Valid / partly valid, fix is in scope:** fix it without waiting for me, test-first (a regression test that
  fails before the fix; if it passes before the fix, the claim was wrong, so treat it as disputed).
- **Invalid (I disagree):** send the reviewer my rebuttal with the evidence (`path:line`, wiki page, test) via
  SendMessage and ask it to concede or hold, with its reason. If it concedes, drop it. If it holds after one
  exchange, it is a **dispute**: ask me, with both positions side by side.
- **Unclear** (needs product intent) → ask me.
- **Out of scope** → collect as a follow-up for step 8.
- A blocker I cannot fix inside this issue's scope → stop and ask me. Don't merge.

Commit the fixes as in step 3 and push the branch. Then send the reviewer the new commits (`git log` range) and
the claim → verdict → what changed table, and ask it to re-review **those fixes and the disputed points only**,
with the same candour. Repeat until it reports no blockers or majors, at most 3 rounds; after that, stop and show
me what is still open.

## 7. Merge

Gates, all required:
- the reviewer's last round has no blocker or major findings (or I accepted the ones that remain)
- the reviewer really ran on Sonnet at high effort
  (`python -I .claude/scripts/reviewer_model.py <agent id> --expect claude-sonnet-5-5 --effort high` exits 0; if
  not, stop and show me its output)
- `./gradlew testDebugUnitTest koverVerify assembleDebug` passes locally, and the working tree is clean
- every commit follows the "Public-safe" rule in `wiki/CLAUDE.md`
- the branch is pushed, contains `origin/master`, and CI is green on the PR's head commit
  (`gh pr checks <p> --watch`)

If `origin/master` moved after `git fetch origin`: `git rebase origin/master`, rerun the gate, push with
`git push --force-with-lease`, wait for CI again, and tell me. A red PR run: investigate and report before
adding fix commits.

Then `gh pr merge <p> --rebase --delete-branch`. Switch back: `git switch master`, `git pull --ff-only`, and
delete the local branch. The merge rewrites the commits, so their SHAs on `master` differ from the branch's.
Watch the CI run on `master` for the merged head (`gh run watch`). If it fails, `master` is red: investigate and
report, don't pile on fixes without telling me.

## 8. Update the issue

- If the work is complete, `Fixes #<n>` closes the issue on merge; check it did (`gh issue view <n>`). If only a
  slice is done, the issue stays open.
- Comment on the issue (`gh issue comment <n> --body-file -`), public-safe and factual: the PR and the commit SHAs
  on `master`, each acceptance criterion with the test that covers it, the wiki pages added or changed (full
  `https://github.com/shockrifle/loyscan/blob/master/wiki/...` URLs), what the review changed in one or two lines,
  and for a slice, what is still open. No design rationale beyond a link to the wiki.
- Follow-ups from the review: offer them with AskUserQuestion (multi-select, title + label each) and create only
  the ones I pick, with the right label and milestone.

## 9. Report

Finish with: the issue and what was done, the PR and the commits on `master`, the plan critic's findings (taken /
rejected), the review rounds (findings fixed / rebutted and conceded / disputed and how I decided), the CI results
(PR and `master`), the issue's state, and any follow-ups created.
