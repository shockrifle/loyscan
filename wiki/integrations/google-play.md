# Google Play (Play Console)

Where LoyScan is published. There is **no API integration**: no service account, no upload from CI. The owner
uploads each AAB by hand in Play Console ([[release]], #74). The store listing, privacy policy and data safety form are
#75.

## Contract

- **Play App Signing.** Google holds the app signing key and re-signs what it delivers to phones. We sign uploads with
  an **upload key**, which Play can reset when it leaks or is lost ([[leaked-signing-secret]]). For a new app, the key
  that signs the first uploaded AAB becomes the upload key (Play App Signing is the default for new apps; Google's
  help: https://support.google.com/googleplay/android-developer/answer/9842756). Why: [[2026-11-17-release-signing-on-ci]].
- **What we upload**: the signed AAB from the draft GitHub release of the tag. Play needs an AAB, not an APK, for a
  new app.
- **`versionCode` must grow** with every upload, on every track; `X*10000 + Y*100 + Z` does that as long as the version
  grows ([[2026-11-17-release-versioning]]). An AAB with a code Play has already seen is refused.

## Store listing assets

What Play shows on the listing but the app doesn't ship lives in `play/` at the repo root, outside every Gradle source
set. For now that is only the store icon; where the listing texts, screenshots and feature graphic go is decided with
them (#75).

- **Store icon** `play/store-icon.png`: 512×512, 32-bit PNG (RGBA), at most 1024 KB, a full opaque square. Play draws
  the rounded corners and the shadow itself, so none are drawn in (Play Console help, "Graphic assets"). It is the
  launcher icon's background and foreground layers, the visible 72dp centre of the 108dp layer scaled to 512px. That
  crop is our choice (not checked against Android Studio's Image Asset export): it is the most a launcher shows, so the
  icon looks the same on the listing as on a phone. `StoreIconTest` renders the layers named in `mipmap-anydpi/ic_launcher.xml`
  and fails when the PNG differs from them, or when it misses Play's spec or hand-worked pixels (the crop and scale).
  **After a launcher icon change**: run `StoreIconTest`, then copy `app/build/store-icon/store-icon.png` (written on
  every run) over `play/store-icon.png`.

## Quirks

- **R8 mapping.** AGP puts the R8 mapping inside the AAB (`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`),
  so Play deobfuscates crash reports without a separate upload (seen in our AAB, 2026-11-17). It also means the AAB is as revealing as `mapping.txt`: keep both private. The `mapping.txt` on the draft release is for
  retracing by hand ([[release]] → Where release files are kept).
- **Self-signed certificates.** Every upload key is self-signed, so `jarsigner -verify -strict` fails on any of our
  AABs; Play doesn't care. Our checks use `jarsigner -verify` and compare the signer's SHA-256
  (`check-release-build.sh`, [[ci]]).
