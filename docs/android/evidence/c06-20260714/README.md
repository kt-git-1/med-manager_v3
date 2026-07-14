# C06 Patient Adaptive UI Evidence — 2026-07-14

## Environment

- Branch: `android-dev`
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi
- Locale/time zone: Japanese / `ja_JP`, `Asia/Tokyo`
- Production Compose screens with deterministic preview arguments; no real identity, medication, dose or authentication data

## Captures

| Contract | Variant | Evidence |
|---|---|---|
| UI-101 Patient Today | Dark, font scale 1.0 | [`android-ui-101-patient-today-dark.png`](android-ui-101-patient-today-dark.png) |
| UI-104 Patient History | Dark, font scale 1.0 | [`android-ui-104-patient-history-dark.png`](android-ui-104-patient-history-dark.png) |
| UI-106 Patient Settings | Dark, font scale 1.0 | [`android-ui-106-patient-settings-dark.png`](android-ui-106-patient-settings-dark.png) |
| UI-101 Patient Today | Light, font scale 2.0 initial viewport | [`android-ui-101-patient-today-font-2.0.png`](android-ui-101-patient-today-font-2.0.png) |

The 200% capture records the initial viewport only. Automated scrolling assertions are the acceptance evidence that content below that viewport remains reachable.

## Automated acceptance

`PatientAdaptiveUiTest` launches production Patient Today, History and Settings at font scale 2.0 and verifies these lower-screen targets can be reached through the real lazy lists:

- Today primary bulk action and planned section
- History recent-record section and today's week row
- Settings analytics toggle and logout action

`PatientAccessibilityTest` verifies each History week cell exposes one localized weekday/date/status announcement, for example `月 7月13日 忘れ`, alongside the existing heading and summary semantics.

The targeted adaptive/accessibility run passed 5/5 tests. The complete command below subsequently passed with 40/40 API-35 instrumentation tests, all JVM tests, Debug/Release assembly and Android Lint:

```sh
./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest
```

## Residual before C06 verification

C06 remains open. It still requires font scale 1.3 coverage, full TalkBack traversal on every patient state, remaining dark/large-text state captures, matched iOS variants, compact/large device classes and physical-device verification. Those residuals are intentionally not inferred from emulator automation.
