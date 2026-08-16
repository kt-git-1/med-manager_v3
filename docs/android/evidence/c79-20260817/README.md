# C79 authenticated production FCM registration preflight

**Date:** 2026-08-17
**Branch:** `android-dev`
**Production baseline:** `origin/main@432b34c`
**Device class:** SHARP A302SH, Android 15/API 35, configured Debug APK

## Scope and safety

- Used only the retained dedicated QA caregiver and its synthetic patient.
- The password remained Keychain-only; neither credential, caregiver/patient identifier nor FCM token was printed or committed.
- No medication, dose, inventory, patient, account or schema mutation occurred.
- Notification permission was granted only to the temporary Debug install.

## Physical sequence

1. Install the production-runtime/Firebase-configured Debug APK and keep Analytics OFF.
2. Log in with the dedicated QA caregiver, restore the encrypted Caregiver session and skip the static tutorial.
3. Open Caregiver Settings; `見守り通知` starts OFF.
4. Toggle ON. Firebase token acquisition succeeds and the client reaches authenticated backend sync with `platform=android`, `environment=DEV`.
5. The UI shows the retryable sync-failure state because live production returns HTTP 422.
6. Toggle OFF, run explicit Firebase cleanup, verify no enabled/token/registered/pending keys remain, then clear app data, uninstall and remove temporary artifacts.

## Root cause evidence

- The live error is the fixed validation result `platform must be ios`.
- `origin/main@432b34c` has `VALID_PLATFORMS = ["ios"]`.
- The production route validates before calling `upsertPushDevice`; this request therefore creates no PushDevice row.
- `android-dev` already has `VALID_PLATFORMS = ["ios", "android"]`, exact Android register/unregister contract tests and green API/CI evidence under C77.

## Result

| Gate | Result |
|---|---|
| Dedicated QA login/session restore | PASS |
| Notification permission and Settings OFF baseline | PASS |
| Firebase token acquisition | PASS — value never exposed |
| Authenticated Android register request | REACHED |
| Production register response | BLOCKED_BY_DEPLOYMENT — HTTP 422 iOS-only validation |
| Device upsert | NOT EXECUTED — validation occurs first |
| UI failure state | PASS — retryable sync failure is visible |
| OFF/token/private-state cleanup | PASS |
| End state | PASS — session, app package and temporary artifacts removed |

## Required continuation

Do not weaken the Android client to send `platform=ios`: the platform value selects the server's Android data-only privacy envelope. After Android/API completion is approved for merge, deploy the tested validator and sender changes, rerun FC-001 until registration succeeds, then execute FC-002–FC-010 delivery/routing/Doze/dedup/invalid-payload/unregister evidence on the exact release artifact.
