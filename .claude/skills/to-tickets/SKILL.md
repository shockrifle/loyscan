---
name: to-tickets
description: Break a large issue, plan or the current conversation into ordered, vertical-slice GitHub issues, each naming the issues that block it, as sub-issues of the parent.
disable-model-invocation: true
argument-hint: "[issue #n | spec path] [notes]"
---

# To Tickets

Break a plan, spec or conversation into **tickets**: GitHub issues on `shockrifle/loyscan`, each a **tracer-bullet**
vertical slice, each naming the tickets that **block** it. Source / notes: `$ARGUMENTS`

Creating issues changes the backlog: nothing is published until the user approves the breakdown (step 4).

## Process

### 1. Gather context

Work from what is already in the conversation. If the argument is an issue, read it whole:
`gh issue view <n> --comments`. Note its labels and milestone; the tickets inherit them unless the user says
otherwise.

### 2. Explore

Read the wiki pages for the area (from `wiki/index.md`), `wiki/architecture.md` (its worked example is the layer
template: Room entity/DAO → mapper → repository → ViewModel → Compose screen + route/Directions, each layer with
its test), `wiki/decisions/_index.md`, and the code the work touches. Use `wiki/glossary.md` vocabulary in titles
and bodies. Check the open issues (`gh issue list --state open --limit 100`) so a ticket doesn't duplicate one, and
so an existing issue can be a blocker.

Look for prefactoring that makes the rest easier: "make the change easy, then make the easy change". Substantial
prefactoring gets its own first ticket.

### 3. Draft vertical slices

<vertical-slice-rules>

- Each slice cuts a narrow but COMPLETE path through every layer it needs (schema and migration, DAO, mapper,
  repository, ViewModel, screen, tests): vertical, NOT a horizontal slice of one layer.
- A completed slice is verifiable on its own: its acceptance criteria are testable statements
  (`wiki/CLAUDE.md`, "GitHub Issues vs this wiki"), each provable by a test at one of the layers in
  `wiki/workflows/tdd.md`.
- Each slice fits one `/next-task` run: one fresh session, test-first, reviewed, pushed.
- Prefactoring comes first.
- A slice that changes the Room schema carries its version bump and migration (`wiki/workflows/database-schema-changes.md`);
  the schema is frozen from v2, so there is no "migration later" ticket.

</vertical-slice-rules>

Give each ticket its **blocking edges**: the tickets (new or existing) that must be done before it can start. A
ticket with no blockers can start immediately.

**Wide refactors are the exception to vertical slicing.** A wide refactor is one mechanical change (a rename, a
retyped shared model) whose blast radius breaks many call sites at once, so no vertical slice can land green.
Sequence it as **expand–contract**: expand (add the new form beside the old, nothing breaks), migrate call sites in
batches (per package or screen), each batch a ticket blocked by the expand and green on its own, then contract
(delete the old form) in a ticket blocked by every batch.

Some work can't be finished in code: a check with a real card or at a till. That is its own ticket, labelled
`needs-field-check`, blocked by the code ticket it checks.

### 4. Quiz the user

Present the breakdown as a numbered list, blockers first. For each ticket show:

- **Title**: in the style of the existing issues (a plain statement of the behaviour or the change, no prefix)
- **Blocked by**: the tickets (by number in this list, or existing `#N`) that must be done first
- **What it delivers**: the end-to-end behaviour this ticket makes work
- **Acceptance criteria**: the testable statements
- **Label and milestone**

Ask the user:

- Does the granularity feel right (too coarse, too fine)?
- Are the blocking edges right: does each ticket depend only on tickets that genuinely gate it?
- Should any tickets be merged or split?

Iterate until the user approves the breakdown.

### 5. Publish

Create the approved tickets in dependency order (blockers first), so each one can name real issue numbers. Use
the template below with `gh issue create --title … --label … --milestone … --body-file -`.

Then link them with GitHub's native relationships. These endpoints take the issue's **id**
(`gh api repos/{owner}/{repo}/issues/<n> --jq .id`), not its number:

- **Sub-issue of the parent** (when the source was an issue):
  `gh api -X POST repos/{owner}/{repo}/issues/<parent>/sub_issues -F sub_issue_id=<id of the ticket>`
- **Blocked by**: `gh api -X POST repos/{owner}/{repo}/issues/<ticket>/dependencies/blocked_by -F issue_id=<id of the blocker>`

The "Blocked by" line stays in the body as well: `/next-task` reads blockers from the body text. If a native link
call fails, report it and keep going; the text line is enough for `/next-task`.

Do NOT close or edit the parent issue. Report the created issues as a list (number, title, blocked by), and the
**frontier**: the tickets whose blockers are all done, which `/next-task` can pick next.

<issue-template>

<One or two sentences: the end-to-end behaviour this ticket makes work, from the user's point of view.>

Part of #<parent> (omit when there is no parent).

**Acceptance criteria**
- [ ] <testable statement>
- [ ] <testable statement>

Blocked by #<n>, #<m> (or "Blocked by: nothing, can start now")

</issue-template>

Keep bodies public-safe (`wiki/CLAUDE.md`): no personal data, no real card numbers, no local paths. Leave out file
paths and code snippets, which go stale; link a wiki page with its full
`https://github.com/shockrifle/loyscan/blob/master/wiki/...` URL when the design rationale matters.

---

Adapted from Matt Pocock's `to-tickets` skill (https://github.com/mattpocock/skills, MIT licence; see
`.claude/skills/LICENSE-mattpocock-skills`).
