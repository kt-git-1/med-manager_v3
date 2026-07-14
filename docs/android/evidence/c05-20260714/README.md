# C05 Patient Settings evidence — 2026-07-14

Current source baseline: `PatientSettingsView` in `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift` and current localization strings.

| Evidence | Purpose | Result |
|---|---|---|
| `android-ui-106-patient-settings-light.png` | Production Settings preview | Current header, notification master, linked status, off-by-default privacy analytics card, explained legal/support rows and selected Settings tab |

Automated verification covers the analytics consent callback, notification-denied state, canonical privacy/terms/support URLs, server-first logout confirmation, notification settings persistence/schedule rebuild, tutorial actions and patient notification routing.

Full gate: `./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest` — success, 37/37 API-35 instrumentation tests.

Gate H remains authoritative for Firebase configuration, safe fixed-event wrapper, reset on disable, preview/test suppression and DebugView/Realtime/Events/Explore verification.
