# C87 strict Release dependency-lock evidence

**Date:** 2026-08-17
**Branch:** `android-dev`
**Source baseline:** published iOS/API `main@432b34c`
**Parity row:** XP-009 remains `PARTIAL`

## Contract

- Activate Gradle dependency locking only for `releaseRuntimeClasspath`.
- Use `LockMode.STRICT` so missing or stale lock state fails resolution.
- Check in the generated external-module lock state and make it an explicit `verifyReleaseSdkPolicy` input.
- Require intentional dependency updates to regenerate and review the lock before repeating SDK, permission and vendor-disclosure checks.
- Do not broaden the lock to unrelated Debug/test configurations.

## Results

- Generated `android/app/gradle.lockfile`: 174 external resolved module coordinates plus Gradle's terminal `empty` state.
- Locked Release graph plus C86 policy: passed; the runtime inventory remains 175 modules including the app component.
- Missing-lock negative contract: rejected with `Locking strict mode` and `does not have lock state` before the policy could pass.
- Required Firebase Analytics, Messaging and Installations versions and the known Firebase Analytics advertising-ID/Privacy Sandbox support transitives are pinned.

## Deliberately incomplete external evidence

- Locking makes the reviewed local graph reproducible; it does not replace current Firebase/Google disclosure guidance.
- The release-owner-signed AAB, Play scan, Data safety/Health apps submission and dated Console evidence remain required.
