# C38 UI-003 Current iOS Matched Auth Choice

## Baseline and capture method

- Product baseline: `main@1cf8aef`; Android starting point: `android-dev@86590cf`.
- iOS was built without source edits from this Android worktree and launched through the checked-in `UITEST_SESSION_BOOTSTRAP=1` / `UITEST_MODE=caregiver` boundary. No account, credential or network request was used.
- iOS: iPhone 17e simulator, iOS 26.5, 1170 x 2532. Android: API 35 AVD, 1080 x 2400.
- Japanese locale, deterministic status bars where supported, and Analytics disabled.

## Matched evidence

| State | Diagnostic pair |
|---|---|
| Light | `ui-003-light-side-by-side.png` |
| Dark | `ui-003-dark-side-by-side.png` |
| iOS OS Accessibility XXXL / Android 200% dark | `ui-003-dark-adaptive-side-by-side.png` |

The iOS application currently applies `.dynamicTypeSize(.xLarge)` at the root. Consequently its dark and OS Accessibility XXXL captures are byte-identical. Android intentionally retains the stricter project requirement: at 200% font scale the header and cards expand, and login, signup and mode-reset remain scroll-reachable. The adaptive pair documents this current cross-platform policy instead of falsely treating the fixed iOS image as enlarged text.

## Drift closed

- Replaced the orange filled shield header with the current caregiver-avatar hierarchy: 62 dp semantic surface, shadow, teal person/security-equivalent glyph.
- Replaced the login person glyph with a directional sign-in symbol.
- Replaced the generic add-circle signup glyph with a person-add symbol.
- Replaced long-shaft forward/back arrows with chevron navigation affordances matching the SwiftUI hierarchy.
- Added a stable header-icon semantic tag and current-runtime device-capture regressions.

SF Symbols and Material glyph outlines, system bars and responsive line wrapping remain platform-native. Copy, section order, role colors, card hierarchy, targets and navigation meaning match the current iOS runtime.

## Automated result

- Current `CaregiverAuthChoiceScreenTest`: 6/6 passed on API 35.
- Full related Auth Choice plus login/signup flow: 18/18 passed on API 35.
- `testDebugUnitTest`, `assembleDebug` and `lintDebug`: passed.
- The suite verifies canonical copy, callback isolation, light/dark hierarchy, header presence and complete 200%-font action reachability.
- Physical TalkBack traversal remains Gate I evidence.

## SHA-256

| Artifact | SHA-256 |
|---|---|
| `ios-ui-003-caregiver-auth-choice-light.png` | `157b13207082e56d166009b8910838a26699ad03f6d29efb2a07b13145a739de` |
| `android-ui-003-caregiver-auth-choice-matched-light.png` | `9060ac5317d560baf25393e4b83dde07405a3da9a3120481f63caf814eea092a` |
| `ios-ui-003-caregiver-auth-choice-dark.png` | `9f0a1ab4f0c28da5c4ccd675801a94f89478e014bdcf7437aff4763a9ceb9928` |
| `android-ui-003-caregiver-auth-choice-matched-dark.png` | `0099495fa9a87dbbfb83074bec1837fff1c3319ea01fa09911d795e9e345ff5e` |
| `ios-ui-003-caregiver-auth-choice-dark-axxxl.png` | `9f0a1ab4f0c28da5c4ccd675801a94f89478e014bdcf7437aff4763a9ceb9928` |
| `android-ui-003-caregiver-auth-choice-dark-font-2.0.png` | `c14687aa0dd780d913317657f7244e94a7399dd2f2aff5f04b0234f2c071097c` |
