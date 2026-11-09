---
name: grilling
description: Grill the user relentlessly about a plan, decision, feature idea or issue scope until it is settled. Use when the user wants to stress-test their thinking, shape an issue's acceptance criteria, or uses any 'grill' trigger phrase.
---

Interview the user relentlessly until you reach a shared understanding. Map this as a **design tree**: every
decision branches into the decisions that hang off it.

Before the first round, read what already settles part of the tree, so you don't ask it again: the issue
(`gh issue view <n> --comments`) if there is one, the wiki pages for the area (from `wiki/index.md`), and
`wiki/decisions/_index.md`. A recorded decision is a settled branch unless the user wants to reopen it; when a
question would contradict one, name the decision page in the question.

Work the tree in **rounds**. The **frontier** is every decision whose prerequisites are already settled: the
questions you can ask _now_ without guessing at answers you haven't heard yet. Ask the whole frontier in one
round: number each question and give your recommended answer. Then wait for the user's answers before the next
round.

Format a round like so:

```
❓ **Q1** - **<question title>**: <question body, might be multiple paragraphs, including multiple choices>

➡️ <your recommended answer>

---

❓ **Q2** - **<question title>**: <question body, might be multiple paragraphs, including multiple choices>

➡️ <your recommended answer>
```

Word each question so "yes" accepts your recommended answer.

Each round the user answers reshapes the tree: settled decisions push the frontier outward and unblock questions
that depended on them. Recompute the frontier and ask the next round. A question whose answer depends on another
question still open in this round belongs to a _later_ round, not this one.

Finding _facts_ is your job, never the user's. When a frontier question needs a fact from the code, the git
history, an issue or the wiki, dispatch an `Explore` subagent with a fresh context window to find it; don't ask
the user for anything you could look up yourself. Don't block on it: a running exploration is an unsettled
prerequisite, so only the questions downstream of it wait for the subagent to report; ask the rest of the
frontier now. The _decisions_ are the user's: put each to them and wait.

The session is done when the frontier is empty: every branch of the design tree visited, nothing left silently
assumed. Do not act on it until the user confirms you have reached a shared understanding.

## Writing it down

Then offer to record the outcome where `wiki/CLAUDE.md` ("GitHub Issues vs this wiki") says each part belongs,
and write only what the user accepts:

- **What and whether** (scope, acceptance criteria as testable statements) → the GitHub issue. Creating or editing
  an issue is outward-facing: show the text first.
- **Why** (a decision with real alternatives that a future reader would wonder about) →
  `wiki/decisions/YYYY-MM-DD-<slug>.md` per the schema, plus its `_index.md` entry and a `wiki/_log.md` line,
  committed with the code it shapes or on its own if no code follows yet.
- **A term the user sharpened** → `wiki/glossary.md`.

---

Adapted from Matt Pocock's `grilling` skill (https://github.com/mattpocock/skills, MIT licence; see
`.claude/skills/LICENSE-mattpocock-skills`).
