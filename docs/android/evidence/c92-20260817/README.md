# C92 Atomic Play release handoff evidence

**Date:** 2026-08-17

**Branch:** `android-dev`

**Source baseline:** published iOS/API `main@432b34c`

**Parity row:** XP-010 remains `PARTIAL`

## Contract

- Make `bundleSignedRelease` end at the handoff task, after the complete C91 graph.
- Reparse and revalidate the exact C91 schema, clean source, production identity/version, source AAB hash, upload-certificate form, base-only module and ordered gate set. C93 extends that set from eight to nine with `bundle-install-surface`; the C93 contract rerun proves the handoff fails if the new gate is absent.
- Create a version/code/commit-named directory atomically.
- Allow exactly one correspondingly named AAB, byte-identical `play-release-evidence.json` and two-entry `SHA256SUMS`.
- Accept an existing target only when all three files still match; reject symlinks, missing/extra files and conflicts.
- Keep keys, passwords, runtime credentials, user/health data and Console actions outside the handoff.

## Current verification

- Standalone contract accepted one exact handoff and its idempotent rerun.
- Seven fixtures failed closed: tampered source AAB, dirty source ledger, wrong application ID, malformed signer, added feature module, missing gate and tampered existing packaged AAB.
- Temporary construction directories were absent after both success and rejection.
- C91 JSON was made deterministic for identical inputs by removing a non-identity generation timestamp; repeated packaging therefore compares byte-identical ledgers while a changed artifact still conflicts.
- From clean implementation commit `c6148ab`, the complete synthetic `bundleSignedRelease` graph passed 76 tasks and created `v1.0.6-code1-c6148ab47f45` with exactly the named AAB, JSON and checksum manifest.
- `shasum -a 256 -c SHA256SUMS` passed for both files; independent checks matched the JSON commit and AAB hash. The same full task reran idempotently in 7 seconds (15 executed, 61 up-to-date) without replacing the handoff.
- A final clean ordinary regression passed 109 tasks: handoff contract 1/idempotent/7, evidence contract 1/11, keystore contract 1/7, Debug JVM 216/216, Release JVM 213/213, Lint, 175-module SDK policy, APK/16 KB policy, base-only AAB policy and Play assets.
- Final `clean` removed the synthetic key, signed AAB, JSON, checksum and handoff directory; no generated release package remains.
- Hosted Android CI [run #31975815710](https://github.com/kt-git-1/med-manager_v3/actions/runs/31975815710) passed all 23 steps for `fec2b5d`, including the Linux evidence and Play handoff contracts plus every existing Android release gate.

## Deliberately incomplete external evidence

- No release-owner upload key or production runtime signed AAB/handoff was used.
- A synthetic handoff proves packaging and pairing only; it is not upload authorization or Play evidence.
- Independent Play Console fingerprint comparison, processing/scan, track installation and rollout evidence remain required.
