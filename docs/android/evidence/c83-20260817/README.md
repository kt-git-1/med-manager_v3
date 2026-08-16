# C83 Android CI alignment and release-input audit — 2026-08-17

## Source synchronization audit

- Android worktree branch: `android-dev`
- Starting checkpoint: `aabca16` (C82)
- `origin/main`: unchanged at the published iOS 1.0.6 Build 51 baseline `432b34c`
- `origin/staging`: two commits not in `android-dev`; their changed paths are iOS source/project dependencies and iOS CI only
- Decision: do not merge unrelated staging history into the isolated Android branch. The Android Analytics contract already follows the fixed-enum source boundary and has its own C76/C80 runtime evidence.

## CI gap and correction

The pre-C83 workflow passed Firebase runtime, Debug JVM, Debug build, Lint, Release APK compatibility and Play listing assets. It did not execute Release JVM tests or validate the checked-in C82 shard runner contract.

C83 adds two push/PR gates:

1. `Connected shard runner contract`
   - Bash syntax
   - help path
   - invalid shard-count rejection
   - invalid single-shard-index rejection
2. `Unit tests (Debug and Release)`
   - `testDebugUnitTest`
   - `testReleaseUnitTest`

The workflow deliberately does not run the 840 connected tests on a GitHub-hosted emulator. C82 owns the clean API 26/33/35 AVD evidence, and C81 owns the bounded physical A302SH run.

## Local verification

- Workflow YAML syntax: pass
- Runner syntax/help/invalid-argument contract: pass
- Debug JVM: 216/216, 0 failed, 0 skipped
- Release JVM: 213/213, 0 failed, 0 skipped
- `verifyReleaseApkCompatibility`: pass for application ID, min/target SDK, forbidden advertising/attribution permissions, 16 KB ZIP alignment and native ELF load-segment alignment
- `verifyProductionRuntime`: expected fail-closed because the local worktree has no approved production Supabase/Firebase values
- `verifyProductionSigning`: expected fail-closed because no release-owner upload keystore/password/alias inputs are configured

No production values, signing values, device identifiers or user/medical data were printed or written.

## Hosted CI

- Pre-change C82 Android CI run #140: `PASS`
- C83 Android CI run #141: `PASS`
  - Firebase runtime: success
  - Connected shard runner contract: success
  - Debug and Release JVM tests: success
  - Debug build: success
  - Lint: success
  - Release APK compatibility: success
  - Play listing assets: success

Run #141 proves the two new steps and every retained downstream gate on commit `48f5611`. A hosted CI pass still does not satisfy Analytics Explore, physical spoken TalkBack, production Android FCM deployment/delivery, missing device classes, signed AAB or Play Console evidence.
