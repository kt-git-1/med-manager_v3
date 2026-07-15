# C10 UI-005 Signup and Resend State Matrix — 2026-07-15

## Baseline and environment

- iOS/API baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android baseline: `android-dev@b9e8c95`
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, Japanese/light, font scale 1.0 unless stated
- Surface: production `CaregiverAuthFlow` with typed `SessionRepository` transitions and synthetic credentials

## Source comparison and repair

Current iOS `CaregiverSignupView` renders validation/server failures through `ErrorStateView`: a centered 36-point warning icon and multiline red message inside an inset, rounded glass card. Android previously rendered only an uncontained red text line.

Android now uses the same hierarchy:

- 36 dp warning icon;
- centered 17 sp semibold multiline copy with 24 sp line height;
- 22 dp inner padding and 16 dp horizontal inset;
- rounded low-emphasis red container and border;
- one merged semantic surface for the exact error message.

The confirmation notice now follows the iOS 20-unit horizontal inset, 22-unit inner padding, 34-unit mail icon and 12-unit spacing. The resend action also gains the iOS-equivalent rounded secondary background and clock/mail icon while retaining the independent progress state added in C08.

## Evidence

| Required state | Evidence |
|---|---|
| Invalid email | [`android-ui-005-caregiver-signup-invalid-email-light.png`](android-ui-005-caregiver-signup-invalid-email-light.png) |
| Password mismatch | [`android-ui-005-caregiver-signup-password-mismatch-light.png`](android-ui-005-caregiver-signup-password-mismatch-light.png) |
| Signup in flight | [`android-ui-005-caregiver-signup-loading-light.png`](android-ui-005-caregiver-signup-loading-light.png) |
| Confirmation resend success | [`android-ui-005-caregiver-signup-resend-success-light.png`](android-ui-005-caregiver-signup-resend-success-light.png) |
| Confirmation resend 429 | [`android-ui-005-caregiver-signup-resend-rate-limit-light.png`](android-ui-005-caregiver-signup-resend-rate-limit-light.png) |

The refreshed 200%-font confirmation/cooldown image remains at [`../c01-20260714/android-ui-005-caregiver-signup-confirmation-font-2.0.png`](../c01-20260714/android-ui-005-caregiver-signup-confirmation-font-2.0.png). The resend-in-flight image remains at [`../c08-20260715/android-ui-005-caregiver-signup-resend-loading-light.png`](../c08-20260715/android-ui-005-caregiver-signup-resend-loading-light.png).

## Verification

- `CaregiverAuthFlowScreenTest` now has nine API-35 tests covering form destinations, invalid email, password mismatch, signup loading, confirmation-required at 200%, resend loading/success/429, stale-state clearing and pinned copy.
- Error and info states assert their production semantic containers before capture.
- JVM tests, Debug assembly and Lint remain required at the checkpoint gate.

Live Supabase signup/email delivery remains release-environment verification. The deterministic local validation, progress, confirmation and resend result matrix is complete.
