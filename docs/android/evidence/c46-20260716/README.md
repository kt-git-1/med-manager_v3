# C46 UI-104 Current-iOS Patient History Matrix — 2026-07-16

## Baseline and environment

- iOS UI baseline: clean parallel iOS worktree `staging@2b7d1fe`
- Android baseline: `android-dev@92ded68` before this parity repair
- iOS: disposable iPhone 17e simulator, iOS 26.5, production `HistoryMonthView` / `HistoryRetentionLockView`
- Android: `MedicationApp_API_35`, 1080 x 2400, 420 dpi, production `PatientHistoryScreen`
- Locale/time: Japanese, deterministic Tokyo date 2026-07-16
- Comparison diagnostics: both raw captures normalized to 1080 x 2400, then rendered side by side and at 50% opacity

The simulator-only iOS route selected synthetic local lifecycle data and rendered the unchanged production History components. It performed no API request. The route was removed after capture and the Android worktree contains no iOS diff. A second disposable simulator was used to re-capture stable PNGs from the already-built temporary binary without modifying the parallel iOS worktree.

## Evidence

| State | Raw iOS | Raw Android | Side by side | 50% overlay |
|---|---|---|---|---|
| No plan, light | [`ios`](ios-ui-104-patient-history-no-plan-light.png) | [`android`](android-ui-104-patient-history-no-plan-light-matched.png) | [`compare`](compare-ui-104-patient-history-no-plan-light-side-by-side.png) | [`overlay`](compare-ui-104-patient-history-no-plan-light-overlay-50.png) |
| Initial loading, light | [`ios`](ios-ui-104-patient-history-loading-light.png) | [`android`](android-ui-104-patient-history-loading-light-matched.png) | [`compare`](compare-ui-104-patient-history-loading-light-side-by-side.png) | [`overlay`](compare-ui-104-patient-history-loading-light-overlay-50.png) |
| Failure, light | [`ios`](ios-ui-104-patient-history-error-light.png) | [`android`](android-ui-104-patient-history-error-light-matched.png) | [`compare`](compare-ui-104-patient-history-error-light-side-by-side.png) | [`overlay`](compare-ui-104-patient-history-error-light-overlay-50.png) |
| Retention lock, light | [`ios`](ios-ui-104-patient-history-retention-light.png) | [`android`](android-ui-104-patient-history-retention-light-matched.png) | [`compare`](compare-ui-104-patient-history-retention-light-side-by-side.png) | [`overlay`](compare-ui-104-patient-history-retention-light-overlay-50.png) |
| No plan, dark | [`ios`](ios-ui-104-patient-history-no-plan-dark.png) | [`android`](android-ui-104-patient-history-no-plan-dark-matched.png) | [`compare`](compare-ui-104-patient-history-no-plan-dark-side-by-side.png) | [`overlay`](compare-ui-104-patient-history-no-plan-dark-overlay-50.png) |
| No plan, largest text | [`ios`](ios-ui-104-patient-history-no-plan-accessibility-xxxl.png) | [`android`](android-ui-104-patient-history-no-plan-font-2.0-matched.png) | [`compare`](compare-ui-104-patient-history-no-plan-adaptive-side-by-side.png) | [`overlay`](compare-ui-104-patient-history-no-plan-adaptive-overlay-50.png) |

## Review disposition

1. Current iOS History uses the filled clock identity. Android replaces the stale history-arrow symbol with the equivalent clock while retaining Android-native vector rendering.
2. The no-plan progress card no longer exposes the Android-only `記録済み 0回分` pill. Progress, week and recent-record hierarchy remain source-calibrated, and the 200% Android surface remains scroll-reachable.
3. Initial loading now uses the shared neutral indicator weight, scale and message hierarchy instead of a teal Material treatment.
4. Failure now follows the inset elevated warning card and leading iOS-blue `再試行` action while preserving an exact one-shot retry callback contract.
5. Retention is a full-screen lock surface, not an inline cutoff-date card. It uses current iOS title/body copy plus `更新` and `閉じる`; refresh invokes the authoritative retry and close reveals ordinary History without leaking a server cutoff date into product copy.
6. The retained day-detail retention component remains isolated for the unreached UI-105 foundation and is not used by current patient History.
7. Dark mode preserves semantic surface contrast. Current iOS caps its root at `.xLarge` under Accessibility XXXL; Android intentionally preserves complete content and scrolling at 200% rather than copying that cap.
8. Device-screenshot APIs on both simulators displayed transient black tiles in the inspection renderer. Pixel sampling proved the raw PNG backgrounds were intact; GPU-sensitive Android loading/retention evidence was additionally captured from the Compose root to make the stored artifacts deterministic.

## Verification

- `PatientHistoryContentTest`: 18/18 on API 35, including all six UI-104 states, retention actions and 200% reachability.
- Full API 35 instrumentation: 209/209, 0 skipped and 0 failed.
- JVM: 185/185, 0 skipped and 0 failed.
- `assembleDebug` and `lintDebug`: passed.
- Android worktree iOS diff after temporary capture-route removal: empty.
- Parallel iOS worktree remained clean throughout the final capture pass.

Physical TalkBack traversal, real retained-history entitlement transitions, OEM rendering/lifecycle and real-session refresh remain Gate I requirements.
