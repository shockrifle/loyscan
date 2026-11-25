# Release signing: Play App Signing, an upload key outside the repo

**Status:** Active · **Date:** 2026-11-17 · **Commits:** see #74

## Context

Play needs a signed AAB. The repo will go public, so the key and its passwords can never be in it, and a leaked one
must be replaceable. The version scheme, the other half of #74, is [[2026-11-17-release-versioning]].

## Options considered

- **Who holds the app's signing key.** Play App Signing (Google keeps the app signing key; uploads are signed with an
  upload key that Play can reset) or our own app signing key (lost or leaked, the app can never be updated).
- **Who builds the signed AAB.** Only the owner's machine; CI on a version tag, from Actions secrets; CI building
  and uploading to Play through a service account (another credential and the Play API to set up).
- **Where the build reads the key from.** A `keystore.properties` file in the project (common, but one `git add -f`
  from being committed), or Gradle properties in the user's home and environment variables.

## Decision

Chosen by the owner when #74 was planned:
- **Play App Signing with an upload key.** The upload key is a PKCS12 keystore outside the repo.
- **CI signs on a version tag**, with the keystore and its password from Actions secrets; the owner uploads the AAB to
  Play by hand. No service account.
- **The build reads three values**, each from a Gradle property or an environment variable (`app/build.gradle.kts`,
  `uploadSigningNames`): `loyscan.upload.storeFile` / `LOYSCAN_UPLOAD_STORE_FILE`, `loyscan.upload.storePassword` /
  `LOYSCAN_UPLOAD_STORE_PASSWORD`, `loyscan.upload.keyAlias` / `LOYSCAN_UPLOAD_KEY_ALIAS`. A non-empty Gradle property
  wins. All three set: `release` is signed. None: `release` stays unsigned, as on CI's PR builds. Some: the build
  stops and names the missing ones, never a value.

## Why

- **Play App Signing** makes a leaked or lost upload key recoverable: Play Console resets it. A leaked app signing key
  could not be fixed.
- **One password.** With PKCS12, keytool's default since JDK 9, a key password different from the store password is
  ignored, so a separate key password would be a value that unlocks nothing (checked 2026-11-17 with JDK 18's
  keytool: jarsigner then fails with "key associated with … not a private key"). The build uses the store password
  for the key.
- **Gradle properties in `~/.gradle/gradle.properties`, not a project file**: nothing to commit by accident.
  `.gitignore` still covers `keystore.properties`, `*.jks` and `*.keystore` (`GitIgnoreTest`).
- **Half set up stops the build**: an unsigned AAB from a mistyped property would only fail at upload.
- **Names, never values, in errors**: Gradle's output ends up in CI logs.

## Consequences

- `.github/scripts/check-release-build.sh` checks it on every CI run that runs the gate
  ([[2026-11-23-ci-run-trims]]) with a throwaway key made the same way
  ([[ci]]): the AAB verifies with `jarsigner` and its signer's SHA-256 is the throwaway key's, a half set-up build
  fails naming only the missing variable, and neither log contains the password. `jarsigner -verify -strict` isn't
  used: it fails on any self-signed certificate, which every upload key is.
- A developer whose `~/.gradle/gradle.properties` holds the upload key signs every local release build with it.
