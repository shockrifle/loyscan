# Cutting a release

A release is a version bump merged to `master`, then a `vX.Y.Z` tag on it. The tag runs the **Release** workflow
(`.github/workflows/release.yml`), which builds the AAB signed with the upload key and attaches it, with R8's
`mapping.txt`, to a **draft** GitHub release. The owner uploads the AAB to Play by hand. Why it works this way:
[[2026-11-17-release-signing-on-ci]], [[2026-11-17-release-versioning]]; Play's side: [[google-play]] (#74).

## Once: the upload key and the secrets

The upload key is a PKCS12 keystore kept outside the repo, with one password for the store and the key. CI needs
three Actions secrets:

| Secret | Holds |
|---|---|
| `LOYSCAN_UPLOAD_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `LOYSCAN_UPLOAD_STORE_PASSWORD` | its password (also the key's) |
| `LOYSCAN_UPLOAD_KEY_ALIAS` | the key's alias |

**Run the wizard**: `scripts/release-signing-setup.sh` from Git Bash (`gh` logged in). It makes the key, asks for
the password blind, offers to write the local Gradle properties (below), sets the three secrets and opens Play Console.
Safe to re-run: saved values come back as defaults. Written with the `wizard` skill ([[agent-skills]]).

Or by hand, from Git Bash, with the keystore in a folder outside the repo:

```sh
read -rs LOYSCAN_UPLOAD_STORE_PASSWORD && export LOYSCAN_UPLOAD_STORE_PASSWORD   # typed blind
keytool -genkeypair -keystore ~/keys/loyscan-upload.jks -storetype PKCS12 -alias upload -keyalg RSA -keysize 4096 \
  -validity 10000 -dname "CN=<name you choose>" -storepass:env LOYSCAN_UPLOAD_STORE_PASSWORD
base64 -w0 ~/keys/loyscan-upload.jks | gh secret set LOYSCAN_UPLOAD_KEYSTORE_BASE64
printf '%s' "$LOYSCAN_UPLOAD_STORE_PASSWORD" | gh secret set LOYSCAN_UPLOAD_STORE_PASSWORD
gh secret set LOYSCAN_UPLOAD_KEY_ALIAS --body upload
```

To sign on your own machine too, put `loyscan.upload.storeFile`, `loyscan.upload.storePassword` and
`loyscan.upload.keyAlias` in `~/.gradle/gradle.properties` (a `C:/…` path with forward slashes on Windows),
[[build-and-run]] → Release signing. Back up the keystore and its password together (a password manager): a lost upload
key can be reset in Play Console, but the app can't be updated until it is.

## Each release

1. **Smoke-test the minified build** on a phone: `./gradlew connectedSmokeAndroidTest` ([[build-and-run]] → Tests).
2. **Bump the version** in a PR: `loyscan.versionName=X.Y.Z` in `gradle.properties`, the only place it is set. Merge it
   when CI is green ([[code-review]]).
3. **Tag the merged commit** on `master` and push the tag:
   ```sh
   git switch master && git pull --ff-only
   git tag -a vX.Y.Z -m "LoyScan X.Y.Z"
   git push origin vX.Y.Z
   ```
   The workflow stops if the tagged commit isn't on `master`, or if the tag isn't the version Gradle resolves
   (`release-bundle.sh`: *"the build's version is …, not …"*). A wrong tag: delete it (`git push origin :vX.Y.Z`,
   `git tag -d vX.Y.Z`), fix, and tag again.
4. **Watch it**: `gh run list --workflow release.yml --limit 3`, then `gh run watch <id>`.
5. **Download the AAB** from the draft release (its page on GitHub, or `gh release download vX.Y.Z --dir <a folder
   outside the repo>`).
6. **Upload to Play** ([[google-play]]): a new release on the internal testing track first, then promote it.
7. **Leave the GitHub release a draft.** Drafts are seen only by people with write access; published, the AAB and
   `mapping.txt` would be public with the repo.

Without CI (the workflow is down): with the upload key in `~/.gradle/gradle.properties`, run
`.github/scripts/release-bundle.sh X.Y.Z` from the tagged commit; the AAB is
`app/build/outputs/bundle/release/app-release.aab` and the mapping `app/build/outputs/mapping/release/mapping.txt`.
Attach both to a draft release yourself (`gh release create vX.Y.Z --draft --verify-tag <files>`).

## Where release files are kept

- **The AAB and `mapping.txt`**: on the draft GitHub release of their tag, and the AAB in Play Console. Never in the
  repo: `.gitignore` covers `*.aab`, `*.apk` and `mapping.txt` (`GitIgnoreTest`). Not as workflow artifacts either,
  which expire.
- **`mapping.txt`** turns an obfuscated stack trace from that version back into names: keep it as long as the version
  is installed anywhere.
- **The upload keystore and its password**: outside the repo on the owner's machine, in a backup, and as the three
  Actions secrets. A secret that ends up in a commit, an issue or a log: [[leaked-signing-secret]].

## Who can reach the secrets

The three secrets are repository secrets, so any workflow run with access to secrets can read them: today that means
anyone with write access, who could also push a tag on a branch whose `release.yml` skips the "on `master`" check.
That check guards against mistakes (a tag on the wrong commit), not against a collaborator. Pull requests from forks
get no secrets. Limiting an Actions environment to `v*` tags would not close this alone, since a collaborator can tag
any commit; an environment with required reviewers, or rules on who may create `v*` tags, would. None is set up. (Until
2026-11-23 the repo was private and environments needed a paid plan; public repos have them.)

## Checks

- `ReleaseWorkflowTest` (JVM, in the gate) reads `release.yml` and `release-bundle.sh`: secrets reach a step only as
  an `env:` entry, nothing echoes, traces (`set -x`) or runs Gradle with `--info`/`--debug`, the trigger is a `v*` tag,
  the tag must be on `master`, the release is created as a draft.
- `check-release-build.sh` (CI, [[ci]]) runs `release-bundle.sh` with a throwaway key on every PR: the signed AAB,
  the version check and the logs. The workflow itself first runs for real on the first tag.
