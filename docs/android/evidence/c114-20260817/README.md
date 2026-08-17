# C114 Google Play upload receipt contract

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

`android/scripts/verify-play-upload-receipt.py` provides a fail-closed comparison between a freshly reverified C113 three-file handoff and the official Google Play Developer API v3 Bundle response returned for an owner-controlled upload or bundle list.

The official Bundle resource contains `versionCode`, SHA-1 and the SHA-256 of the upload payload. The verifier:

- reruns the full retained-handoff verification before reading the API response;
- accepts only the exact direct Bundle fields or official bundles-list envelope;
- requires exactly one matching positive `versionCode` and lowercase SHA-1/SHA-256 values;
- requires the Play upload-payload SHA-256 to equal the retained AAB/ledger/checksum SHA-256;
- writes a canonical deterministic receipt atomically outside the three-file handoff;
- records only package, handoff/commit/version identity, AAB/API-response hashes and Bundle hashes;
- records no OAuth token, authorization header, service-account value, edit ID, account identity or patient data.

The hosted contract exercises direct upload, list, CLI and idempotent paths. It rejects missing/symlink/malformed/oversized responses, schema drift, invalid or duplicate bundle identity, wrong version/hash, unsafe/inside-handoff output, conflicting receipt and tampered handoff cases.

## Verification

- Play upload receipt contract: 4 accepted/idempotent, 21 rejected.
- Existing receipt: accepted only when canonical bytes are identical.
- Output: atomic and outside the immutable three-file handoff.
- Android CI: tracks the dedicated Gradle contract.

## Authority boundary

No Play account, API credential, edit, upload or track is created or changed by this evidence. Synthetic API responses do not prove a real upload. `RG-005` remains unchecked until the release owner verifies Organization ownership and signing, performs the exact upload under approved Play authority, retains the real response plus receipt and completes the remaining gate conditions.
