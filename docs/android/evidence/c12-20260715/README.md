# C12 UI-003 Auth Choice Dark and 200% Text — 2026-07-15

## Baseline and environment

- iOS/API baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android baseline: `android-dev@9795a2b`
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, Japanese
- Surface: production `CaregiverAuthChoiceScreen`

## Evidence

| Required state | Evidence |
|---|---|
| Dark theme | [`android-ui-003-caregiver-auth-choice-dark.png`](android-ui-003-caregiver-auth-choice-dark.png) |
| 200% font, initial viewport | [`android-ui-003-caregiver-auth-choice-font-2.0.png`](android-ui-003-caregiver-auth-choice-font-2.0.png) |
| 200% font, lower action reached | [`android-ui-003-caregiver-auth-choice-font-2.0-scrolled.png`](android-ui-003-caregiver-auth-choice-font-2.0-scrolled.png) |

## Review disposition

- Dark theme preserves white primary content, readable secondary text and the teal/orange login/signup role distinction.
- Header and both cards expand at 200% instead of clipping their Japanese copy.
- Login, signup and mode-reset actions remain individually reachable and invoke only their corresponding callback.
- The mode-reset action remains visible within the standard phone viewport; the explicit scroll assertion protects smaller/later layout variants.
- System icon glyphs remain platform-native while size, role and placement follow the current iOS hierarchy.

No production UI repair was required. `CaregiverAuthChoiceScreenTest` now has four API-35 tests covering canonical content, action isolation, dark rendering and complete 200%-font action reachability.

Matched iOS dark/largest-text and physical TalkBack verification remain C01/Gate I work; Android's required UI-003 dark/large-text matrix is complete.
