# C168 Signed-release synthetic env-path check (2026-08-19)

- Date: 2026-08-19
- Branch: `android-dev`
- Commit head at run: `5878ea9`
- Command: `./gradlew :app:verifySignedReleaseBundle --no-daemon -q` (executed twice with different env setups)

## Results

- `PLAY_UPLOAD_CERT_SHA256` path: PASS
  - release keystore, signed AAB build, release bundle signer/SDK/manifest/split-surface gates passed.
- `EXPECTED_UPLOAD_CERT_SHA256` only path: FAIL (expected)
  - `verifyProductionSigning` requires `PLAY_UPLOAD_CERT_SHA256` to be configured in the Play-signing contract boundary.

## Notes

- This checkpoint uses a temporary synthetic PKCS12 key in `/tmp` and synthetic runtime values only.
- No secrets are introduced into repository or logs, and no source files were modified.
- Full command output is retained in `c168-commands.log`.
