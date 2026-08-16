# C78 physical Firebase Messaging token preflight

**Date:** 2026-08-17
**Branch:** `android-dev`
**Device class:** SHARP A302SH, Android 15/API 35, Debug APK

## Safety boundary

- No FCM token value was printed, returned, copied or committed.
- No caregiver/patient login, backend token registration, push send, medical-data request, production mutation or schema deployment occurred.
- The preflight immediately disabled Messaging auto-init, deleted the Firebase token and cleared app-owned enabled/token/registered/pending state.
- A second cleanup succeeded before app data clearing and uninstall.

## Diagnostic design

`FirebasePushDiagnosticReceiver` exists only in the Debug source set. Its exported manifest entry requires `android.permission.DUMP`, accepts only explicit verify/cleanup actions and logs fixed status codes. Release merged-manifest inspection proves that the component and actions are absent.

The verify action initializes the configured Firebase app, temporarily enables Messaging, requests a token and reports only `TOKEN_READY` or a fixed failure code. Every outcome enters cleanup; cleanup disables auto-init, calls Firebase token deletion, clears app-owned token state and reports only `CLEANUP_READY` or `CLEANUP_FAILED`.

## Results

| Gate | Result |
|---|---|
| Configured Firebase runtime gate | PASS |
| A302SH token acquisition | PASS — `TOKEN_READY`, token value never logged |
| Immediate cleanup | PASS — `CLEANUP_READY` |
| Explicit second cleanup | PASS — `CLEANUP_READY` |
| End state | PASS — app data cleared and package uninstalled |
| Release diagnostic exclusion | PASS — component/action absent from merged Release manifest |
| Debug/Release JVM | PASS — 216/216 and 213/213, 0 failed/skipped |
| Lint / Release assembly / Release APK compatibility / Play assets | PASS |
| GitHub Android CI | PASS — run `31960548523` on implementation commit `55a1a01`; Firebase runtime, Unit tests, Debug build, Lint, Release compatibility and Play listing assets all completed successfully |

## Remaining FCM evidence

This preflight proves Firebase installation/token connectivity only. It does not close FC-001 backend registration or FC-002–FC-010. Those rows still require caregiver-authenticated registration/unregister, token refresh, generic taken/missed delivery and tap routing, Doze/App Standby, duplicate/invalid payloads, self-exclusion, offline retry, permission revocation and the exact signed Play artifact.
