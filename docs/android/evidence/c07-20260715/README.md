# C07 UI-001 Compact/Large Matched Evidence — 2026-07-15

## Baseline and capture conditions

- iOS/API source baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android capture baseline: `android-dev@f1aae2b`
- iOS build: current `MedicationApp` Debug source, fixed DerivedData path, simulator build with code signing disabled
- Locale/appearance: Japanese (`ja_JP`), light mode
- Analytics collection: disabled; consent screenshots were taken before a decision was stored
- iOS compact: iPhone 17e, iOS 26.5, 1170 x 2532 pixels
- iOS large: iPhone 17 Pro Max, iOS 26.5, 1320 x 2868 pixels
- Android compact: API 35 emulator override, 1080 x 1920 at 420 dpi, approximately 411 x 731 dp
- Android large: API 35 emulator override, 1344 x 2992 at 480 dpi, 448 x 997 dp
- Font scale: default on both platforms

The iOS app was launched with `-AppleLanguages '(ja)' -AppleLocale ja_JP -disableAnalytics`. The Android captures use the production `MainActivity`, not a preview-only host.

## Matched evidence

| State | iOS compact | Android compact | iOS large | Android large |
|---|---|---|---|---|
| Mode select | [`ios-ui-001-mode-select-compact-light.png`](ios-ui-001-mode-select-compact-light.png) | [`android-ui-001-mode-select-compact-light.png`](android-ui-001-mode-select-compact-light.png) | [`ios-ui-001-mode-select-large-light.png`](ios-ui-001-mode-select-large-light.png) | [`android-ui-001-mode-select-large-light.png`](android-ui-001-mode-select-large-light.png) |
| Analytics consent | [`ios-ui-001-analytics-consent-compact-light.png`](ios-ui-001-analytics-consent-compact-light.png) | [`../c01-20260714/android-ui-001-analytics-consent-light-compact.png`](../c01-20260714/android-ui-001-analytics-consent-light-compact.png) | [`ios-ui-001-analytics-consent-large-light.png`](ios-ui-001-analytics-consent-large-light.png) | [`../c01-20260714/android-ui-001-analytics-consent-light-large.png`](../c01-20260714/android-ui-001-analytics-consent-light-large.png) |

## Review disposition

- Both mode cards, their descriptions and both primary actions remain fully visible at compact and large phone sizes on each platform.
- Compact wrapping differs only where platform font metrics require it. It does not change copy, hierarchy, emphasis or action reachability.
- Large-phone whitespace grows proportionally without changing the iOS-derived card order or role emphasis.
- Analytics consent preserves the same privacy statement, explicit decline/allow choices and blocked first-run state. SwiftUI alert and Material 3 dialog geometry remain intentionally platform-native.
- No Android production-code repair was required from this comparison.

This closes the matched iOS compact/large portion of UI-001. UI-001 remains `IMPLEMENTED`, rather than `VERIFIED`, until physical-device and full TalkBack traversal evidence is recorded.
