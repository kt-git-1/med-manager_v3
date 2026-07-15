# C44 UI-102 Current-iOS Dose Detail Matrix — 2026-07-16

## Scope and capture contract

This checkpoint closes the emulator-verifiable current-runtime UI-102 matrix: populated detail, empty memo, cache-miss loading, retryable failure, dark mode and largest-text behavior. The iOS references use the unchanged production `PatientTodayDoseDetailView` from `main@1cf8aef`; Android uses the production `PatientDoseDetailContent` from `android-dev`. Both sides use the same taken dose, medication snapshot, Tokyo timestamp, memo and per-intake quantity.

The private iOS production component was reached through a temporary DEBUG-only local preview route. That route was removed immediately after capture and `git diff -- ios` is empty. No production API, real account, patient identity, medication record, dose record or Analytics payload was used.

## Matrix

| State | Current iOS | Android result | Diagnostic comparison |
|---|---|---|---|
| Populated light | [`iOS`](ios-ui-102-patient-dose-detail-content-light.png) | [`Android`](android-ui-102-patient-dose-detail-content-light-matched.png) | [`side-by-side`](compare-ui-102-content-light-side-by-side.png), [`50% overlay`](compare-ui-102-content-light-overlay-50.png) |
| Empty memo light | [`iOS`](ios-ui-102-patient-dose-detail-empty-light.png) | [`Android`](android-ui-102-patient-dose-detail-empty-light-matched.png) | [`side-by-side`](compare-ui-102-empty-light-side-by-side.png), [`50% overlay`](compare-ui-102-empty-light-overlay-50.png) |
| Cache-miss loading | [`iOS`](ios-ui-102-patient-dose-detail-loading-light.png) | [`Android`](android-ui-102-patient-dose-detail-loading-light-matched.png) | [`side-by-side`](compare-ui-102-loading-light-side-by-side.png), [`50% overlay`](compare-ui-102-loading-light-overlay-50.png) |
| Retryable failure | [`iOS`](ios-ui-102-patient-dose-detail-error-light.png) | [`Android`](android-ui-102-patient-dose-detail-error-light-matched.png) | [`side-by-side`](compare-ui-102-error-light-side-by-side.png), [`50% overlay`](compare-ui-102-error-light-overlay-50.png) |
| Populated dark | [`iOS`](ios-ui-102-patient-dose-detail-content-dark.png) | [`Android`](android-ui-102-patient-dose-detail-content-dark-matched.png) | [`side-by-side`](compare-ui-102-content-dark-side-by-side.png), [`50% overlay`](compare-ui-102-content-dark-overlay-50.png) |
| Largest text | [`iOS Accessibility XXXL`](ios-ui-102-patient-dose-detail-content-accessibility-xxxl.png) | [`Android 200%`](android-ui-102-patient-dose-detail-content-font-2.0-matched.png) | [`side-by-side`](compare-ui-102-content-adaptive-side-by-side.png), [`50% overlay`](compare-ui-102-content-adaptive-overlay-50.png) |

Diagnostic files resize raw captures to 1080 x 2400 for alignment only. Raw PNGs remain authoritative. Platform status/navigation glyphs and OS clock values are excluded from app-parity judgment.

## Differences found and closed

1. Android now uses the same schedule description (`朝食後`) and exact three-card hierarchy as current iOS, with no stale strength or inventory cards.
2. Detail cards now use the current 16-unit rounded/elevated treatment instead of outlined Material cards. Header date/status typography, vertical rhythm, memo heading and inset field were recalibrated from the current SwiftUI capture.
3. The taken-status capsule now uses the current iOS green treatment instead of the unrelated patient teal.
4. The loading state now uses the neutral progress tint, calibrated 172-unit blocking card, rounded elevation and the exact `読み込み中...` hierarchy over preserved detail content.
5. The retryable failure now uses an inset elevated error card and a leading iOS-blue `再試行` action instead of a full-width outlined card with a centered teal action.
6. The Android 200% surface keeps all canonical content scroll-reachable. Current iOS caps the app root at `.xLarge`, so its OS Accessibility XXXL capture intentionally remains at the capped product scale; Android retains the more permissive 200% behavior.

## Verification

- `PatientTodayContentTest`: 24/24 on API 35, including all six UI-102 states and retry invocation.
- Full API 35 instrumentation: 206/206, zero skipped and zero failed.
- JVM: 185/185, zero skipped and zero failed.
- `assembleDebug`: passed.
- `lintDebug`: passed.
- iOS working-tree diff after capture-route removal: empty.

Physical sheet presentation, TalkBack traversal and OEM rendering/lifecycle remain Gate I requirements.
