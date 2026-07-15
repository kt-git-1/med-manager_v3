# C45 UI-103 Current-iOS Patient PRN Matrix — 2026-07-16

## Baseline and environment

- iOS/API baseline: `main@1cf8aef`
- Android baseline: `android-dev@035f901` before this parity repair
- iOS: disposable iPhone 17e simulator, iOS 26.5, current production `PrnMedicationListView`
- Android: `MedicationApp_API_35`, 1080 x 2400, 420 dpi, production `TodayContent` -> `PatientPrnScreen`
- Locale/theme data: Japanese, identical `頭痛薬 1錠` / `1回1錠` / `痛い時` fixture
- Comparison diagnostics: both raw captures normalized to 1080 x 2400, then rendered side by side and at 50% opacity

The simulator-only iOS capture route decoded synthetic local data and rendered the unchanged production PRN list/card, scheduling overlay and toast components. It performed no API request and was fully removed after capture; the final iOS working-tree diff is empty.

## Evidence

| State | Raw iOS | Raw Android | Side by side | 50% overlay |
|---|---|---|---|---|
| List, light | [`ios`](ios-ui-103-prn-list-light.png) | [`android`](android-ui-103-prn-list-light-matched.png) | [`compare`](compare-ui-103-prn-list-light-side-by-side.png) | [`overlay`](compare-ui-103-prn-list-light-overlay-50.png) |
| Blocking update, light | [`ios`](ios-ui-103-prn-loading-light.png) | [`android`](android-ui-103-prn-loading-light-matched.png) | [`compare`](compare-ui-103-prn-loading-light-side-by-side.png) | [`overlay`](compare-ui-103-prn-loading-light-overlay-50.png) |
| Failed record feedback, light | [`ios`](ios-ui-103-prn-error-light.png) | [`android`](android-ui-103-prn-error-light-matched.png) | [`compare`](compare-ui-103-prn-error-light-side-by-side.png) | [`overlay`](compare-ui-103-prn-error-light-overlay-50.png) |
| Insufficient inventory, light | [`ios`](ios-ui-103-prn-insufficient-light.png) | [`android`](android-ui-103-prn-insufficient-light-matched.png) | [`compare`](compare-ui-103-prn-insufficient-light-side-by-side.png) | [`overlay`](compare-ui-103-prn-insufficient-light-overlay-50.png) |
| List, dark | [`ios`](ios-ui-103-prn-list-dark.png) | [`android`](android-ui-103-prn-list-dark-matched.png) | [`compare`](compare-ui-103-prn-list-dark-side-by-side.png) | [`overlay`](compare-ui-103-prn-list-dark-overlay-50.png) |
| Largest text | [`ios`](ios-ui-103-prn-list-accessibility-xxxl.png) | [`android`](android-ui-103-prn-list-font-2.0-matched.png) | [`compare`](compare-ui-103-prn-list-adaptive-side-by-side.png) | [`overlay`](compare-ui-103-prn-list-adaptive-overlay-50.png) |

## Review disposition

1. Direct runtime capture proved current iOS pushes PRN into a full navigation surface rather than presenting a bottom sheet. Android now replaces the old 85%-height Material sheet with a full in-tab route, explicit back action and system-back handling.
2. The screen title, 28-unit instruction, 20-unit centered navigation title, 20-unit card inset, orange outline, 50-unit medication symbol, 28/20/17-unit medicine hierarchy and 72-unit teal action follow the production SwiftUI metrics.
3. The generic medical-cross icon was replaced with the shared capsule-and-tablet symbol used by current iOS.
4. Blocking submission now uses the same app illustration, neutral progress, dim layer and elevated rounded card as the current scheduling overlay; pointer input is consumed until completion.
5. Failed submission remains visible without dismissing the PRN route and now uses the current top error capsule hierarchy. Successful submission remains the only path that advances the monotonic revision and closes the route.
6. Insufficient inventory retains the red badge and teal action at 55% whole-control opacity while remaining semantically disabled. Dark mode preserves the current surface/orange/teal hierarchy.
7. Current iOS caps the app root at `.xLarge` even under OS Accessibility XXXL. Android intentionally keeps the complete card/action scroll-reachable at 200% instead of copying that cap.
8. The Android back affordance is present in evidence because this is a real navigation destination. The simulator-only iOS reference starts the same production destination at the root, so it has no synthetic back item.

## Verification

- `PatientTodayContentTest`: 25/25 on API 35, including full-route back/feedback clearing, all six UI-103 states, insufficient callback lock, 200% reachability and success-only dismissal.
- Full API 35 instrumentation: 207/207, 0 skipped and 0 failed.
- JVM: 185/185, 0 skipped and 0 failed.
- `assembleDebug` and `lintDebug`: passed.
- iOS working-tree diff after capture-route removal: empty.

Physical TalkBack traversal, real PRN API submission, OEM rendering/lifecycle and haptics remain Gate I requirements.
