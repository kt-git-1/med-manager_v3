# C91 Exact signed-artifact evidence ledger

**Date:** 2026-08-17

**Branch:** `android-dev`

**Source baseline:** published iOS/API `main@432b34c`

**Parity row:** XP-010 remains `PARTIAL`

## Contract

- `bundleSignedRelease` must end at the evidence task, not merely signature verification.
- Refuse uncommitted Android, consumed iOS role/icon or Play listing/asset inputs.
- Validate canonical application ID, positive version code, semantic version name and a full Git commit SHA.
- Bind the exact AAB and dumped base manifest hashes, actual/expected upload certificate, base-only structure, DEX/native counts, dependency lock and resolved SDK inventory.
- Atomically write one machine-readable JSON ledger only after every production gate passes.
- Emit no runtime key, password, token, health data or user identity.

## Current verification

- `origin/main` remains the published `432b34c` baseline; the two `origin/staging` commits remain iOS-only Analytics/Xcode CI changes and were not merged.
- Pure policy contract: one valid fixture passed and eleven invalid package/version/source/dirty/hash/signer/module/DEX/SDK/lock fixtures failed closed.
- A full synthetic signed AAB passed runtime, upload-key, SDK, APK, AAB, asset and signature gates, then was deliberately rejected because `android/app/build.gradle.kts` was not yet committed. The failure listed that release input and did not write a release ledger.

## Deliberately incomplete external evidence

- No release-owner upload key or production runtime signed AAB was used.
- A synthetic clean-source ledger proves the mechanism only; it is not the production submission ledger.
- Independent Play Console certificate comparison, processing/scan, track installation and rollout evidence remain required.
