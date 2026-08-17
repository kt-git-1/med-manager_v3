# C113 retained Play handoff verification

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

`android/scripts/verify-prepared-play-release-handoff.py` provides a read-only upload-time verification for a retained or transferred handoff directory. It does not need the original build-output AAB or evidence path.

The verifier requires:

- a real, non-symlink directory named from ledger version/code/commit;
- exactly the renamed AAB, `play-release-evidence.json` and `SHA256SUMS`;
- canonical schema-v2 JSON bytes;
- production package/version/source/base-module/gate identity;
- packaged AAB and evidence hashes matching the ledger/checksum file;
- the current Japanese listing/source-map/icon/feature/eight ordered screenshot hashes matching the ledger;
- no symlink, missing or extra entry.

The existing handoff contract now verifies both creation/idempotency and retained upload-time validation. It rejects an extra file, checksum tamper, noncanonical evidence with recomputed checksum, store drift, renamed directory and symlinked target in addition to the existing source/evidence/artifact failures.

## Verification

- Handoff contract: 3 accepted/idempotent, 17 rejected, including the documented CLI.
- Retained exact handoff: accepted.
- Six retained/transfer drift cases: rejected.

## Boundaries

This verifies repository and retained bytes immediately before upload. It does not authenticate Play Console, prove the uploader selected these files, replace release-owner certificate comparison or close Internal/Closed track evidence. `RG-005` remains unchecked.
