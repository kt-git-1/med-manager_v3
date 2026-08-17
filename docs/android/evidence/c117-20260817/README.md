# C117 Google Play generated-APK signing receipt contract

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

`android/scripts/verify-play-generated-apks-receipt.py` binds the official Google Play Developer API v3 `generatedapks.list` response to the exact C116 Internal-track receipt and therefore to the retained C113 handoff, C114 upload response and C116 published/completed version.

The verifier requires the production package targeting, at least one strict generated split group and a base-module master split under every signing key. The complete API certificate set must exactly equal the independently supplied App Links app-signing set and must be disjoint from the upload certificate recorded in the retained signed-AAB ledger. Hex, colon-hex and 32-byte base64 certificate hash representations normalize to one uppercase colon fingerprint.

The generated receipt retains only fixed package/commit/version/AAB identity, hashes of the C116 receipt and raw API response, normalized public certificate fingerprints and aggregate signing-key/split/download counts. Download IDs, request URLs, OAuth data, account/tester identity, credentials, release notes and patient data are excluded.

## Verification

- Generated-APK receipt contract: 4 accepted/idempotent, 36 rejected.
- Accepted coverage: one key, byte-identical re-entry, CLI and two-key rotation with base64 normalization.
- Rejected coverage: missing/unsafe/malformed/oversized response, schema/package/certificate/split/targeting drift, upload-key reuse, missing/tampered C116 chain and unsafe/conflicting output.
- Residual gate contract: one accepted, 51 rejected; RG-005 and RG-006 both retain C117.
- Hosted Android CI: runs the dedicated Gradle contract.
- Clean local Android regression: 146 actionable Gradle tasks, 144 executed and 2 up-to-date; Debug/Release unit tests, Lint, assemblies, APK/AAB policies, universal/device-split surfaces, store assets and all non-secret release contracts passed.
- The four untracked Firebase runtime values remain hosted-secret inputs; the pushed Android CI run must execute that real-value gate before C117 acceptance.

Official schema reviewed on 2026-08-17: [`generatedapks.list`](https://developers.google.com/android-publisher/api-ref/rest/v3/generatedapks/list) returns generated APK metadata grouped by signing key, and identifies that key through `certificateSha256Hash`.

## Authority boundary

All fixtures are synthetic and no Play API call or artifact download occurs here. A real C117 receipt proves that owner-retained metadata for the exact requested version has the expected signing identities and generated split surface; it does not prove the downloaded APK bytes, Play installer identity, installation/update behavior or device acceptance. RG-005/RG-006 remain unchecked until the owner supplies real responses/artifacts and completes every dependent gate.
