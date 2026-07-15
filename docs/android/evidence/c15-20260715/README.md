# C15 UI-105 Retained History Day Contract — 2026-07-15

## Baseline and environment

- iOS/API baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android baseline: `android-dev@2b161e5` before this parity repair
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, Japanese
- Surface: production `HistoryDayDetailContent`
- Reachability: historical component harness and shared foundation for caregiver History; C47 confirms it is not a current patient destination

## Evidence

| Required state | Evidence |
|---|---|
| Scheduled and PRN timeline | [`android-ui-105-patient-history-day-content-light.png`](android-ui-105-patient-history-day-content-light.png) |
| Empty day | [`android-ui-105-patient-history-day-empty-light.png`](android-ui-105-patient-history-day-empty-light.png) |
| Initial loading | [`android-ui-105-patient-history-day-loading-light.png`](android-ui-105-patient-history-day-loading-light.png) |
| Retryable failure | [`android-ui-105-patient-history-day-error-light.png`](android-ui-105-patient-history-day-error-light.png) |
| Retention lock | [`android-ui-105-patient-history-day-retention-light.png`](android-ui-105-patient-history-day-retention-light.png) |

## Source comparison and repairs

- Android previously grouped scheduled medicines and PRN records into separate sections. Current iOS sorts both record kinds into one timeline by timestamp and name. Android now uses the same ordering.
- The scheduled row now follows the patient-style iOS hierarchy: `HH:mm`, colored slot badge, medication plus dosage, full recorder label and status capsule. The PRN row uses time, `頓服: 薬名`, full recorder label and indigo PRN badge.
- Date formatting now uses `M月d日 (E)`. Empty copy is the current iOS `予定がありません` / `この日の服用予定はありません` pair.
- Loading now includes exact `読み込み中...`; a detail failure hides raw internal text and uses exact `読み込みに失敗しました。再試行してください。`. Shared `common.retry` was corrected from the stale Android `もう一度試す` to the current iOS `再試行`.
- Day-detail error and retention metadata are now separate from the History-list fields. A failed/retention-limited notification target cannot replace the valid History list, and dismissing the detail clears only its isolated transient state.

`PatientHistoryContentTest` passed 11/11 on API 35 at C15. `PatientRepositoryTest` separately protects generic-failure isolation, retention isolation and cleanup. C47 supersedes the earlier patient-notification reachability assumption: current visual/adaptive acceptance for these shared rows belongs to caregiver UI-206, while patient navigation must not present this component.
