# Integrations

External systems and third-party runtime libraries whose **contract and quirks** matter: what we send and
expect, configuration, version-specific gotchas. Not vendor docs — link those instead.

- [[coil-image-loading]] — Coil 2: card photos and the user's store logos, all private files (#151)
- [[zxing-core]] — ZXing core writers draw the detail screen's barcode; format → Code 128 → QR fallback (GS1 data: GS1-128 → GS1 QR)
- [[google-play]] — Play Console: Play App Signing with our upload key, AABs uploaded by hand, versionCode must grow, mapping inside the AAB (#74)
- [[zxing-cpp]] — zxing-cpp reads the scanner's camera frames; plain text mode, formats mapped one to one

LoyScan has no backend or network API of its own (as of 2026-10-16). Add a page here when one appears. Play is a
service we publish through by hand, not one the app talks to.
