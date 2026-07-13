# Android Phase 1: Session and API Core

**Status: PARTIAL. This document previously overstated completion.**

The implemented foundation is retained, but Phase 1 is complete only when the `SH-*` and scoped `AU-*` rows in `parity-requirements.md` reach `VERIFIED`.

## Implemented foundation

- Supabase caregiver login and refresh-token exchange
- Patient linking-code exchange through the existing API
- AES-GCM session encryption backed by Android Keystore
- Mode/session restoration and expiry handling
- Shared HTTP status/error mapping
- Caregiver login and patient linking Compose screens

The backend remains authoritative. Caregiver API tokens retain the existing `caregiver-` prefix expected by the Next.js middleware, while Supabase refresh calls use the raw refresh token.

## Runtime configuration

Runtime Supabase values are intentionally not committed. Add `SUPABASE_URL` and `SUPABASE_ANON_KEY` to ignored `android/local.properties`, or export them as environment variables before building. `API_BASE_URL` defaults to `https://www.okusuri-mimamori.com/` and can be overridden the same way.

## Verification performed so far

- `./gradlew test assembleDebug lint`
- Current Android unit tests pass in both debug and release variants.
- Production `/api/health` responds successfully.
- A debug APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

Physical-device installation is pending because no Android device is currently visible to ADB.

## Required before Phase 1 completion

- Physical-device session restoration verification
- High-fidelity mode select, auth choice, login, signup, confirmation callback, and resend flows

## SH-004 / SH-006 implementation evidence

### Reference

- iOS: `APIClient.refreshPatientAuthenticationIfNeeded`, `APIClient.send`, `SessionStore.invalidatePatientToken`
- Backend: `POST /api/patient/session/refresh`
- DTO: `PatientSessionResponseDTO` / `PatientSessionTokenDTO`

### Implemented contract

- A near-expiry patient token is refreshed before the protected request.
- A patient request returning 401 triggers one forced refresh and one retry.
- The retry rebuilds the Authorization header from the newly persisted token.
- The refresh endpoint itself disables refresh retry to prevent recursion.
- Refresh failure or a second 401 invalidates the patient token.
- Invalidation retains patient mode so the UI returns to linking rather than mode selection.
- Missing `expiresAt` uses the same 30-day fallback policy as the iOS session store.

### Automated evidence

- Refresh response parser: token, optional expiry, missing-token rejection
- Repository: near-expiry persistence and invalidation state transition
- HTTP client: proactive failure, refresh failure, new-token retry, second-401 stop
- `./gradlew test assembleDebug lint` passes for debug and release variants

Status remains `IMPLEMENTED`, not `VERIFIED`, until a real patient session is exercised on a physical device.

## SH-003 implementation evidence

- Caregiver refresh is guarded by a mutex so overlapping foreground/UI requests share one refresh operation.
- The expiry condition is checked again after acquiring the mutex, preventing a second refresh after the first succeeds.
- A successful refresh preserves `currentPatientId`, matching iOS `preserveCurrentPatientId: true` behavior.
- Refresh failure clears caregiver credentials but retains caregiver mode, returning the user to login rather than mode selection.
- Unit tests cover overlapping refresh calls and refresh failure state transition.

Status remains `IMPLEMENTED`, not `VERIFIED`, until real Supabase refresh is exercised on a physical device.

## SH-005 implementation evidence

Android maps API failures to typed errors before they reach repositories or Compose:

- 400/422: validation with all `messages` joined
- 401: unauthorized
- 403: generic forbidden, `PATIENT_LIMIT_EXCEEDED`, or `HISTORY_RETENTION_LIMIT`
- 404: not found
- 409: generic conflict or `insufficient_inventory`
- 429: rate limited with a safe Japanese retry message
- 5xx: server failure without exposing backend internals
- Transport failure: network failure

Structured errors preserve `limit/current` and `cutoffDate/retentionDays`, allowing later screens to render the same specialized UI as iOS without parsing strings. Tests cover each domain contract and the general HTTP families.

`SH-005` is `IMPLEMENTED`. Feature UI remains tracked separately, including `PT-008` inventory display and `PH-004` retention-lock UI.

## SH-007 implementation evidence

Instrumentation tests run inside an Android 15 emulator and verify the real Android Keystore/SharedPreferences path:

- Stored ciphertext does not contain the access token in plaintext.
- A newly created `AndroidSessionStorage` instance restores mode, selected patient, access token, and refresh token.
- A newly created `SessionRepository` restores a valid patient session.
- An expired patient token is removed while patient mode remains selected, returning the app to linking.
- `adb shell am force-stop` followed by a cold `MainActivity` launch succeeds without a crash.

Verification command: `./gradlew test assembleDebug lint connectedDebugAndroidTest`.

`SH-007` is `IMPLEMENTED`, not `VERIFIED`, until the same restoration checks pass on a physical Android device.

