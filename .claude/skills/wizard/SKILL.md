---
name: wizard
description: "Write an interactive bash wizard that walks the user through steps only they can take: keys and passwords, Actions secrets, a third-party console (Play Console), a one-off cutover."
disable-model-invocation: true
argument-hint: "[the procedure, e.g. 'rotate the upload key']"
---

# Wizard

A **wizard** is a bash script that walks the user, stage by stage, through a manual procedure that's tedious to do by
hand and tedious to re-explain every time. It opens each URL, says exactly what to click and copy, captures the values,
writes them where they belong (a Gradle properties file outside the repo, GitHub Actions secrets), confirms at every
stage, and shows how many stages are left.

The UX is solved by [template.sh](template.sh): progress, confirmation gates, opening URLs (Git Bash on Windows, WSL,
Linux, macOS), hidden secret entry, idempotent property upserts, `gh secret` / `gh variable` writes, a closing summary.
**Your job is only to scope the procedure and author its stages.** The library above the `STAGES` marker is the same in
every wizard: never hand-edit it in a wizard; change it here, in the template, and copy it to the committed wizards.

You write the script; you never run it. The user runs it in their own terminal, so no key or password passes through
this conversation. If the user pastes a secret into the chat anyway, tell them to treat it as leaked
(`wiki/runbooks/leaked-signing-secret.md` for the signing key).

## In LoyScan

- **No secret ever lands in the repo.** There is no `.env`. A value local builds need goes to a Gradle property in
  `~/.gradle/gradle.properties` (`write_property`; it refuses a file inside the repo, as `inside_repo` refuses a path).
  A value CI needs goes to an Actions secret (`set_secret`). Files like keystores go to a folder outside the repo the
  user picks. `.gitignore` covers the usual names as a backstop (`GitIgnoreTest`).
- **Windows.** The owner runs Git Bash. Pass paths to Java as `java_path` gives them (`C:/…`, forward slashes: a
  backslash is an escape in a properties file). Gradle reads properties as ISO-8859-1, so a password must be ASCII.
- **Public-safe.** The script is committed when it is a repeatable path, so it holds no personal data and no real
  card data: names, emails and values come from the user at run time.
- **Committed wizards** live in `scripts/` and are listed in `wiki/scripts/_index.md`; one-off ones go to the scratchpad
  and are deleted after the run. Existing: `scripts/release-signing-setup.sh` (#74).

## Process

### 1. Scope the procedure

Work out every manual step the user must take and every value captured along the way. Read the repo first, don't ask
cold: `.github/workflows/*.yml` (every `secrets.*` / `vars.*` is a value the wizard must produce), `app/build.gradle.kts`
and `gradle.properties` (the Gradle properties and environment variables the build reads), and the wiki pages for the
area (`wiki/workflows/release.md` for signing).

Then show the user the ordered list of stages and the values each produces, and confirm: they may add, drop, or reorder.

**Done when:** every stage is named in order, and for each captured value you know (a) where the user gets it, (b)
where it's written (a Gradle property, an Actions secret, both, or nowhere), and (c) whether it's secret (hidden entry).

### 2. Map each stage's journey

For each stage, write the path the user follows: which URL to open, what to do there, which value it fills. Where you
don't know the current UI (Play Console's pages change), say so in the stage text and link the vendor's help page;
never invent clicks.

**Done when:** every stage traces to instructions a stranger could follow.

### 3. Author the wizard

Copy `template.sh` to the target path. Replace the example stage with one `stage` per step, in dependency order. Use
the helpers: `stage`, `say` / `step` / `note` / `warn`, `open_url`, `ask` / `ask_secret` (a third argument names the
property whose saved value is offered back), `java_path`, `inside_repo`, `write_property`, `set_secret` / `set_var`,
`pause` / `confirm`. Set `TOTAL_STAGES`.

Hold the template's bar: open the URL before asking for its value, `ask_secret` for anything secret, `confirm` before
anything irreversible, one focused task per stage (each stage clears the screen). Pass a secret to a tool through an
environment variable or stdin (`keytool -storepass:env`, `gh secret set` reads stdin), never as an argument, and never
print it. Don't touch the library above the marker.

### 4. Verify and hand off

- `bash -n <script>`. CI runs `shellcheck` on `scripts/*.sh` and this template.
- `chmod +x` it, and `git add --chmod=+x` when it is committed (`.gitattributes` keeps `*.sh` LF).
- Don't run it end to end yourself: it opens a browser and waits for the user. Trace it statically, and run it with
  answers piped in, a sandbox `HOME` and no `gh` on the `PATH`, to see each stage and file write. Every `set_secret`
  name must match a `secrets.*` the workflows read: `ReleaseWorkflowTest` checks the release wizard against
  `release.yml`; add the same check for a new committed wizard.
- Tell the user how to run it, and that it is safe to re-run: saved values come back as defaults.

Adapted from Matt Pocock's `wizard` skill (https://github.com/mattpocock/skills, MIT licence; see
`.claude/skills/LICENSE-mattpocock-skills`).
