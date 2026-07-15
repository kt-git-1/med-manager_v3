# C17 UI-208 Caregiver Settings State and Sheet Contract — 2026-07-15

Baseline: `android-dev@fb8174f`, compared with the current iOS `PatientManagementView`, `PatientLinkCodeView` and time-preset sheet source contracts. Captures use deterministic production Compose state on the API 35 emulator at 1080 × 2400 and contain no real identity, linking code, token or health data.

| State | Android evidence |
|---|---|
| Initial loading | [`android-ui-208-caregiver-settings-loading-light.png`](android-ui-208-caregiver-settings-loading-light.png) |
| Initial failure with retry/login | [`android-ui-208-caregiver-settings-error-light.png`](android-ui-208-caregiver-settings-error-light.png) |
| No-patient onboarding | [`android-ui-208-caregiver-settings-empty-light.png`](android-ui-208-caregiver-settings-empty-light.png) |
| Linking-code sheet | [`android-ui-208-caregiver-linking-code-sheet-light.png`](android-ui-208-caregiver-linking-code-sheet-light.png) |
| Time-preset sheet | [`android-ui-208-caregiver-slot-times-sheet-light.png`](android-ui-208-caregiver-slot-times-sheet-light.png) |
| Linking-code sheet, dark + 200% font | [`android-ui-208-caregiver-linking-code-sheet-dark-font-2.0.png`](android-ui-208-caregiver-linking-code-sheet-dark-font-2.0.png) |
| Time-preset sheet, dark + 200% font | [`android-ui-208-caregiver-slot-times-sheet-dark-font-2.0.png`](android-ui-208-caregiver-slot-times-sheet-dark-font-2.0.png) |

## Source comparison and repairs

- Current iOS presents an issued linking code in `PatientLinkCodeView` as a sheet. Android previously expanded the value inside the settings card. Android now opens a dedicated full sheet with the same title/subtitle hierarchy, six separate digits, copy/share actions and Tokyo expiry.
- Dismissing the code sheet clears only the ephemeral issued code. Patient switching and successful destructive operations retain their existing cleanup guarantees.
- Copy now has the current-iOS completion dialog. The share payload remains the privacy-bounded public linking message; no authenticated or patient payload is added.
- The time-preset sheet now uses current-iOS `朝` / `昼` / `夜` / `眠前` labels, opens fully expanded and owns an explicit scroll surface. The save action therefore remains reachable at 200% font scale.
- Initial loading, failure and no-patient states are captured from the same assertions that verify exact retry/login and three-step onboarding behavior.

`CaregiverHomeScreenTest` passes 16/16 and `CaregiverLargeTextUiTest` passes 10/10 on API 35. Physical clipboard/Sharesheet/browser/FCM/destructive-operation checks and matched iOS screenshots remain Gate I work.
