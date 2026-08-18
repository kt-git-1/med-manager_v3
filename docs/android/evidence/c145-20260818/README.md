# C145 Local release-contract verification suite — 2026-08-18

**Date:** 2026-08-18

**Branch:** `android-dev`  
**Commit:** `6f92f10`

## Scope

Executed the repository-owned verifier contracts that are safe to run locally and do not require production credentials:

- `scripts/test-verify-release-gates.py`
- `scripts/test-verify-android-ci-runtime.py`
- `scripts/test-verify-release-manifest-policy.py`
- all Python verifier fixtures under `scripts/test-*.py`
- all shell verifier fixtures under `scripts/test-*.sh`

## Commands

```bash
cd android
python3 scripts/test-verify-release-gates.py
python3 scripts/test-verify-android-ci-runtime.py
python3 scripts/test-verify-release-manifest-policy.py
for f in scripts/test-*.py; do python3 "$f"; done
for f in scripts/test-*.sh; do bash "$f"; done
```

## Results

- Release gate contract: passed (`accepted=1`, `rejected=70`)
- Android CI runtime contract: passed (`accepted=1`, `rejected=23`)
- Release manifest policy tests: passed (`12` tests)
- Python verifier fixtures:
  - `extract-universal-apk`, `generate-release-evidence`, `prepare-play-release-handoff`,
    `verify-device-split-set`, `verify-main-merge-surface`,
    `verify-play-*-receipt` families, `verify-production-app-links`,
    `verify-release-gates`, `verify-release-manifest-policy` all passed
- Shell verifier fixtures:
  - shard-runner, split-install, signed-AAB, upload-keystore all passed

## Notes

- This evidence is local contract verification only.
- No app/runtime state changed by these runs; no Play/production accounts, no migration, and no real Firebase/Play deployment actions were performed.
- Devices currently listed but not used by this contract run:
  - `SX3LHMB430113755` (A302SH)
  - `emulator-5554` (API 35)
