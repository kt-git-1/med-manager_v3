# C01 Current-iOS Reference Capture Evidence — 2026-07-14

## Baseline and environment

- iOS/API source baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android work branch: `android-dev@1b38208c7178b35c49a33548b066eb0fce67a367` before the current uncommitted slice
- Rebaseline check: `git diff android-dev..main -- ios api package.json` returned no runtime differences.
- iOS build: `MedicationApp`, Debug, iOS Simulator, `BUILD SUCCEEDED`
- iOS device: iPhone 17 Pro, iOS 26.5, 1206 x 2622 pixels
- Android device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, font scale 1.0
- Locale/time zone: Japanese / `ja_JP`, `Asia/Tokyo`
- Appearance: light
- Analytics: collection disabled. The consent prompt was captured before recording the explicit declined state; later launches used `-disableAnalytics`.

## Stable captures

| Contract | State | Evidence | Deterministic setup |
|---|---|---|---|
| UI-001 | First-run analytics consent | [`c01-20260714/ui-001-analytics-consent-light.png`](c01-20260714/ui-001-analytics-consent-light.png) | Fresh install; no consent decision |
| UI-001 | Mode select default | [`c01-20260714/ui-001-mode-select-light.png`](c01-20260714/ui-001-mode-select-light.png) | Consent decided=false for collection; no selected mode/session |
| UI-001 | Android comparison after safe-area repair | [`c01-20260714/android-ui-001-mode-select-light.png`](c01-20260714/android-ui-001-mode-select-light.png) | Fresh Android app data, production `ModeSelectScreen` |
| UI-001 | Mode select dark | [`c01-20260714/ui-001-mode-select-dark.png`](c01-20260714/ui-001-mode-select-dark.png) | iOS system dark appearance, default content size |
| UI-001 | Android mode select dark | [`c01-20260714/android-ui-001-mode-select-dark.png`](c01-20260714/android-ui-001-mode-select-dark.png) | Android system dark appearance, production theme |
| UI-001 | iOS largest accessibility content size | [`c01-20260714/ui-001-mode-select-large-text.png`](c01-20260714/ui-001-mode-select-large-text.png) | `accessibility-extra-extra-extra-large`; this source screen's fixed-size text remains visually fixed |
| UI-001 | Android font scale 2.0, initial viewport | [`c01-20260714/android-ui-001-mode-select-font-2.0.png`](c01-20260714/android-ui-001-mode-select-font-2.0.png) | Android `font_scale=2.0`; production scroll container |
| UI-001 | Android font scale 2.0, family action reached | [`c01-20260714/android-ui-001-mode-select-font-2.0-scrolled.png`](c01-20260714/android-ui-001-mode-select-font-2.0-scrolled.png) | Same state after scrolling; family primary action is fully reachable |
| UI-100 | Patient Today tutorial step 1/4 | [`c01-20260714/ui-100-patient-tutorial-today-light.png`](c01-20260714/ui-100-patient-tutorial-today-light.png) | `-ForceModeTutorial.patient`; production tutorial overlay and sample view |
| UI-101 | Patient Today typical content | [`c01-20260714/ui-101-patient-today-light.png`](c01-20260714/ui-101-patient-today-light.png) | `-ForceModeTutorial.patient -PatientMarketingScreenshot.today`; production shell with deterministic sample data and no overlay |

## Review disposition

- The first Android UI-001 capture exposed a real edge-to-edge defect: its content began inside the status-bar inset while iOS applies 52 points after the safe area. `ModeSelectScreen` now applies `safeDrawingPadding()` before its pinned 52 dp top padding.
- Dark comparison exposed black Android content on teal role-action circles while iOS keeps white content. The dark `onPrimary` token now follows the iOS white-on-teal contract; the repaired dark capture confirms both actions.
- The repaired Android capture matches the iOS section order, safe-area origin, 22-unit horizontal rhythm, 24-unit section rhythm, card structure, role imagery, semantic teal/orange treatment and action placement.
- At Android font scale 2.0 both cards reflow, remain scrollable and expose their primary actions. `ModeSelectScreenTest.familyActionRemainsReachableAtTwoHundredPercentFontScale` protects this behavior.
- Remaining UI-001 differences are platform typography/icon rendering and viewport-class height. Android analytics-consent UI, compact/large matched captures and physical verification remain required before UI-001 can be marked `VERIFIED`.
- UI-100 and UI-101 use the iOS production component tree and deterministic in-app sample data; no API response, identity, medication record or auth token from a real user was captured.

## Remaining C01 reference work

- UI-001 compact/large viewport and Android analytics consent; patient link and caregiver auth entry states.
- UI-100 tutorial steps 2–4, completion/skip and 200% text.
- UI-101 exceptional Today states plus UI-102 dose detail and UI-103 PRN states.
- UI-104 History month, UI-105 History day and UI-106 Settings states.
- Compact/large viewport pairs and dark/large-text variants required by `ui-screen-contracts.md`.

C01 remains in progress until the full scoped state inventory is captured. These files are the first current-baseline references and replace older evidence only for the states listed above.

Post-fix verification: `./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest` completed successfully with 32/32 API 35 emulator tests, and `git diff --check` passed.
