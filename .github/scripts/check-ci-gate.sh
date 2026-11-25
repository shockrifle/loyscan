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

# A fake gh, first on the PATH: `gh api PATH` answers from the files in $sandbox/api (the artifacts list, one file per
# run), shaped as GitHub's answers on 2026-11-23, and logs every call. $sandbox/api/down makes every call fail.
mkdir -p "$sandbox/bin" "$sandbox/api"
cat >"$sandbox/bin/gh" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$FAKE_GH/calls"
[ "$1" = api ] || exit 2
if [ -e "$FAKE_GH/down" ]; then echo "HTTP 502: Bad Gateway" >&2; exit 1; fi
case "$2" in
  */actions/artifacts\?*) cat "$FAKE_GH/artifacts.json" ;;
  */actions/runs/*) if [ -f "$FAKE_GH/run-${2##*/}.json" ]; then cat "$FAKE_GH/run-${2##*/}.json"; else echo "HTTP 404: Not Found" >&2; exit 1; fi ;;
  *) exit 2 ;;
esac
FAKE
chmod +x "$sandbox/bin/gh"
export FAKE_GH="$sandbox/api"

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

# decide EVENT: runs the script in the current repo; its outputs land in $sandbox/out, the API calls in $FAKE_GH/calls.
decide() {
  : >"$sandbox/out"
  : >"$FAKE_GH/calls"
  EVENT="$1" REPO=owner/loyscan GH_TOKEN=fake GITHUB_OUTPUT="$sandbox/out" PATH="$sandbox/bin:$PATH" "$gate" \
    >"$sandbox/log" 2>&1 || { cat "$sandbox/log"; fail "ci-gate.sh exited with an error on $1"; }
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

# ── The API's answers ───────────────────────────────────────────────────────────────────────────────────────────────
this_repo_id=1410216263

# artifact RUN_ID [EXPIRED] [HEAD_REPOSITORY_ID]: one entry of the artifacts list, named after HEAD's tree.
artifact() {
  printf '{"id": 14820462855, "node_id": "MDg6QXJ0aWZhY3QxNDgyMDQ2Mjg1NQ==", "name": "tested-tree-%s",
    "size_in_bytes": 41, "url": "https://api.github.com/repos/owner/loyscan/actions/artifacts/14820462855",
    "archive_download_url": "https://api.github.com/repos/owner/loyscan/actions/artifacts/14820462855/zip",
    "expired": %s, "digest": "sha256:8daaad497fe10716ea94a897894b59552c5fafeeeb5a648008c2f740ab17ae3d",
    "created_at": "2026-11-23T12:55:29Z", "updated_at": "2026-11-23T12:55:29Z", "expires_at": "2026-12-07T12:55:27Z",
    "workflow_run": {"id": %s, "repository_id": %s, "head_repository_id": %s, "head_branch": "199-ci-run-trims",
    "head_sha": "6eb994d20690f99bea8f648d9a382a36ff8e2f22"}}' \
    "$(git rev-parse 'HEAD^{tree}')" "${2:-false}" "$1" "$this_repo_id" "${3:-$this_repo_id}"
}

# artifacts ENTRY...: the list GET /repos/{repo}/actions/artifacts?name=… answers with.
artifacts() {
  local IFS=,
  printf '{"total_count": %s, "artifacts": [%s]}\n' "$#" "$*" >"$FAKE_GH/artifacts.json"
}

# run_answer ID EVENT STATUS CONCLUSION [PATH] [HEAD_REPOSITORY]: what GET /repos/{repo}/actions/runs/ID answers.
run_answer() {
  local conclusion=null
  [ "$4" = null ] || conclusion="\"$4\""
  printf '{"id": %s, "name": "Tests", "node_id": "WFR_kwLOVA4xR88AAAAKhJ6Vbg", "head_branch": "199-ci-run-trims",
    "head_sha": "6eb994d20690f99bea8f648d9a382a36ff8e2f22", "path": "%s", "run_number": 412, "event": "%s",
    "status": "%s", "conclusion": %s, "workflow_id": 197614553,
    "repository": {"id": %s, "full_name": "owner/loyscan"}, "head_repository": {"id": 1, "full_name": "%s"}}\n' \
    "$1" "${5:-.github/workflows/tests.yml}" "$2" "$3" "$conclusion" "$this_repo_id" "${6:-owner/loyscan}" \
    >"$FAKE_GH/run-$1.json"
}

