#!/usr/bin/env bash
# Checks .github/scripts/ci-gate.sh, which decides whether a Tests run runs the gate (#199). Each case is a throwaway
# git repo whose HEAD^1 is the base (a PR run's checkout is GitHub's merge commit, whose first parent is the base) and
# whose HEAD makes the case's changes; the script's $GITHUB_OUTPUT file is what the workflow reads, so that is what is
# checked. Run from the repo root: on CI ("CI gate checks") and in Git Bash. wiki/workflows/ci.md -> What runs
set -euo pipefail

fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
pass() { printf 'ok: %s\n' "$1"; }

gate="$PWD/.github/scripts/ci-gate.sh"
sandbox=$(mktemp -d)
trap 'rm -rf "$sandbox"' EXIT
export GIT_AUTHOR_NAME=check GIT_AUTHOR_EMAIL=check@example.com GIT_COMMITTER_NAME=check GIT_COMMITTER_EMAIL=check@example.com

base_files=(app/src/main/A.kt README.md wiki/index.md wiki/integrations/google-play.md gradle.properties
  gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties app/build.gradle.kts settings.gradle.kts
  .github/workflows/tests.yml .github/workflows/release.yml .github/scripts/ci-gate.sh .github/scripts/robolectric-jars.sh)
repo_count=0

# repo CHANGE...: a new repo with the base files in HEAD^1 and the changes in HEAD; cds into it. A change is a path to
# edit or add, "rm:PATH" to delete one, or "mv:FROM:TO" to rename one. No change makes an empty commit.
repo() {
  repo_count=$((repo_count + 1))
  local dir="$sandbox/repo$repo_count" change from to
  mkdir -p "$dir" && cd "$dir"
  git init -q && git config core.autocrlf false
  for f in "${base_files[@]}"; do mkdir -p "$(dirname "$f")"; printf 'base %s\n' "$f" >"$f"; done
  git add -A && git commit -qm base
  for change in "$@"; do
    case "$change" in
      rm:*) git rm -q "${change#rm:}" ;;
      mv:*) from=${change#mv:}; from=${from%%:*}; to=${change##*:}; mkdir -p "$(dirname "$to")"; git mv "$from" "$to" ;;
      *) mkdir -p "$(dirname "$change")"; printf 'changed\n' >>"$change"; git add "$change" ;;
    esac
  done
  git commit -q --allow-empty -m change
}

# decide EVENT: runs the script in the current repo; its outputs land in $sandbox/out.
decide() {
  : >"$sandbox/out"
  EVENT="$1" REPO=owner/loyscan GITHUB_OUTPUT="$sandbox/out" "$gate" >"$sandbox/log" 2>&1 ||
    { cat "$sandbox/log"; fail "ci-gate.sh exited with an error on $1"; }
}

# expect CASE KEY=VALUE...: each output has exactly that value, written once.
expect() {
  local case=$1 pair
  shift
  for pair in "$@"; do
    [[ $(grep -c "^${pair%%=*}=" "$sandbox/out") == 1 ]] || { cat "$sandbox/out"; fail "$case: ${pair%%=*} isn't written exactly once"; }
    grep -qx "$pair" "$sandbox/out" || { cat "$sandbox/log" "$sandbox/out"; fail "$case: expected $pair"; }
  done
  pass "$case"
}

# ── Pull requests: which run the gate ───────────────────────────────────────────────────────────────────────────────
repo wiki/index.md wiki/new-page.md; decide pull_request
expect "a PR changing only wiki pages skips the gate" run=false marker=false
repo rm:wiki/index.md; decide pull_request
expect "a PR deleting a wiki page skips the gate" run=false marker=false
repo wiki/index.md README.md; decide pull_request
expect "a PR changing a wiki page and another file runs the gate" run=true
repo wiki/integrations/google-play.md; decide pull_request
expect "a PR changing a wiki page the tests read runs the gate" run=true
repo mv:app/src/main/A.kt:wiki/A.kt; decide pull_request
expect "a PR moving a file into wiki/ runs the gate (the old path counts too)" run=true
repo; decide pull_request
expect "a PR with no changes runs the gate" run=true marker=false
repo app/src/main/A.kt; git checkout -q --orphan lone && git commit -qm "a commit with no parent"; decide pull_request
expect "a PR run that can't see its base runs the gate" run=true marker=false

# ── Pull requests: which may record their tree as tested (marker) ───────────────────────────────────────────────────
repo app/src/main/A.kt README.md .github/workflows/release.yml; decide pull_request
expect "a PR leaving the cache keys' inputs alone may record its tree" run=true marker=true
for input in gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties app/build.gradle.kts settings.gradle.kts \
  gradle.properties buildSrc/build.gradle.kts versions.properties .github/workflows/tests.yml .github/scripts/ci-gate.sh \
  .github/scripts/robolectric-jars.sh; do
  repo app/src/main/A.kt "$input"; decide pull_request
  expect "a PR changing $input doesn't record its tree" run=true marker=false
done
repo mv:gradle/libs.versions.toml:wiki/libs.versions.toml; decide pull_request
expect "a PR moving a cache input away doesn't record its tree" run=true marker=false

# ── A manual run ────────────────────────────────────────────────────────────────────────────────────────────────────
repo wiki/index.md; decide workflow_dispatch
expect "a manual run runs the gate, even on a wiki-only change" run=true marker=false

# ── The tree is written for the marker's name ───────────────────────────────────────────────────────────────────────
repo app/src/main/A.kt; decide pull_request
expect "the output names HEAD's tree" "tree=$(git rev-parse 'HEAD^{tree}')"
