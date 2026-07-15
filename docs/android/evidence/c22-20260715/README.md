# C22 UI-201 Caregiver Today Source-Calibrated Comparison — 2026-07-15

## Evidence

| Surface | File | Provenance |
|---|---|---|
| iOS app-derived light reference | [`ios-ui-201-caregiver-today-app-reference-light.png`](ios-ui-201-caregiver-today-app-reference-light.png) | Unmodified `api/public/screenshots/caregiver-today.png` from current `main`; SHA-256 `2cd7a1d0c3614fe6c25e787ee82c8d65a36ed6c608f655143d79e9873e51c6fc` |
| Android same-data light result | [`android-ui-201-caregiver-today-source-calibrated-light.png`](android-ui-201-caregiver-today-source-calibrated-light.png) | Production `CaregiverTodayContent` on API 35 with 田中 花子, 08:00/12:30/19:00 slots, two recorded slots and two pending noon medicines |
| Diagnostic comparison | [`side-by-side`](ui-201-light-side-by-side.png), [`50% overlay`](ui-201-light-overlay-50.png) | Both raw images normalized to 1080 x 2400 for alignment only |

The iOS asset is a real-app-derived marketing capture. The deterministic Android fixture reproduces its patient, next slot, two noon medicines and 2/3 progress while current `CaregiverTodayView.swift`, `CaregiverAvatar`, `CaregiverCard` and `CaregiverPrimaryButton` remain authoritative for copy and metrics. Raw captures remain authoritative because the diagnostic files rescale both images.

## Differences found and closed

- The stale Android header `%@さんを見守り中` and pale 56 dp avatar were replaced by the current `%@さん` / `今日の服薬` hierarchy and shared 62/50 dp white-ringed teal initial avatar.
- The screen now uses the iOS 20 dp horizontal, 16 dp top and 120 dp bottom layout rhythm.
- The next-action card now has the current 18 dp/1.5 dp accent card, 66 dp clock, 32 sp slot time, status pill, helper text, complete next-slot medication list and always-present 58 dp/16 dp-corner primary action.
- Recordability excludes out-of-stock medicines before opening the existing confirmation and caregiver slot-bulk route; a fully blocked next slot keeps the source-matched disabled action visible.
- The progress card now uses a 76 dp ring with nine-unit teal/16%-teal track, 24 sp ratio, current no-space `2/3回分 完了` format and next/missed/done summary.
- The PRN entry now follows the current card-level navigation hierarchy with a 62 dp icon and chevron instead of an inner Material button.
- Neutral cards and inset medicine rows use the shared semantic `cardStroke` token instead of Compose's purple-tinted default `outlineVariant`. C21 patient-settings fixtures were regenerated after the same token correction.

## Regression coverage

- `CaregiverTodayScreenTest.currentIosSourceCalibratedTodayFixtureUsesProductionHeroHierarchy`
- The complete `CaregiverTodayScreenTest` class passes 12/12 on API 35 and emits the Android raw fixture.
- `CaregiverTodayScreenTest.contentShowsNextActionProgressPrnAndTimelineAggregation` rejects the stale watching copy, verifies current progress/missed copy and protects the disabled inventory-blocked primary action.
- `CaregiverTodayScreenTest`, `CaregiverLargeTextUiTest` and `PatientSettingsContentTest` pass 28/28, retaining the caregiver dark/200% action path and the corrected patient-settings hierarchy.
- The complete API-35 instrumentation suite passes 181/181; JVM tests, Debug assembly and Lint also pass.

This closes the same-data typical-light UI-201 header/hero/progress pass without changing the iOS worktree. Timeline-row source calibration, matched loading/error/PRN/adaptive states, physical TalkBack and physical-device rendering remain in C01/C06/Gate I.
