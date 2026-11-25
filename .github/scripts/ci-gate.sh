#!/usr/bin/env bash
# Decides whether this Tests run runs the gate (#199): wiki/workflows/ci.md -> What runs. The run's first step after
# checkout; later steps run on run=true (the gate), setup=true (JDK, Gradle) or wiki=true. Checked by check-ci-gate.sh.
# Env: EVENT (github.event_name), REPO (owner/name), GITHUB_OUTPUT, and GH_TOKEN for the API.
# Writes to $GITHUB_OUTPUT:
#   run=true|false   whether the gate runs
#   wiki=true|false  a wiki-only PR: run only the tests that read every wiki page (PublicSafeRepoTest, WikiReferencesTest)
#   setup=true|false whether the JDK and Gradle are set up (for the gate, or for that one test)
#   marker=true|false  pull_request only: whether a green run may record its tree as tested (see the workflow)
#   tree=<sha>       HEAD's tree, which names that record
# Anything unexpected (no base commit, an API error) gives run=true: a full run costs minutes, a wrong skip a check.
set -euo pipefail

# Wiki pages a test besides PublicSafeRepoTest and WikiReferencesTest reads (StoreListingTest, PrivacyPolicyTest): a
# change to one runs the gate like code does. Those two read every wiki page, so a wiki-only PR still runs them.
tested_wiki='^wiki/integrations/google-play\.md$'
# What the cache keys in tests.yml hash or are made by: setup-gradle's Gradle files, the wrapper, the version catalog,
# and the workflow and scripts that form the keys. A PR changing one leaves master's entries stale unless master runs.
cache_inputs='(^|/)[^/]*\.gradle[^/]*$|(^|/)gradle\.properties$|^gradle/|^buildSrc/|(^|/)versions\.properties$|^\.github/workflows/tests\.yml$|^\.github/scripts/(ci-gate|robolectric-jars)\.sh$'

run=true
marker=false
wiki=false
tree=$(git rev-parse 'HEAD^{tree}')

# pull_request: the checkout is GitHub's merge commit; its first parent is the base, so HEAD^1..HEAD is the PR's change.
decide_pull_request() {
  local list files=() f wiki_only=true touches_cache=false
  list=$(mktemp)
  # --no-renames: a file moved into wiki/ must count by its old path too.
  if ! git diff --no-renames --name-only -z 'HEAD^1' HEAD >"$list" 2>/dev/null; then
    echo "Can't diff against the base commit (HEAD^1): running the gate."
    rm -f "$list"
    return
  fi
  mapfile -d '' -t files <"$list"
  rm -f "$list"
  if [ "${#files[@]}" -eq 0 ]; then
    echo "The PR changes no files: running the gate."
    return
  fi
  for f in "${files[@]}"; do
    if [[ $f != wiki/* || $f =~ $tested_wiki ]]; then wiki_only=false; fi
    if [[ $f =~ $cache_inputs ]]; then touches_cache=true; fi
  done
  if [ "$wiki_only" = true ]; then
    echo "The PR changes only wiki pages: skipping the gate, running only the wiki checks."
    run=false
    wiki=true
  elif [ "$touches_cache" = true ]; then
    echo "The PR changes what a cache key is made of: running the gate; master runs it again to save fresh caches."
  else
    echo "Running the gate; a green run records its tree, so master can skip it after the merge."
    marker=true
  fi
}

# push (to master, the only branch the workflow runs on for a push): skip when a green pull_request run of this
# workflow, on a branch of this repo, recorded this very tree (the "Record the tested tree" step). A rebase-merge of an
# up-to-date PR lands exactly the tree its PR run tested, and master requires PRs to be up to date.
decide_push() {
  local answer ids id facts expected
  expected="pull_request completed success .github/workflows/tests.yml $REPO"
  answer=$(mktemp)
  if ! gh api "repos/$REPO/actions/artifacts?name=tested-tree-$tree&per_page=100" >"$answer" ||
    ! ids=$(jq -r '.artifacts[]
        | select(.expired == false and .workflow_run.head_repository_id == .workflow_run.repository_id)
        | .workflow_run.id' "$answer"); then
    echo "Couldn't look up whether a PR run tested this tree: running the gate."
    rm -f "$answer"
    return
  fi
  for id in $ids; do
    if gh api "repos/$REPO/actions/runs/$id" >"$answer" &&
      facts=$(jq -r '[.event, .status, .conclusion, .path, .head_repository.full_name] | map(tostring) | join(" ")' "$answer") &&
      [ "$facts" = "$expected" ]; then
      echo "Run $id, a green pull_request run of this workflow, tested this tree: skipping the gate."
      run=false
      rm -f "$answer"
      return
    fi
  done
  rm -f "$answer"
  echo "No green PR run recorded this tree: running the gate."
}

case "${EVENT:-}" in
  pull_request) decide_pull_request ;;
  push) decide_push ;;
  *) echo "A ${EVENT:-<unknown>} run: running the gate." ;;
esac

setup=false
if [ "$run" = true ] || [ "$wiki" = true ]; then setup=true; fi

outputs=$(printf 'run=%s\nmarker=%s\nwiki=%s\nsetup=%s\ntree=%s\n' "$run" "$marker" "$wiki" "$setup" "$tree")
printf '%s\n' "$outputs"
# The file the "Record the tested tree" step uploads; its name, not its content, is what a push looks up.
if [ "$marker" = true ] && [ -n "${RUNNER_TEMP:-}" ]; then
  printf '%s\n' "$tree" >"$RUNNER_TEMP/tested-tree"
fi
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  printf '%s\n' "$outputs" >>"$GITHUB_OUTPUT"
fi
