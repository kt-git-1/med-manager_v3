# C41 UI-002 Current iOS Matched Patient Link

## Baseline and capture method

- Product baseline: `main@1cf8aef`; Android starting point: `android-dev@0a7e748`.
- Current iOS source was launched without source edits on a disposable iPhone 17e / iOS 26.5 simulator through the checked-in patient UI-test bootstrap.
- Simulator accessibility inspection confirmed the title, numeric code field, disabled/enabled submit action and mode-reset order.
- Filled references use synthetic code `123456` for display only. Submit was never invoked, so no patient link request or session was created.
- Android captures use the production `PatientLinkContent` on the API 35 AVD and enter the same digits through the real field before dismissing the keyboard.

## Matched evidence

| State | Diagnostic pair |
|---|---|
| Empty light | `ui-002-empty-light-side-by-side.png` |
| Filled light, keyboard dismissed | `ui-002-filled-light-side-by-side.png` |
| Filled dark, keyboard dismissed | `ui-002-filled-dark-side-by-side.png` |
| iOS OS Accessibility XXXL / Android 200% dark filled | `ui-002-dark-adaptive-side-by-side.png` |

The iOS application caps Dynamic Type at `.xLarge`; its ordinary dark and OS Accessibility XXXL files are therefore byte-identical. Android retains the stricter 200%-font requirement and keeps the code field, send and mode-reset actions scroll-reachable.

## Drift closed

- Calibrated the code field, send action and mode-reset target heights against the current device pair.
- Calibrated the header/card/action gaps so the card and mode-reset origins match the current SwiftUI runtime.
- Matched the iOS `.xLarge` visual hierarchy for the card label, monospaced six digits, number glyph, check glyph, send label, subtitle and mode-reset label.
- Replaced the long Material back arrow with a chevron matching the SwiftUI navigation meaning.
- Added current-runtime empty, real-field synthetic-filled light/dark and Android 200%-font device regressions.

SF Symbols and Material glyph outlines, status/navigation bars and Android's true 200% reflow remain platform-native. Copy, card geometry, semantic teal roles, enabled/disabled state, input filtering and accessibility order match the current runtime.

## Automated result

- `PatientLinkScreenTest`: 11/11 passed on API 35 after final calibration.
- The suite also verifies digit-only six-character sanitization, submit enablement, loading lock, inline errors, exact pinned copy and 200%-font failure reachability.
- JVM unit tests, Debug assembly and lint passed.
- Live link-code exchange, physical keyboard/OEM IME and TalkBack traversal remain Gate I evidence.

## SHA-256

| Artifact | SHA-256 |
|---|---|
| `ios-ui-002-patient-link-empty-light.png` | `53fecfec6129dcffce17a20c3e4ac0617cf2ac09ff50714e5fc8f7a49dd6f396` |
| `android-ui-002-patient-link-empty-light.png` | `4b58d631e06d81a9f7d6685198e82035f7fbfdefc9dc6441fca6e02415415b58` |
| `ios-ui-002-patient-link-filled-light.png` | `8ae663a88a9ae18d1cc57c0eebc0f8c1136d0f1411115f55051915027cd6e464` |
| `android-ui-002-patient-link-filled-light.png` | `b0d7890e65830ea472d0703824c83dad3fd3f98b2f40d53c926fdc7e209ccb63` |
| `ios-ui-002-patient-link-filled-dark.png` | `f79696d98006988a39b41fd152217ba003281dc9c96c50eb704846ff74eabb6e` |
| `android-ui-002-patient-link-filled-dark.png` | `a821719c404e3613141a611c786a2048a4b8cc8e47d4232951bbe8bc3882fcf3` |
| `ios-ui-002-patient-link-filled-dark-axxxl.png` | `f79696d98006988a39b41fd152217ba003281dc9c96c50eb704846ff74eabb6e` |
| `android-ui-002-patient-link-filled-dark-font-2.0.png` | `72a0aef69d18b14189dd75b7f2ac12483395ab84b67e19e17c561622ef79738f` |
