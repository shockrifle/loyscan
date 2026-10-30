---
description: Pick the next GitHub issue, implement it test-first, get it reviewed by an independent Fable subagent, fix the findings, push and update the issue
argument-hint: "[issue #n] [notes]"
---

Take the next LoyScan task from GitHub Issues all the way to a pushed, reviewed change. Target / notes:
`$ARGUMENTS`

The loop is: **pick → implement → independent review → fix with the reviewer → push → update the issue.** Running
this command authorizes the push to `master` and the issue comment at the end, but only when every gate below is
green. Anything else that is outward-facing (new issues, editing an issue's scope or labels) needs my go-ahead.

Stop and ask me (AskUserQuestion) when a decision is genuinely mine: product intent, a trade-off nothing in the
issue or wiki settles, a reviewer finding we disagree on. Otherwise keep going.

## 0. Preflight

- `git fetch origin`; the working tree must be clean and `master` must not be behind `origin/master`
  (`git pull --ff-only` if it is). Unpushed local commits or uncommitted changes → stop and ask: they would be
  reviewed and pushed together with this task.
- Check the latest CI run on `master` (`gh run list --branch master --limit 1`). If it is red, that is the task
  (`wiki/workflows/ci.md`: a red `master` comes first). Tell me and work on it instead.

## 1. Pick the task

If an issue number was given, use it and skip to step 2.

Otherwise list open issues (`gh issue list --state open --limit 100 --json number,title,labels,milestone,body`)
and rank them:

1. Milestone order: `v1 – extras`, then `v1 – release`, then no milestone, then `Later`. Skip issues labelled
   `needs-field-check`: they need a real card or a till test, not code.
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
- Write a short plan: the criteria → the tests that prove each → the files and layers that change → the wiki
  pages from the "When to update" checklist → how it splits into commits. Show it to me in a few lines and
  continue unless it contains a decision I should make.

## 3. Implement test-first

Follow `wiki/workflows/tdd.md`: for each criterion write the failing test, run it and see it fail for the right
reason, then make it pass. Set `JAVA_HOME` first (root `CLAUDE.md`). Before committing,
`./gradlew testDebugUnitTest koverVerify assembleDebug` must pass; raise the Kover `minBound` if coverage rose.

Commit locally on `master`: one focused commit per logical change, the repo's message style, wiki updates and the
`wiki/_log.md` line in the same commit as the code. The commit that completes the issue says `Fixes #<n>`;
earlier ones and a partial slice say `Refs #<n>`. Do not push yet.

## 4. Independent review (Fable subagent)

Spawn a reviewer with the Agent tool: `subagent_type: "general-purpose"`, `model: "fable"`, not a fork. It must
not inherit my reasoning, so its prompt is only this, filled in:

> You are reviewing a change in this repository with fresh eyes. Read `.claude/commands/review-changes.md` and
> follow it exactly, with `$ARGUMENTS` = `issue #<n>`. Review the unpushed commits on `master`
> (`origin/master..HEAD`). Be candid: report real defects bluntly, don't soften them, and don't pad the list
> with nits. The wiki explains the project's rules and past decisions. A recorded decision is not proof that
> the code is right, so challenge it if the code shows it to be wrong. Return the full report as described in
> that file.

Don't add my plan, my rationale or my view of the code. Keep the agent ID: the reviewer is continued with
SendMessage below, so it keeps its context.

## 5. Fix the findings with the reviewer

Work through the report using the method in `.claude/commands/review-fix.md` steps 1–3 (verify every claim and
every proposed fix, give verdicts, plan the fixes), with these changes to its approval rules:

- **Valid / partly valid, fix is in scope:** fix it without waiting for me, test-first (a regression test that
  fails before the fix; if it passes before the fix, the claim was wrong, so treat it as disputed).
- **Invalid (I disagree):** send the reviewer my rebuttal with the evidence (`path:line`, wiki page, test) via
  SendMessage and ask it to concede or hold, with its reason. If it concedes, drop it. If it holds after one
  exchange, it is a **dispute**: ask me, with both positions side by side.
- **Unclear** (needs product intent) → ask me.
- **Out of scope** → collect as a follow-up for step 7.
- A blocker I cannot fix inside this issue's scope → stop and ask me. Don't push.

Commit the fixes as in step 3. Then send the reviewer the new commits (`git log` range) and the claim → verdict →
what changed table, and ask it to re-review **those fixes and the disputed points only**, with the same
candour. Repeat until it reports no blockers or majors, at most 3 rounds; after that, stop and show me what is
still open.

## 6. Push

Gates, all required: the reviewer's last round has no blocker or major findings (or I accepted the ones that
remain), `./gradlew testDebugUnitTest koverVerify assembleDebug` passes, the working tree is clean, and every
commit follows the "Public-safe" rule in `wiki/CLAUDE.md`.

Then `git fetch origin` again. If `origin/master` moved, `git pull --rebase`, rerun the tests, and tell me. Then
`git push`. Watch the CI run for the pushed commit (`gh run watch`). If it fails, investigate and report; don't
pile on fix commits without telling me.

## 7. Update the issue

- If the work is complete, `Fixes #<n>` closes the issue on push; check it did (`gh issue view <n>`). If only a
  slice is done, the issue stays open.
- Comment on the issue (`gh issue comment <n> --body-file -`), public-safe and factual: what was done (commit
  SHAs), each acceptance criterion with the test that covers it, the wiki pages added or changed (full
  `https://github.com/shockrifle/loyscan/blob/master/wiki/...` URLs), what the review changed in one or two lines,
  and for a slice, what is still open. No design rationale beyond a link to the wiki.
- Follow-ups from the review: offer them with AskUserQuestion (multi-select, title + label each) and create only
  the ones I pick, with the right label and milestone.

## 8. Report

Finish with: the issue and what was done, the commits, the review rounds (findings fixed / rebutted and conceded /
disputed and how I decided), the CI result, the issue's state, and any follow-ups created.
