# C115 immutable Node 24 Android CI runtime

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Finding

Android CI #171 completed successfully but retained two GitHub annotations: the checkout, setup-java and setup-gradle v4 actions targeted Node 20 and were being forced onto Node 24, and setup-java v4 was deprecated.

## Correction

The Android-only workflow now uses these signed official release commits rather than mutable major tags:

- `actions/checkout` v6.1.0: `d23441a48e516b6c34aea4fa41551a30e30af803`;
- `actions/setup-java` v5.7.0: `b6effb05e454b25005698d916606bdc6ffcbf961`;
- `gradle/actions/setup-gradle` v6.3.0: `9c971963bec38e04b3d30dcc455b5382be2fdbfb`.

All three use Node 24-compatible current majors. The workflow grants only top-level `contents: read`, disables checkout credential persistence, retains `fetch-depth: 0`, uses one `ubuntu-latest` job and keeps Temurin Java 17.

`android/scripts/verify-android-ci-runtime.py` requires the exact action inventory, order, release comments and full SHAs plus the permission/runner/trigger/checkout/Java contract. Its synthetic suite accepts the current workflow once and rejects twenty-three malformed or weakened variants, including mutable tags, extra actions, added/write permission and `pull_request_target`.

## Verification

- Runtime contract: 1 accepted, 23 rejected.
- Direct workflow verification: 3 full-SHA Node 24 actions, `contents: read`.
- Gradle task: `verifyAndroidCiRuntimeContract` passes and is executed inside Android CI.
- Hosted acceptance: the pushed commit must finish the complete Android CI job without Node/action-runtime deprecation annotations.

## Boundary

This hardens the evidence runner and removes a maintenance warning. It does not test physical behavior, authorize a production/Play write, verify a signed release or close any external gate. RG-010 remains unchecked until all dependencies and exact-final-commit evidence pass.
