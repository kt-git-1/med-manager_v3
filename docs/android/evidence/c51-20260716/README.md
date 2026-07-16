# C51 UI-203 Caregiver Medication Form current-runtime evidence

**Captured:** 2026-07-16

**iOS reference:** `staging@2b7d1fe` production `MedicationFormView` rendered with deterministic local scenarios

**Android baseline:** `android-dev@8696015` plus the C51 change

**Devices:** iPhone 17e / iOS 26.5 simulator and Pixel-compatible API 35 emulator

## Acceptance matrix

| State | iOS raw | Android raw | Comparison |
|---|---|---|---|
| Add / basic information / light | `raw/ios-add-basic-light.png` | `raw/android-add-basic-light.png` | `side-by-side/add-basic-light.png` |
| Add / schedule / light | `raw/ios-add-schedule-light.png` | `raw/android-add-schedule-light.png` | `side-by-side/add-schedule-light.png` |
| PRN / light | `raw/ios-prn-light.png` | `raw/android-prn-light.png` | `side-by-side/prn-light.png` |
| Weekday schedule / light | `raw/ios-weekly-light.png` | `raw/android-weekly-light.png` | `side-by-side/weekly-light.png` |
| Edit / basic / light | `raw/ios-edit-basic-light.png` | `raw/android-edit-basic-light.png` | `side-by-side/edit-basic-light.png` |
| Edit / lower actions / light | `raw/ios-edit-lower-light.png` | `raw/android-edit-lower-light.png` | `side-by-side/edit-lower-light.png` |
| Delete confirmation / light | `raw/ios-delete-confirm-light.png` | `raw/android-delete-confirm-light.png` | `side-by-side/delete-confirm-light.png` |
| Aggregate validation / light | `raw/ios-validation-light.png` | `raw/android-validation-light.png` | `side-by-side/validation-light.png` |
| Add / dark | `raw/ios-add-basic-dark.png` | `raw/android-add-basic-dark.png` | `side-by-side/add-basic-dark.png` |
| Adaptive text | `raw/ios-add-adaptive.png` | `raw/android-add-adaptive.png` | `side-by-side/add-adaptive.png` |

Every row also has a normalized pair under `normalized/` and a 50% alpha diagnostic under `overlay/`. Overlays are alignment aids, not pixel-equality claims across UIKit and Material rendering.

## Closed residuals

- Period precedes schedule and uses the current start-date plus optional end-date hierarchy.
- Scheduled medicine exposes the guidance card, daily/weekday segment, seven equal weekday choices and the 2 x 2 morning/noon/night/bedtime slot grid with current preset times.
- PRN help and hero copy match the current iOS contract; scheduled-only controls disappear for PRN.
- Initial inventory appears only during creation; edit retains notes, save and confirmation-protected destructive delete without showing initial inventory.
- The validation result uses the current large centered warning hierarchy and exact common messages.
- Save uses the current blue primary treatment, delete uses the pale destructive treatment, and edit uses the pencil identity.
- Dark mode and true Android 200% font reachability are protected by instrumentation.

## Accepted platform differences

- Android keeps an explicit in-content Cancel/title bar; iOS uses its native navigation presentation.
- Date controls, switches, symbols and confirmation dialogs retain native platform geometry.
- The iOS app caps Dynamic Type at `.xLarge`; the Android adaptive reference intentionally proves uncapped 200% font-scale scrolling.
- Deterministic iOS capture hooks used only local fixtures and production components, performed no API calls, and were removed with the temporary worktree.

## Verification

- Focused `CaregiverMedicationScreenTest`: 24/24 on API 35.
- Full API-35, JVM, Debug assembly and Lint results are recorded in the C51 checkpoint documentation after the final regression run.
