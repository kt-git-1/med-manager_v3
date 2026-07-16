# C53 UI-205 Caregiver Inventory Detail current-runtime evidence

**Captured:** 2026-07-16

**iOS reference:** `staging@2b7d1fe` production `InventoryDetailView` rendered with deterministic local scenarios

**Android baseline:** `android-dev@b59939e` plus the C53 change

**Devices:** iPhone 17e / iOS 26.5 simulator and Pixel-compatible API 35 emulator

## Acceptance matrix

| State | iOS raw | Android raw | Comparison |
|---|---|---|---|
| Scheduled detail top / light | `raw/ios-scheduled-top-light.png` | `raw/android-scheduled-top-light.png` | `side-by-side/scheduled-top-light.png` |
| Scheduled refill/correction / light | `raw/ios-scheduled-lower-light.png` | `raw/android-scheduled-lower-light.png` | `side-by-side/scheduled-lower-light.png` |
| PRN detail / light | `raw/ios-prn-light.png` | `raw/android-prn-light.png` | `side-by-side/prn-light.png` |
| Inventory tracking off / light | `raw/ios-disabled-light.png` | `raw/android-disabled-light.png` | `side-by-side/disabled-light.png` |
| Refill confirmation / light | `raw/ios-refill-confirm-light.png` | `raw/android-refill-confirm-light.png` | `side-by-side/refill-confirm-light.png` |
| Correction confirmation / light | `raw/ios-correction-confirm-light.png` | `raw/android-correction-confirm-light.png` | `side-by-side/correction-confirm-light.png` |
| Failed correction / light | `raw/ios-failure-light.png` | `raw/android-failure-light.png` | `side-by-side/failure-light.png` |
| Updating overlay / light | `raw/ios-updating-light.png` | `raw/android-updating-light.png` | `side-by-side/updating-light.png` |
| Scheduled detail / dark | `raw/ios-scheduled-dark.png` | `raw/android-scheduled-dark.png` | `side-by-side/scheduled-dark.png` |
| Scheduled detail / adaptive text | `raw/ios-scheduled-adaptive.png` | `raw/android-scheduled-adaptive.png` | `side-by-side/scheduled-adaptive.png` |

Every row also has a normalized pair under `normalized/` and a 50% alpha diagnostic under `overlay/`. Overlays are alignment aids, not pixel-equality claims across SwiftUI and Material rendering.

## Closed residuals

- Detail status is derived from the live tracking toggle and current quantity/day threshold, so available, low, out and unconfigured colors/copy cannot remain stale after an edit.
- Refill and correction actions follow the live toggle state. Preset selection has an explicit blue/white selected state, the custom option moves focus to its input, and refill confirmation uses the current `補充する` action copy.
- Failed mutations remain on the detail route, show the current warning card below the attempted operation and retry that exact operation.
- The blocking update surface now uses the production app illustration, neutral progress indicator and current update copy.
- PRN, tracking-off, confirmation, failure, updating, dark and true Android 200% reachability are protected by API-35 Compose tests.

## Accepted platform differences

- Android retains its leading Back action because this detail is navigated inside the caregiver tab; iOS presents the view with its native centered dismissal contract.
- Android uses Material icons, switches and `AlertDialog`; iOS uses SF Symbols, native toggles and `confirmationDialog`, so confirmation geometry and dimming are intentionally platform-native.
- Android keeps status/navigation-bar geometry and true 200% scroll reachability; iOS keeps native safe areas and caps Dynamic Type at `.xLarge`.
- Deterministic iOS capture hooks used only local fixtures and production components, made no API calls and were removed with the temporary worktree.

## Verification

- Focused `CaregiverInventoryScreenTest`: 25/25 on API 35.
- Full API-35, JVM, Debug assembly and Lint results are recorded in the C53 checkpoint documentation after the final regression run.
