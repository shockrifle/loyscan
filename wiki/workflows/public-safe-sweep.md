# Public-safe sweep

How to check that nothing public breaks the public-safe rule ([[CLAUDE]], "Public-safe"): the history, the issues and
PRs, and the tracked files. First run before the repo went public ([[2026-11-23-public-repository]], #200). Run it again
after something slipped in (a pasted log, a screenshot), and always before a history rewrite is pushed.

**The tracked files** are checked on every gate run by `PublicSafeRepoTest` ([[tdd]]): local paths naming a user,
email addresses, licences of adapted files, the `LICENSE` text. The rest needs the steps below. Keep the dumps outside
the repo, and quote only aggregate results in issues and PRs.

## 1. Secrets in history

```sh
git fetch origin '+refs/heads/*:refs/remotes/origin/*'
gitleaks git --log-opts="--all" --redact .
```

GitHub also serves commits no branch reaches any more (a force-pushed `master`, a deleted branch). List the
force-pushes (`gh api "repos/shockrifle/loyscan/activity?activity_type=force_push"`), `git fetch origin <before sha>`,
and scan with `--log-opts="<before sha>"`. A real finding: [[leaked-signing-secret]] for signing material; anything
else is rotated first, then removed.

## 2. Personal details in history

`git log --all -p --format='commit %H%n%B' -a` into a file, then grep it for `Users[\\/]<name>`, `/home/<name>`,
email addresses and runs of 10+ digits (a real card number). `git log --all --diff-filter=D --name-only` lists deleted
files: look at removed images and data files.

## 3. Issues, PRs and comments

```sh
R=repos/shockrifle/loyscan
gh api --paginate "$R/issues?state=all&per_page=100"  > issues.json        # PRs too
gh api --paginate "$R/issues/comments?per_page=100"   > issue-comments.json
gh api --paginate "$R/pulls/comments?per_page=100"    > review-comments.json
gh api --paginate "$R/comments?per_page=100"          > commit-comments.json
```

plus each PR's reviews (`$R/pulls/<n>/reviews`). Grep titles and bodies as in step 2. **Editing doesn't remove
text:** GitHub keeps an edited body's earlier revisions, visible to anyone. Delete the comment, or delete the
revision from its edit history, instead.

## 4. Actions logs

Logs of runs are public with the repo. Scan a suspicious run's log (`gh run view <id> --log`), or delete the run
(`gh api -X DELETE repos/shockrifle/loyscan/actions/runs/<id>`).
