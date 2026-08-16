# C77 Android missed-dose FCM privacy envelope

**Date:** 2026-08-17
**Branch:** `android-dev`
**Reference:** published iOS 1.0.6 Build 51, `main@432b34c`

## Finding

The server already sent Android `DOSE_TAKEN` as high-priority data-only FCM so the Android service could render generic local copy. `DOSE_MISSED` still passed the iOS notification/APNs envelope to every platform. In the Android background case, Firebase could therefore render patient-specific server text without entering the app-owned privacy-safe renderer.

## Correction

- Both event types now branch on the registered device platform.
- Android receives no `notification` or APNs envelope, only strict `type`, `patientId`, `date` and `slot` navigation data with Android high priority.
- The patient display name is not present in the Android payload.
- iOS retains its existing patient-facing notification/APNs behavior.
- Android continues to validate the data and render fixed generic resources locally.

## Verification

| Gate | Result |
|---|---|
| Targeted push integration suite | PASS — 16/16 |
| Complete API suite | PASS — 333/333 across 75 files |
| TypeScript typecheck | PASS |
| ESLint | PASS |
| Production traffic/data mutation | NOT RUN — no token registration, FCM send, health-data mutation or schema deployment |

The new regression isolates one Android missed-dose device, asserts that notification/APNs arguments are absent, asserts high priority, checks the exact strict data payload and rejects leakage of the synthetic display name.

## Remaining physical gate

C77 closes the sender-construction defect but does not claim physical FCM acceptance. FC-001–FC-010 in `physical-device-matrix.md` still require the configured physical device, production-shaped server sender, generic tray copy, taken/missed routing, Doze/App Standby, duplicate/invalid payload, token refresh, unregister and exact Play artifact evidence.
