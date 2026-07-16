# C54 UI-206 Caregiver History current-runtime evidence

**Captured:** 2026-07-16

**iOS reference:** `staging@2b7d1fe` production caregiver history/state components rendered with deterministic local scenarios

**Android baseline:** `android-dev@5f5b01a` plus the C54 change

**Devices:** iPhone 17e / iOS 26.5 simulator and Pixel-compatible API 35 emulator

## Acceptance matrix

| State | iOS raw | Android raw | Comparison |
|---|---|---|---|
| Patient list unavailable / light | `raw/ios-patient-error-light.png` | `raw/android-patient-error-light.png` | `side-by-side/patient-error-light.png` |
| No patient / light | `raw/ios-no-patient-light.png` | `raw/android-no-patient-light.png` | `side-by-side/no-patient-light.png` |
| Patient selection required / light | `raw/ios-selection-light.png` | `raw/android-selection-light.png` | `side-by-side/selection-light.png` |
| Month loading / light | `raw/ios-month-loading-light.png` | `raw/android-month-loading-light.png` | `side-by-side/month-loading-light.png` |
| Month unavailable / light | `raw/ios-month-error-light.png` | `raw/android-month-error-light.png` | `side-by-side/month-error-light.png` |
| Populated calendar top / light | `raw/ios-populated-top-light.png` | `raw/android-populated-top-light.png` | `side-by-side/populated-top-light.png` |
| Selected-day timeline / light | `raw/ios-populated-lower-light.png` | `raw/android-populated-lower-light.png` | `side-by-side/populated-lower-light.png` |
| Day loading / light | `raw/ios-day-loading-light.png` | `raw/android-day-loading-light.png` | `side-by-side/day-loading-light.png` |
| Day empty / light | `raw/ios-day-empty-light.png` | `raw/android-day-empty-light.png` | `side-by-side/day-empty-light.png` |
| Day unavailable / light | `raw/ios-day-error-light.png` | `raw/android-day-error-light.png` | `side-by-side/day-error-light.png` |
| Missed-dose backfill confirmation / light | `raw/ios-backfill-confirm-light.png` | `raw/android-backfill-confirm-light.png` | `side-by-side/backfill-confirm-light.png` |
| Blocking update / light | `raw/ios-updating-light.png` | `raw/android-updating-light.png` | `side-by-side/updating-light.png` |
| Populated calendar / dark | `raw/ios-populated-dark.png` | `raw/android-populated-dark.png` | `side-by-side/populated-dark.png` |
| Populated timeline / adaptive text | `raw/ios-populated-adaptive.png` | `raw/android-populated-adaptive.png` | `side-by-side/populated-adaptive.png` |

Every row also has a normalized pair under `normalized/` and a 50% alpha diagnostic under `overlay/`. Overlays are alignment aids, not pixel-equality claims across SwiftUI and Material rendering.

## Closed residuals

- Patient-list failure, no-patient and selection-required states now use the same current caregiver recovery/registration/settings hierarchy as iOS, with production Home callbacks.
- Month loading and initial failure preserve the caregiver avatar/title and centered displayed-month label instead of replacing the whole screen with an Android-only message.
- Month and day failures expose the full Retry plus return-to-login recovery contract. Day empty content uses the current unboxed centered hierarchy.
- Calendar/selected-day content retains the Monday-first grid, ordered status markers, timestamp-sorted scheduled/PRN timeline, recorder attribution, exact push highlight and confirmation-protected missed-dose backfill from C16/C28.
- The blocking update surface now uses the production app illustration and neutral progress hierarchy.
- Dark mode and true Android 200% timeline reachability are protected by API-35 Compose tests.

## Accepted platform differences

- Android uses Material icons and `AlertDialog`; iOS uses SF Symbols and native SwiftUI alerts, so icon and confirmation geometry remain platform-native.
- Android keeps status/navigation-bar geometry and true 200% scrolling; iOS keeps native safe areas and caps Dynamic Type at `.xLarge`.
- The same state may expose a slightly different vertical slice because SwiftUI and Compose calculate programmatic scroll anchors differently; the ordered calendar, summary and timeline remain reachable and contract-identical.
- Deterministic iOS capture hooks used only local fixtures and production components, made no API calls and were removed with the temporary worktree.

## Verification

- Focused `CaregiverHistoryScreenTest`: 17/17 on API 35.
- Full API-35 instrumentation: 249/249, 0 skipped/failed.
- JVM: 186/186, 0 skipped/failed.
- Debug assembly and Lint: passed.
