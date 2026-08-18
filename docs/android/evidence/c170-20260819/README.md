# C170 Play signed-release local ledger synthesis (2026-08-19)

- Date: 2026-08-19
- Branch: `android-dev`
- Commit head at run: `110ed0b7a971697d6e1e7ecf3c90bf1c6e54ae15`
- Command: `./gradlew bundleSignedRelease`
- Device/environment: local Android build only
- Notes: Temporary synthetic upload key and runtime values were supplied from `/tmp`-scoped shell variables for local verification only.

## Results

- `bundleSignedRelease` passed fully after resolving stale handoff hash mismatch.
- `Source commit`: `110ed0b7a971697d6e1e7ecf3c90bf1c6e54ae15`
- `AAB` hash (SHA-256): `df73426a66116f603918109aa6e9d8c56f7ea09077bd9be3d09da044b5fc9243`
- `Upload cert` (SHA-256): `3313f73dc347a90831e0484e844b1077b6cf526c58c932f94c7e91e31e2f6251`
- Handoff directory:
  - `android/app/build/outputs/play-release/v1.0.6-code1-110ed0b7a971`
- Files in handoff:
  - `med-manager-android-v1.0.6-code1-110ed0b7a971.aab`
  - `play-release-evidence.json`
  - `SHA256SUMS`
- `verifyPreparedPlayReleaseHandoff` passed:
  - `VERSION_NAME=1.0.6 VERSION_CODE=1 COMMIT=110ed0b7a971697d6e1e7ecf3c90bf1c6e54ae15`
  - `AAB_SHA256=df73426a66116f603918109aa6e9d8c56f7ea09077bd9be3d09da044b5fc9243`
  - `FILES=3`

## Evidence files

- `c170-commands.log`