reset_api() { rm -f "$FAKE_GH"/*.json "$FAKE_GH/down"; artifacts; }

no_api_calls() { [ ! -s "$FAKE_GH/calls" ] || { cat "$FAKE_GH/calls"; fail "$1: called the API"; }; }

# ── Pull requests: which run the gate ───────────────────────────────────────────────────────────────────────────────
repo wiki/index.md wiki/new-page.md; decide pull_request
expect "a PR changing only wiki pages skips the gate, but runs the wiki checks" run=false marker=false setup=true wiki=true
repo rm:wiki/index.md; decide pull_request
expect "a PR deleting a wiki page skips the gate, but runs the wiki checks" run=false marker=false setup=true wiki=true
repo wiki/index.md README.md; decide pull_request
expect "a PR changing a wiki page and another file runs the gate" run=true setup=true wiki=false
repo wiki/integrations/google-play.md; decide pull_request
expect "a PR changing a wiki page the tests read runs the gate" run=true setup=true wiki=false
repo mv:app/src/main/A.kt:wiki/A.kt; decide pull_request
expect "a PR moving a file into wiki/ runs the gate (the old path counts too)" run=true setup=true wiki=false
repo; decide pull_request
expect "a PR with no changes runs the gate" run=true marker=false setup=true wiki=false
repo app/src/main/A.kt; git checkout -q --orphan lone && git commit -qm "a commit with no parent"; decide pull_request
expect "a PR run that can't see its base runs the gate" run=true marker=false setup=true wiki=false

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

no_api_calls "a pull request"

# ── A manual run ────────────────────────────────────────────────────────────────────────────────────────────────────
repo wiki/index.md; reset_api; artifacts "$(artifact 101)"; run_answer 101 pull_request completed success
decide workflow_dispatch
expect "a manual run runs the gate, even on a wiki-only change or a tested tree" run=true marker=false setup=true wiki=false
no_api_calls "a manual run"

# ── The tree is written for the marker's name ───────────────────────────────────────────────────────────────────────
repo app/src/main/A.kt; decide pull_request
expect "the output names HEAD's tree" "tree=$(git rev-parse 'HEAD^{tree}')"

# ── A push to master: skipped only when a green PR run of this workflow, in this repo, tested the same tree ─────────
# The lookup reads the API's JSON with jq (preinstalled on ubuntu-latest). Without it the cases can't run: that fails
# on CI, and is reported as skipped elsewhere.
if ! command -v jq >/dev/null 2>&1; then
  [ -z "${CI:-}" ] || fail "jq isn't installed: the push cases need it"
  printf 'skipped: the push cases (jq is not installed)
'
  exit 0
fi

repo app/src/main/A.kt; reset_api; artifacts "$(artifact 101)"; run_answer 101 pull_request completed success
decide push
expect "a push whose tree a green PR run tested skips the gate" run=false marker=false setup=false wiki=false
grep -q "^api repos/owner/loyscan/actions/artifacts?name=tested-tree-$(git rev-parse 'HEAD^{tree}')&per_page=100$"   "$FAKE_GH/calls" || { cat "$FAKE_GH/calls"; fail "the lookup didn't ask for this tree's marker"; }
pass "the lookup asks for the marker named after HEAD's tree"

reset_api; decide push
expect "a push of a tree no PR run recorded runs the gate" run=true setup=true wiki=false

reset_api; artifacts "$(artifact 101 true)"; run_answer 101 pull_request completed success; decide push
expect "a push whose marker has expired runs the gate" run=true

reset_api; artifacts "$(artifact 101 false 999)"; run_answer 101 pull_request completed success; decide push
expect "a push whose marker came from a fork's PR runs the gate" run=true

reset_api; artifacts "$(artifact 101)"; run_answer 101 pull_request completed success .github/workflows/tests.yml fork/loyscan
decide push
expect "a push whose marker's run was on another repository's head runs the gate" run=true

for outcome in failure cancelled skipped; do
  reset_api; artifacts "$(artifact 101)"; run_answer 101 pull_request completed "$outcome"; decide push
  expect "a push whose marker's run ended in $outcome runs the gate" run=true
done

reset_api; artifacts "$(artifact 101)"; run_answer 101 pull_request in_progress null; decide push
expect "a push whose marker's run hasn't finished runs the gate" run=true

reset_api; artifacts "$(artifact 101)"; run_answer 101 push completed success; decide push
expect "a push whose marker came from a push run runs the gate" run=true

reset_api; artifacts "$(artifact 101)"; run_answer 101 pull_request completed success .github/workflows/release.yml
decide push
expect "a push whose marker came from another workflow runs the gate" run=true

reset_api; artifacts "$(artifact 101)"; decide push
expect "a push whose marker's run can't be read runs the gate" run=true

reset_api; artifacts "$(artifact 101)" "$(artifact 102)"; run_answer 101 pull_request completed failure
run_answer 102 pull_request completed success; decide push
expect "a push with a failed and a green PR run on its tree skips the gate" run=false

reset_api; artifacts "$(artifact 101)"; run_answer 101 pull_request completed success; touch "$FAKE_GH/down"; decide push
expect "a push runs the gate when the API fails" run=true setup=true wiki=false

reset_api; printf '<html>rate limited</html>
' >"$FAKE_GH/artifacts.json"; decide push
expect "a push runs the gate when the API answers something that isn't JSON" run=true
