# A signing secret leaked

**Use when** the upload keystore, its password, or a `LOYSCAN_UPLOAD_*` secret's value has been committed (any branch,
even if never pushed to `master`), pasted into an issue, PR, comment or chat, or printed in a CI log. Release signing
is set up in [[release]] ([[2026-11-17-release-signing-on-ci]], #74).

**Deleting it doesn't fix it.** A commit stays in the history, in forks and clones, and in GitHub's caches; a log or
comment may already have been read. Treat the key as compromised and **rotate**: a new upload key and a new password.
The app signing key is Google's (Play App Signing), so the app itself stays safe; the upload key is what Play Console
can reset.

## Steps

1. **Stop releases.** Don't push a `v*` tag until this is done.
2. **Make a new upload key** with a new password, as in [[release]] → Once (a new keystore file, not the old one with
   a new password: the old file may be out there with its old password).
3. **Ask Play to reset the upload key.** In Play Console, the app's App signing (app integrity) page has the upload key
   reset request. It asks for the new upload certificate as a PEM file:
   ```sh
   keytool -export -rfc -keystore <new keystore> -alias upload -file upload_certificate.pem \
     -storepass:env LOYSCAN_UPLOAD_STORE_PASSWORD
   ```
   Google's help page on Play App Signing describes the reset
   (https://support.google.com/googleplay/android-developer/answer/9842756). Until Play confirms it, uploads signed
   with either key may be refused.
4. **Replace the secrets**: all three `LOYSCAN_UPLOAD_*` Actions secrets ([[release]] → Once), and
   `~/.gradle/gradle.properties` on every machine that signs.
5. **Destroy the old keystore** and its backups once Play accepts the new key.
6. **Clean up where it leaked**, as hygiene only: delete the comment or log, or rewrite the branch if it never
   reached `master`. A leak on `master` stays in its history; the rotation is what makes it harmless.
7. Record what happened in `wiki/_log.md`, and in a `bugs/` postmortem if the leak came from a tool or script.

## If the steps don't help

- **Play refuses the reset**, or the account owner can't be reached: uploads stop until it is reset. Nothing on the
  repo side can replace that step.
- **The keystore was lost, not leaked**: the same reset, without step 6.
