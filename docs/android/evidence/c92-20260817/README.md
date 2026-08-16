# C92 Atomic Play release handoff evidence

**Date:** 2026-08-17

**Branch:** `android-dev`

**Source baseline:** published iOS/API `main@432b34c`

**Parity row:** XP-010 remains `PARTIAL`

## Contract

- Make `bundleSignedRelease` end at the handoff task, after the complete C91 graph.
- Reparse and revalidate the exact C91 schema, clean source, production identity/version, source AAB hash, upload-certificate form, base-only module and ordered eight-gate set.
- Create a version/code/commit-named directory atomically.
- Allow exactly one correspondingly named AAB, byte-identical `play-release-evidence.json` and two-entry `SHA256SUMS`.
- Accept an existing target only when all three files still match; reject symlinks, missing/extra files and conflicts.
- Keep keys, passwords, runtime credentials, user/health data and Console actions outside the handoff.

## Current verification

- Standalone contract accepted one exact handoff and its idempotent rerun.
- Seven fixtures failed closed: tampered source AAB, dirty source ledger, wrong application ID, malformed signer, added feature module, missing gate and tampered existing packaged AAB.
- Temporary construction directories were absent after both success and rejection.

## Deliberately incomplete external evidence

- No release-owner upload key or production runtime signed AAB/handoff was used.
- A synthetic handoff proves packaging and pairing only; it is not upload authorization or Play evidence.
- Independent Play Console fingerprint comparison, processing/scan, track installation and rollout evidence remain required.
