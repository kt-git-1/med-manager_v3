# C27 UI-205 caregiver inventory detail source calibration

- Date: 2026-07-15
- Branch: `android-dev`
- Android fixture: API 35, 1080 x 2400 display
- iOS source fixture: 1170 x 2532 display

## Source contract

This checkpoint calibrates UI-205 against the current production SwiftUI implementation in `InventoryDetailView.swift` using the same deterministic medication state: `夕食後のお薬`, one tablet once daily, remaining quantity 2, low inventory, and refill target 2026-07-18.

The shared contract is:

- a centered `在庫` navigation title and trailing `保存` action that is disabled until the inventory-enabled setting changes;
- an action-status bordered medication card with a 56-unit illustration, title/cadence hierarchy, compact status badge, divider, 52-unit remaining quantity, days remaining and refill date;
- a standalone inventory-enabled toggle card;
- one adjustment card containing 1/2/3-week and custom presets, a borderless refill quantity row and a 58-unit primary refill action;
- a separately titled correction card with a borderless absolute-quantity row and orange correction action;
- unchanged confirmation, server-first mutation, success dismissal and exact failed-operation retry behavior.

Android intentionally retains the leading back action because UI-205 is hosted as a navigated detail inside the Inventory tab. The iOS reference is presented as a sheet and uses swipe dismissal, so it has no corresponding back button.

## Reference provenance

The iOS image was rendered from the current production SwiftUI at detached commit `29a71a0` through a transient debug-only launch route in a temporary worktree. The route and fixture were not committed, and no source change was made to the iOS/main, staging or Android branches. The temporary worktree was removed after capture.

## Closed drift

- Replaced the Android-only top app-bar arrangement with centered title/trailing save semantics while preserving its required back action.
- Matched the illustration, title, cadence, status, divider, 52-unit quantity and refill-plan hierarchy.
- Removed the redundant in-card settings save action; save now follows the navigation contract.
- Rebuilt refill presets as compact capsules and moved custom quantity into the shared inline row.
- Split refill and absolute correction into the same section/card hierarchy as SwiftUI.
- Updated remaining-days and low/out status copy to current iOS (`あと%d日分`, `少`, `なし`).

## Evidence

| File | SHA-256 |
|---|---|
| `ios-ui-205-caregiver-inventory-detail-source-light.png` | `4be418777a36c5ff14d65e87c609d95a2a1b7aa0a5d54cfa61ffdce25da1fc15` |
| `android-ui-205-caregiver-inventory-detail-source-calibrated-light.png` | `98f02e6c176c41db2c54d0bc112b24ab91043d44929a9c91b2be0e69caa933ae` |
| `ui-205-light-side-by-side.png` | `e99a5b393811984cf2449b21d630b87fbeeee8e48ebd4bcfd909dc708cb64526` |

## Verification

- Current iOS Debug reference build: pass.
- `CaregiverInventoryScreenTest`: 10/10 on API 35.
- JVM tests, Debug assembly and Lint: pass.
- Full API-35 instrumentation suite: 184/184 pass, including 130% and light/dark 200% inventory-detail action reachability.

## Remaining release evidence

Matched PRN, inventory-disabled, loading/error/confirmation, dark-mode and large-text iOS/Android pairs remain. Physical-device TalkBack, keyboard/IME and destructive-operation confirmation verification also remain release gates.
