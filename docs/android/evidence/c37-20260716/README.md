# C37 Current iOS Matched Achievement and Caregiver Status

## Baseline and method

- Product baseline: `main@1cf8aef`; Android starting point: `android-dev@00124c0`.
- The iOS references were captured from the unmodified current SwiftUI source in this Android worktree using the checked-in `-PatientHistoryAchievementPreview` and `-CaregiverTodayPreview` launch routes.
- iOS: iPhone 17e simulator, iOS 26.5, 1170 x 2532. Android: API 35 AVD, 1080 x 2400.
- Fixtures contain only deterministic sample medication data. No production account, patient, token or notification data is present.

## Matched pairs

| Surface | Light | Dark / maximum text |
|---|---|---|
| UI-104 Patient History streak | `ui-104-streak-light-side-by-side.png` | `ui-104-streak-dark-adaptive-side-by-side.png` |
| UI-201 Caregiver Today status | `ui-201-status-light-side-by-side.png` | `ui-201-status-dark-adaptive-side-by-side.png` |

The full-resolution source captures are retained beside the diagnostic pairs. iOS dark uses Accessibility XXXL; Android dark uses font scale 2.0. These are equivalent stress states, not a claim that platform typography metrics are pixel-identical.

## Drift closed

- Populated Caregiver Today rows and the single missed alert now derive their visible time from the scheduled dose and format it as `H:mm`, matching SwiftUI (`8:00`). Empty rows retain the configured `HH:mm` fallback (`08:00`). API matching continues to use strict `HH:mm` values.
- The morning slot uses the sunrise-equivalent Material icon.
- Patient streak title and achievement rows use calendar-complete and verified-equivalent Material icons.
- Stable semantic tags protect the missed-alert and progress-card hierarchy in adaptive tests.

System status/navigation bars, SF Symbols and Material icon outlines remain intentionally platform-native. Information order, copy, status hierarchy, color roles, card structure and adaptive reachability match the current iOS runtime.

## Automated result

- Focused API 35 matched-fixture matrix: 4/4 passed.
- Full affected `PatientHistoryContentTest` plus `CaregiverTodayScreenTest`: 31/31 passed on API 35.
- `testDebugUnitTest`, `assembleDebug` and `lintDebug`: passed.
- Both dark maximum-text tests scroll to and assert the target content before taking the device screenshot.
- Physical-device TalkBack, OEM font rendering and real-session behavior remain Gate I evidence.

## SHA-256

| Artifact | SHA-256 |
|---|---|
| `ios-ui-104-history-streak-light.png` | `e2b31b2d5b9bb4efaa9bdb54667b5fca1bfbfb0a07c00b0682ee343aaba526e4` |
| `android-ui-104-history-streak-light.png` | `0ac09a8768eac6438daa0f787853d66de0249c47bf3028f5424dffbad300cb7b` |
| `ios-ui-104-history-streak-dark-axxxl.png` | `25226f984b03f0a89586a4ebda4239d8425be73815a77531ac1329563642789e` |
| `android-ui-104-history-streak-dark-font-2.0.png` | `f3ab8e83bf28ea8aae53eb984123ee583facff1499c6df935646e775388e3815` |
| `ios-ui-201-caregiver-today-status-light.png` | `d51cd9ce7c1e8bec266942ccb151005f93a4d6574eff075dfc1d505a1e143598` |
| `android-ui-201-caregiver-today-status-matched-light.png` | `8a4c5e14d546262e2ac865f24eff34cc7e34f03be661e9835e410ce05c2a0ec7` |
| `ios-ui-201-caregiver-today-dark-axxxl.png` | `c9bc9dd00b9bc67b1614eda076350148530685366b80b9cb73ef10ae3255e1f9` |
| `android-ui-201-caregiver-today-dark-font-2.0.png` | `ff737b3312306de0e06ec4f4dd89e23f3abe2bcf8b41aeb2169a30759c52b3d7` |
