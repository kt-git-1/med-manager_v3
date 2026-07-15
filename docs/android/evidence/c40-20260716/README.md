# C40 UI-005 Current iOS Matched Caregiver Signup

## Baseline and capture method

- Product baseline: `main@1cf8aef`; Android starting point: `android-dev@34af337`.
- Current iOS source was launched without source edits on a disposable iPhone 17e / iOS 26.5 simulator through the checked-in caregiver UI-test bootstrap.
- Simulator accessibility inspection confirmed the back, person-plus header, email, password, confirmation and submit order.
- Filled references use synthetic `care@example.com` / `SamplePass123!` values for display only. The signup action was never invoked, so no account, email or network request was created.
- Android captures use the production `CaregiverAuthFlow` on the API 35 AVD.

## Matched evidence

| State | Diagnostic pair |
|---|---|
| Empty light | `ui-005-empty-light-side-by-side.png` |
| Filled light, keyboard dismissed | `ui-005-filled-light-side-by-side.png` |
| Filled dark, keyboard dismissed | `ui-005-filled-dark-side-by-side.png` |
| iOS OS Accessibility XXXL / Android 200% dark empty | `ui-005-dark-adaptive-side-by-side.png` |

The iOS application caps Dynamic Type at `.xLarge`, so the OS Accessibility XXXL reference remains visually capped. Android intentionally preserves the stricter 200%-font requirement and makes every field, submit action and circular back action scroll-reachable.

## Drift closed

- Reused the C39 white/black form canvas, system-blue accent, semantic glass card, quaternary-fill fields and circular chevron navigation.
- Replaced the generic add-circle title symbol with a person-add semantic glyph.
- Replaced the repeated plain lock with a distinct lock-reset confirmation glyph matching `lock.rotation` meaning.
- Applied the signup-specific card origin from the SwiftUI 52-unit leading spacer rather than forcing the login layout origin.
- Calibrated only the signup header's icon/title/subtitle metrics so the accepted C39 login geometry and evidence remain unchanged.
- Added deterministic empty, synthetic-filled light/dark and dark 200%-font regressions without submitting the form.

SF Symbols and Material glyph outlines, system bars and responsive line wrapping remain platform-native. Copy, hierarchy, card origin, fields, enabled/disabled treatment, navigation meaning and accessibility order match the current runtime.

## Automated result

- Full related Auth Choice plus login/signup flow: 26/26 passed on API 35 after final source calibration.
- Existing login/signup IME, validation, loading, confirmation-required, resend success/loading/429 and stale-state regressions remain in the same suite.
- JVM unit tests, Debug assembly and lint passed.
- Real email delivery, physical keyboard/OEM IME and TalkBack traversal remain Gate I evidence.

## SHA-256

| Artifact | SHA-256 |
|---|---|
| `ios-ui-005-caregiver-signup-empty-light.png` | `36c68fb20c8db8d2e494d73c27b9e8f70a7d232323aacaaa926038b0c816da9d` |
| `android-ui-005-caregiver-signup-empty-light.png` | `133e79d44715541a52a7af496d779c97805e5ecd9a705f05858d794bd8f70e37` |
| `ios-ui-005-caregiver-signup-filled-light.png` | `b463beade475570ff6bc1a1fc00c92cbb26569411445e45a4790422ebb930321` |
| `android-ui-005-caregiver-signup-filled-light.png` | `c5f4c3eef0e55bfc276bb2d7969463bb94302cb41cc6ee39cfaec1899698ba69` |
| `ios-ui-005-caregiver-signup-filled-dark.png` | `7fc1a13b932492b3d7e8c106e9225c76a35a7847c25aa601eea357db2ec47864` |
| `android-ui-005-caregiver-signup-filled-dark.png` | `31a7dc98324412388be8faf83da37f960e76c823ed81daf12353b48e99d730eb` |
| `ios-ui-005-caregiver-signup-empty-dark-axxxl.png` | `a4a9a2e16083368d12dfa61fba14a35820a125d6ffcd220d276581de1e89083b` |
| `android-ui-005-caregiver-signup-empty-dark-font-2.0.png` | `617b95902124d76d50859cb000599b500d98680f28f32fe4779a8a4262c0339d` |
