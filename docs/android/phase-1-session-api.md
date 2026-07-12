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

- Full typed response/error contract coverage
- Concurrent caregiver refresh protection
- Process-death and physical-device session restoration verification
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
