# C08 UI-005 Confirmation Resend Lifecycle — 2026-07-15

## Baseline

- iOS/API baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android branch baseline: `android-dev@e9308de`
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, font scale 1.0, Japanese/light
- Android surface: production `CaregiverAuthFlow` and `SessionRepository`, driven through the signup form

## Source comparison and repair

Current iOS `CaregiverSignupView` has an independent `isResending` state. It clears the previous notice while resending, disables duplicate resend, displays progress in the resend action, and starts the 60-second cooldown only after a successful resend or a rate-limit response.

Android previously started its local cooldown immediately on tap and exposed no in-flight state. Consequently, an ordinary network/server failure incorrectly left the resend action blocked for 60 seconds.

Android now publishes `resendingConfirmation` and a monotonic `resendCooldownRevision` from `SessionRepository`:

- initial confirmation-required response starts the cooldown;
- resend in flight clears transient copy, retains the form, shows progress and rejects duplicate submission;
- successful resend starts a new cooldown and shows the exact iOS success notice;
- rate limit starts a new cooldown and shows the exact iOS 429 notice;
- other failures restore the resend action immediately with no false cooldown.

## Evidence

| State | Evidence |
|---|---|
| Confirmation resend in flight | [`android-ui-005-caregiver-signup-resend-loading-light.png`](android-ui-005-caregiver-signup-resend-loading-light.png) |

The screenshot contains synthetic credentials only. It was emitted by the production Compose component assertion and persisted through the existing screenshot-fixture path.

## Verification

- `SessionRepositoryTest` covers initial/success revision changes, completed in-flight state, rate-limit cooldown, and no cooldown for ordinary/unknown failures.
- `CaregiverAuthFlowScreenTest.resendShowsProgressAndStartsCooldownOnlyAfterSuccess` drives signup, confirmation-required, resend in flight and success through the typed repository.
- The full seven-test API-35 caregiver-auth instrumentation class passes.
- JVM tests, Debug APK, Debug AndroidTest APK and Lint pass.

Live email delivery and live Supabase rate-limit behavior remain release-environment verification; the local state contract no longer depends on those services.

The subsequent C10 visual-parity repair added the current-iOS-style inset error/info cards and rounded icon-bearing resend action without changing this lifecycle contract. See [`../c10-20260715/README.md`](../c10-20260715/README.md).
