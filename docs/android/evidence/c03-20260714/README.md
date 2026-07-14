# C03 Patient Today evidence — 2026-07-14

Baseline: current iOS UI-101 capture at `../c01-20260714/ui-101-patient-today-light.png`.

| Evidence | Purpose | Result |
|---|---|---|
| `android-ui-101-patient-today-light.png` | Android deterministic production preview | Matches current iOS hierarchy, sample date/time, next-dose hero, medication rows, primary action, PRN entry and bottom navigation |
| `android-ui-101b-patient-prn-sheet-light.png` | PRN entry interaction | Scrollable modal list exposes instructions and the record action while preserving Today context |

Automated verification:

- `PatientTodayContentTest` verifies next/PRN/planned vertical order, eligible bulk action, partial inventory warning, PRN sheet/action and dose details.
- Existing repository and contract tests retain recording-window, optimistic/authoritative refresh, partial success, inventory and patient-scoped PRN guarantees.
- Full gate: `./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest` — success, 34/34 API-35 instrumentation tests.

Residual C06/V1 evidence: dark mode, font 1.3/2.0, TalkBack, compact/large phones and physical-device notification/recording behavior.
