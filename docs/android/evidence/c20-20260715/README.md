# C20 UI-104 Patient History Source-Calibrated Comparison — 2026-07-15

> Historical calibration note: C46 provides the deterministic current-runtime UI-104 matrix and supersedes C20 for zero-plan copy, loading/failure treatment, retention behavior, dark mode and largest-text conclusions. C20 remains the provenance record for the populated progress/week/recent metric calibration.

## Evidence

| Surface | File | Provenance |
|---|---|---|
| iOS app-derived light reference | [`ios-ui-104-patient-history-app-reference-light.png`](ios-ui-104-patient-history-app-reference-light.png) | Unmodified `api/public/screenshots/patient-history-demo.png` from current `main`; SHA-256 `3f4e48345acbe31cbc36a35a89da30f7de764b7875b0229bb5bf5cbfde1d6abd` |
| Android light result | [`android-ui-104-patient-history-source-calibrated-light.png`](android-ui-104-patient-history-source-calibrated-light.png) | Production `HistoryContent` on API 35 with a deterministic 2026-06-11 fixture |
| Diagnostic comparison | [`side-by-side`](ui-104-light-side-by-side.png), [`50% overlay`](ui-104-light-overlay-50.png) | Both raw images normalized to 1080 x 2400 for alignment only |

The iOS image is a real-app-derived marketing capture and is useful for visual calibration, but it is not a deterministic production-state contract. Its hard-coded sample data and older visible status wording are therefore not copied blindly. Current `HistoryMonthView.swift`, `PatientReadOnlyView.swift` design-system components and localization are authoritative for metrics and copy. The raw captures remain authoritative because the diagnostic images rescale the iOS reference.

## Differences found and closed

- Progress and week cards now use the current `PatientCard` family: 18-unit corners, 18-unit content padding and 1.5-unit accent strokes instead of the former 24 dp/2 dp treatment.
- The progress ring ratio uses the iOS 24-unit type; the week total uses 50-unit type; the week status uses title-sized type; and week cells use eight-unit spacing.
- Taken, remaining and missed pills use the current 10 x 6 padding, six-unit internal spacing and matching status icons. Their container can wrap vertically like SwiftUI `ViewThatFits` when width is constrained.
- Recent cards use 18-unit corners and title-sized dates.
- The progress pill now uses the current shared copy `記録済み %d回分`; the stale Android-only `服用済み %d回分` is rejected by instrumentation coverage.

The shared 62 dp teal header icon was already corrected under C19 and is visible in this UI-104 result.

## Regression coverage

- `PatientHistoryContentTest.currentIosSourceCalibratedHistoryFixtureUsesCurrentMetricsAndCopy`
- The complete `PatientHistoryContentTest` class passes 12/12 on API 35 and emits the Android raw fixture.
- The complete API-35 instrumentation suite passes 179/179; JVM tests, Debug assembly and Lint also pass.
- The test asserts `1/3回分 記録済み`, `記録済み 1回分`, `3/7日`, and rejects `服用済み 1回分`.

This closes the source-calibrated typical light UI-104 pass. A deterministic same-data iOS route was not added because iOS work remains isolated in its own worktree/thread. Matched iOS dark/large-text exceptional states, physical TalkBack and physical-device rendering remain in C01/C06/Gate I.