## AU-001 mode-select implementation evidence

- Reuses the exact `RolePatient` and `RoleFamily` image assets from the iOS asset catalog through a generated-resource Gradle task.
- Matches the iOS Japanese copy, two-line 38sp title hierarchy, 22dp page inset, 24dp card radius, 112dp illustrations, role badges, semantic teal/orange colors, and circular forward actions.
- Uses the production Compose screen for screenshots and interaction tests; there is no duplicate screenshot-only layout.
- Compose instrumentation tests verify canonical content and patient-card selection.
- Reference captures:
  - iOS: `/Users/kaito/.codex/visualizations/2026/07/12/019f54b5-867d-7a21-8c6c-0827f3167ce6/ios-mode-select-reference.png`
  - Android: `/Users/kaito/.codex/visualizations/2026/07/12/019f54b5-867d-7a21-8c6c-0827f3167ce6/android-mode-select.png`

`AU-001` is `IMPLEMENTED`. Dark mode, large text, and physical-device comparison remain before `VERIFIED`.

## AU-002 patient-link implementation evidence

- Matches the iOS `PatientHeader`, 62dp link emblem, title/subtitle hierarchy, 20dp page inset, 18dp patient card, teal outlined numeric field, 58dp submit button, inline red error, and mode-reset action.
- Input is restricted to six numeric characters; the submit action remains disabled until exactly six digits are present.
- Validation and not-found responses use the canonical iOS Japanese messages.
- Success persists the returned patient token/expiry and transitions through the real session state.
- Repository tests cover local validation without network access, 404/expired handling, normalized submission, and successful persistence.
- Compose tests cover canonical content, disabled/enabled state, sanitization, submission, and inline error rendering.
- Reference captures:
  - iOS: `/Users/kaito/.codex/visualizations/2026/07/12/019f54b5-867d-7a21-8c6c-0827f3167ce6/ios-link-code-reference.png`
  - Android: `/Users/kaito/.codex/visualizations/2026/07/12/019f54b5-867d-7a21-8c6c-0827f3167ce6/android-link-code.png`

`AU-002` is `IMPLEMENTED`. A real linking-code exchange, rate-limit response, large text, dark mode, and physical-device comparison remain before `VERIFIED`.

## AU-003 through AU-007 caregiver-auth implementation evidence

- The caregiver choice screen reproduces the login/signup decision, supporting copy, and mode-reset action as a dedicated Compose surface.
- Login uses the existing Supabase password grant through `SessionRepository`, including disabled/loading/inline-error states.
- Signup validates email format, six-character minimum password, and password confirmation locally before any request.
- Signup and resend both send the same configurable confirmation redirect as iOS: `https://www.okusuri-mimamori.com/auth/confirmed` by default.
- The manifest accepts only the two production HTTPS hosts under `/auth/` plus `okusurimimamori://auth/login`.
- Callback handling rejects unrelated hosts, clears stale caregiver credentials, preserves caregiver mode, and consumes a one-time navigation request to the login form.
- The confirmation view enables resend with a 60-second countdown; Supabase status 429 maps to a stable Japanese retry-later message.
- Repository tests cover validation, confirmation state, resend invocation, all accepted callback variants, callback rejection, and one-time navigation consumption.
- Compose tests cover the choice content and independent login/signup actions.
- An Android 15 cold-launch check opened the production HTTPS confirmation URL into `MainActivity` and landed on the login form.
- Android callback capture: `/Users/kaito/.codex/visualizations/2026/07/12/019f54b5-867d-7a21-8c6c-0827f3167ce6/android-auth-link-login.png`

`AU-003` through `AU-007` are `IMPLEMENTED`. Real Supabase credentials/email delivery, live 429 behavior, verified domain association, dark mode, large text, and physical-device comparison remain before `VERIFIED`.

## SH-008 shared theme implementation evidence

- `MedicationAppTheme` now follows the system light/dark setting and exposes complete Material color schemes instead of a light-only subset.
- `MedicationTheme.colors` carries iOS-equivalent semantic colors for teal text, caregiver blue, orange, indigo, patient/caregiver danger, elevated surfaces, readable secondary text, strokes, shadows, and all four medication slots.
- Entry mode, patient linking, and caregiver authentication consume semantic background, surface, text, action, and error colors rather than screen-local light constants.
- A Compose instrumentation test asserts the canonical light and dark background/secondary-text tokens and proves that theme selection changes them.
- Android 15 dark-mode capture: `/Users/kaito/.codex/visualizations/2026/07/12/019f54b5-867d-7a21-8c6c-0827f3167ce6/android-mode-select-dark.png`
- The full JVM/build/lint suite and 11 Android 15 connected tests pass.

`SH-008` is `IMPLEMENTED`. Patient Today/history legacy color consumers will be replaced while implementing their respective parity rows; matched iOS dark screenshots and physical-device verification remain before `VERIFIED`.
