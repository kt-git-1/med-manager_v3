# C11 UI-004 Login States and Auth IME Flow — 2026-07-15

## Baseline and environment

- iOS/API baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android baseline: `android-dev@60c9300`
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, Japanese/light, font scale 1.0
- Surface: production `CaregiverAuthFlow` and `SessionRepository`, using synthetic credentials

## Implemented input contract

Android auth fields previously relied on the keyboard's unspecified default IME behavior. The production login and signup forms now define the complete input order:

- login email: email keyboard + `Next` -> password;
- login password: password keyboard + `Done` -> submit when ready;
- signup email: email keyboard + `Next` -> password;
- signup password: password keyboard + `Next` -> confirmation;
- signup confirmation: password keyboard + `Done` -> submit when ready;
- `Done` clears field focus/keyboard and uses the same loading guard as the visible primary action;
- incomplete or already-loading forms cannot submit through IME.

API-35 instrumentation drives the actual IME semantics, asserts each focused destination and verifies the completed login/signup repository transition.

## Login state evidence

| Required state | Evidence |
|---|---|
| Login in flight | [`android-ui-004-caregiver-login-loading-light.png`](android-ui-004-caregiver-login-loading-light.png) |
| Invalid credentials | [`android-ui-004-caregiver-login-invalid-credentials-light.png`](android-ui-004-caregiver-login-invalid-credentials-light.png) |

The loading state preserves both fields, disables the action and replaces the exact button label with progress. The failure state preserves the typed values, restores the action and uses the C10 iOS-style warning-card hierarchy with exact current-iOS copy.

## Verification

- `CaregiverAuthFlowScreenTest.loginImeNextMovesFocusAndDoneSubmits`
- `CaregiverAuthFlowScreenTest.signupImeNextTraversesAllFieldsAndDoneSubmits`
- `CaregiverAuthFlowScreenTest.loginLoadingAndInvalidCredentialsUseIosFeedbackHierarchy`
- Full caregiver-auth API-35 class: 12/12 passed during evidence capture

Live Supabase credential acceptance and physical keyboard/OEM IME behavior remain release-device verification. Emulator-verifiable login loading, invalid-credential and IME traversal contracts are complete.
