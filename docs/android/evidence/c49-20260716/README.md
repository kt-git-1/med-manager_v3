# C49 UI-201 Caregiver Today exceptional-state parity

**iOS reference:** detached capture from current shipping source `staging@2b7d1fe`

**Android baseline:** `android-dev@733625a`

**Devices:** iPhone 17e simulator on iOS 26.5; `MedicationApp_API_35` at 1080 x 2400

**Capture date:** 2026-07-16

C49 closes the remaining emulator-verifiable UI-201 matrix. The production Android screen now carries the current iOS three-step empty onboarding, blocking initial-load recovery with Retry and return-to-login actions, and a full navigation PRN surface instead of the former bottom sheet. PRN cards use the shared orange capsule symbol, teal proxy-record action, exact dosage/count/instruction copy, success-only dismissal and a route-local retry error. Back clears that transient error before returning to Today.

| State | Comparison | Acceptance |
|---|---|---|
| Initial loading, light | [side by side](compare-ui-201-caregiver-today-loading-light-side-by-side.png) | Neutral progress and exact `読み込み中...` copy |
| Initial failure, light | [side by side](compare-ui-201-caregiver-today-error-light-side-by-side.png) | Wi-Fi failure hierarchy, Retry and return-to-login remain reachable |
| Empty onboarding, light | [side by side](compare-ui-201-caregiver-today-empty-light-side-by-side.png) | Exact title/body, three numbered steps and medicine action |
| PRN list, light | [side by side](compare-ui-201-caregiver-today-prn-light-side-by-side.png) | Full route, exact medication content and teal proxy action |
| PRN confirmation, light | [side by side](compare-ui-201-caregiver-today-confirm-light-side-by-side.png) | Exact title/body/cancel/confirm contract; native platform alert placement retained |
| Empty onboarding, dark | [side by side](compare-ui-201-caregiver-today-empty-dark-side-by-side.png) | Semantic surfaces, border, icons and action remain legible |
| PRN list, dark | [side by side](compare-ui-201-caregiver-today-prn-dark-side-by-side.png) | Full list hierarchy and controls remain legible |
| Empty onboarding, adaptive | [side by side](compare-ui-201-caregiver-today-empty-adaptive-side-by-side.png) | iOS root cap at `.xLarge`; Android remains scroll-reachable at true 200% font scale |
| PRN list, adaptive | [side by side](compare-ui-201-caregiver-today-prn-adaptive-side-by-side.png) | Primary heading, medicine content and action remain reachable at 200% |

Each comparison also has a `*-overlay-50.png` image plus normalized and raw platform captures in this directory. The Android PRN route intentionally retains an explicit Material back action; the temporary iOS capture root had no navigation stack chrome. Alert geometry is also platform-native. Neither difference changes the product contract.

The iOS fixture used synthetic local data and never called an API. Its temporary root routing and preview initializer existed only in a detached `/tmp` worktree and were removed after capture; the parallel iOS worktree was not modified.

Verification after the production changes: focused UI-201 instrumentation 19/19, full API-35 instrumentation 215/215, JVM 186/186, Debug assembly and Lint all pass with zero failures or skips.
