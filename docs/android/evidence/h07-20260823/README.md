# H07 Android Analytics Phase 1 acceptance — 2026-08-23

## Scope

- Branch: `android-dev`
- Staging authority: `origin/staging@e9ec0d3c6b10`
- Rebaseline merge: `b75dedf`
- Application: Android Staging `com.afterlifearchive.medmanager.staging`, version `1.0.0`
- Device: disposable API 35 Android emulator
- APK SHA-256: `5a6706169ce6a66d632cc5c5b5a39f44deb20bbaff788b7e38324b5fb51201d1`
- Data boundary: synthetic fixed enums only; no real patient, caregiver or medication operation was used.

Console screenshots are not committed because the authenticated Console surface contains unrelated account/project metadata. The observations below are the redacted evidence record.

## Consent and transport

| Check | Result |
|---|---|
| Fresh install, explicit refusal | PASS — diagnostic returned `CONSENT_OFF`; SDK logged collection disabled/reset and emitted none of the three custom parity events. |
| Fresh app data, explicit allow | PASS — diagnostic returned `PARITY_EVENTS_EMITTED`. |
| Firebase transport | PASS — the synthetic batch upload completed with HTTP 204. |
| End-of-test cleanup | PASS — Staging data was cleared, the Staging package was uninstalled, `debug.firebase.analytics.app` was reset to `.none.`, and the existing Production package was preserved. |

## DebugView event and parameter inspection

Firebase DebugView showed the selected Android 15 debug device and the exact three synthetic events. Each event was expanded before acceptance.

| Event | Exact parameters observed | Result |
|---|---|---|
| `core_action_failed` | `action_name=dose_recorded`, `reason=server` | PASS |
| `patient_link_code_share_tapped` | `surface=patient_management` | PASS |
| `notification_permission_result` | `result=authorized`, `surface=notifications` | PASS |

No custom parameter contained a user/patient/caregiver identifier, name, email, medication, dosage, dose state/time/date, inventory quantity, notification content, linking code, token, URL or arbitrary/free text. No Firebase user ID was set.

## Processed report and temporary Explore

- Existing C80 evidence retains the processed Android application and safe Events observations.
- A temporary free-form Explore named `Android Phase1 fixed-enum QA 2026-08-23` was created under the Phase 1 authorization.
- The Explore uses only `イベント名` as the row, `イベント数` as the metric and `プラットフォーム` exactly matching `Android` as the filter.
- The 28-day processed window ending 2026-08-22 showed 38 Android events and fixed event-name rows, including `app_clear_data`, `app_mode_selected`, `caregiver_tab_viewed`, `patient_tab_viewed`, `screen_viewed`, `tutorial_started` and `tutorial_skipped`.
- Newly emitted parity events are accepted from DebugView rather than misreported as immediately processed Explore rows; aggregated reporting is asynchronous.
- Temporary Explore removal is intentionally pending the release owner's immediate deletion confirmation. Until it is removed, `RG-001` and `XP-004` remain open.

## Automated regression paired with this acceptance

- API: typecheck, ESLint, Next production build and 351/351 tests passed.
- Android: Staging/Production Debug/Release JVM tests, Staging Debug and Production Release lint/build passed.
- Compose UI: API 35 four-shard run passed 299/299 with zero failures, errors or skips.
- Release surfaces: APK/AAB content, min/target SDK, 16 KB alignment, credential/privacy policy, universal install surface and three representative device split surfaces passed.

