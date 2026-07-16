# C52 UI-204 Caregiver Inventory List current-runtime evidence

**Captured:** 2026-07-16

**iOS reference:** `staging@2b7d1fe` production `InventoryListView` rendered with deterministic local scenarios

**Android baseline:** `android-dev@f1e16e8` plus the C52 change

**Devices:** iPhone 17e / iOS 26.5 simulator and Pixel-compatible API 35 emulator

## Acceptance matrix

| State | iOS raw | Android raw | Comparison |
|---|---|---|---|
| Loading / light | `raw/ios-loading-light.png` | `raw/android-loading-light.png` | `side-by-side/loading-light.png` |
| Data unavailable / light | `raw/ios-error-light.png` | `raw/android-error-light.png` | `side-by-side/error-light.png` |
| No patient / light | `raw/ios-no-patient-light.png` | `raw/android-no-patient-light.png` | `side-by-side/no-patient-light.png` |
| Patient selection required / light | `raw/ios-selection-light.png` | `raw/android-selection-light.png` | `side-by-side/selection-light.png` |
| Inventory empty / light | `raw/ios-empty-light.png` | `raw/android-empty-light.png` | `side-by-side/empty-light.png` |
| Populated / light | `raw/ios-populated-light.png` | `raw/android-populated-light.png` | `side-by-side/populated-light.png` |
| Out-of-stock filter empty / light | `raw/ios-filter-empty-light.png` | `raw/android-filter-empty-light.png` | `side-by-side/filter-empty-light.png` |
| Period-ended and unconfigured lower content / light | `raw/ios-lower-light.png` | `raw/android-lower-light.png` | `side-by-side/lower-light.png` |
| Inventory empty / dark | `raw/ios-empty-dark.png` | `raw/android-empty-dark.png` | `side-by-side/empty-dark.png` |
| Populated / dark | `raw/ios-populated-dark.png` | `raw/android-populated-dark.png` | `side-by-side/populated-dark.png` |
| Inventory empty / adaptive text | `raw/ios-empty-adaptive.png` | `raw/android-empty-adaptive.png` | `side-by-side/empty-adaptive.png` |
| Populated / adaptive text | `raw/ios-populated-adaptive.png` | `raw/android-populated-adaptive.png` | `side-by-side/populated-adaptive.png` |

Every row also has a normalized pair under `normalized/` and a 50% alpha diagnostic under `overlay/`. Overlays are alignment aids, not pixel-equality claims across SwiftUI and Material rendering.

## Closed residuals

- Patient loading, patient-list failure, no-patient and selection-required states now use the same current caregiver recovery hierarchy as iOS, including retry, login-return, patient-create and settings routing.
- The empty inventory card now matches the current medication illustration, three color-coded onboarding rows and large primary medication action.
- Summary metrics, refill guidance, filters, inventory sections, period-ended and unconfigured cards retain the current iOS information order.
- The out-of-stock filter includes every inventory-enabled zero-stock item even when its medication period has ended, matching the current iOS predicate.
- Dark mode and true Android 200% font reachability are protected for both empty and populated content.

## Accepted platform differences

- Android uses Material icons and controls while iOS uses SF Symbols and native glass treatment.
- Android keeps its status/navigation bar geometry and uncapped scrolling; iOS keeps native safe areas and caps Dynamic Type at `.xLarge`.
- Deterministic iOS capture hooks used only local fixtures and production components, performed no API calls, and were removed with the temporary worktree.

## Verification

- Focused `CaregiverInventoryScreenTest`: 18/18 on API 35.
- Full API-35, JVM, Debug assembly and Lint results are recorded in the C52 checkpoint documentation after the final regression run.
