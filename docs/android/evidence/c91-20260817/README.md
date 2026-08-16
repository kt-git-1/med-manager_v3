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
- The first clean-commit run caught the policy test's own untracked Python bytecode cache under `android/scripts`; bytecode generation was disabled and the cache was removed instead of weakening the dirty-input boundary.
- From clean implementation commit `4749ab9`, the complete synthetic `bundleSignedRelease` graph passed 74 tasks and atomically wrote the JSON ledger only after signature verification.
- Independent shell/JQ checks matched the ledger to that exact AAB SHA-256, upload certificate, dumped base-manifest hash, full source commit, package/version/SDK identity, base-only four-DEX/eight-native-library structure, 175-module SDK inventory and 187-coordinate Gradle lock. C93 subsequently extends the ordered evidence set from eight to nine names with `bundle-install-surface`; C94 extends it to ten with `device-split-install-surface`. Later evidence verifies those contracts rather than rewriting the historical C91 run.
- No temporary JSON file remained after the atomic replacement.
- A final clean ordinary regression passed 108 tasks: evidence contract 1/11, upload-keystore contract 1/7, Debug JVM 216/216, Release JVM 213/213, Lint, 175-module SDK policy, Release APK manifest/16 KB compatibility, validated base-only unsigned AAB content and Play assets.
- That final `clean` removed the synthetic keystore, signed AAB and JSON ledger. No generated keystore or release ledger remains in `app/build`.
- Hosted Android CI [run #31974761066](https://github.com/kt-git-1/med-manager_v3/actions/runs/31974761066) succeeded for `5025e2c`; the Linux Release evidence policy/atomic-JSON step passed, followed by the complete existing Android CI job.

## Deliberately incomplete external evidence

- No release-owner upload key or production runtime signed AAB was used.
- A synthetic clean-source ledger proves the mechanism only; it is not the production submission ledger.
- Independent Play Console certificate comparison, processing/scan, track installation and rollout evidence remain required.
