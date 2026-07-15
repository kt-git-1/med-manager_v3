# C42 UI-101 Current-iOS Initial Loading and Failure — 2026-07-16

## Scope and capture contract

This checkpoint closes the current-runtime UI-101 initial-loading and initial-failure light pair. The iOS reference is the unchanged Debug app from `main@1cf8aef`, launched through its existing `UITEST_SESSION_BOOTSTRAP` path with a synthetic patient token and an unavailable local API endpoint. The loading image was taken before the synthetic request completed; the failure image was taken after its transport timeout. No production API, real account, patient identity, medication, dose record or analytics payload was used, and no iOS source was changed.

Android renders the production `PatientHomeScreen` and `TodayContent` on the API 35 1080 x 2400 emulator. The test data source either suspends its synthetic medication request or fails it locally. This means both the state content and the persistent patient shell are exercised instead of capturing a detached component crop.

## Matrix

| State | Current iOS | Android result | Diagnostic comparison |
|---|---|---|---|
| Initial loading | [`ios-ui-101-patient-initial-loading-light.png`](ios-ui-101-patient-initial-loading-light.png) | [`android-ui-101-patient-initial-loading-light-matched.png`](android-ui-101-patient-initial-loading-light-matched.png) | [`side-by-side`](ui-101-initial-loading-side-by-side.png), [`50% overlay`](ui-101-initial-loading-overlay-50.png) |
| Initial failure | [`ios-ui-101-patient-initial-error-light.png`](ios-ui-101-patient-initial-error-light.png) | [`android-ui-101-patient-initial-error-light-matched.png`](android-ui-101-patient-initial-error-light-matched.png) | [`side-by-side`](ui-101-initial-error-side-by-side.png), [`50% overlay`](ui-101-initial-error-overlay-50.png) |

Diagnostic images normalize both raw captures to 1080 x 2400 for alignment only. Raw PNGs remain authoritative; platform status/navigation-bar glyphs and the taller Android test device are not treated as app defects.

## Differences found and closed

1. Android used a teal 52 dp Material progress indicator while current iOS uses a compact neutral large `ProgressView`. Android now uses a 38 dp neutral indicator and retains the exact `読み込み中...` hierarchy.
2. Android's initial failure was an uncontained icon/text column. It now matches the iOS 18-unit full-width glass-card family with 16-unit outer inset, 22-unit inner padding, semantic red warning hierarchy and patient-card shadow.
3. The full-shell capture exposed a shared patient-navigation drift: Android still used the full-width Material navigation bar, reverse-history icon and small labels. Preview and production now share the iOS-style floating 28-unit card, 14-unit outer inset, 12/10-unit inner padding, 74-unit tab targets, 30-unit calendar/clock/gear icons and bold selected treatment.
4. The loading/failure regressions now capture the real patient shell and assert the Today tab remains visible while the Today content header is absent.

## Verification

- `PatientTodayContentTest`: 23/23 on API 35, including production-shell loading/failure captures.
- Full API 35 instrumentation: 205/205, zero skipped and zero failed.
- JVM: 185/185, zero failed.
- `assembleDebug`: passed.
- `lintDebug`: passed.

This closes the matched UI-101 initial-loading and initial-failure light states and the shared patient bottom-navigation visual drift. Inventory-partial, updating, long-name and notification-target matched iOS pairs plus physical TalkBack/device verification remain in C01/C06/Gate I.
