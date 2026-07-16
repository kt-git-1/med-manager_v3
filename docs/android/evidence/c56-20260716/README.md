# C56 UI-208 Caregiver Settings current-runtime reconciliation

## Authority and scope

- Current iOS source was read directly from the clean parallel worktree at `main@3e52fb2` on 2026-07-16. The Android implementation work remained isolated on `android-dev`.
- Audited iOS files and SHA-256: `PatientManagementView.swift` (`611c9d5f...d56c8`), `PatientCreateView.swift` (`ee7f9716...a423`), `PatientLinkCodeView.swift` (`3480c988...c2f0`), `CaregiverHomeView.swift` (`c9bf2053...48f`) and `StateViews.swift` (`2d9432f8...d56c8`).
- This checkpoint closes emulator-verifiable UI-208 behavior. It does not claim physical TalkBack, clipboard, Sharesheet, browser, FCM, OEM IME or destructive production-account verification.

## Closed residuals

- The zero-patient state now owns one `患者を登録する` CTA and presents creation in a dismissible modal sheet; the submit action is disabled for a blank trimmed name and preserves max-50/server failure handling.
- Successful creation dismisses the sheet, keeps the created patient selected and inserts the exact current-iOS `次は連携コードを発行` guide with issue/close actions.
- Multiple patients now use a native exposed menu rather than an always-expanded radio list. With no valid selection, the screen shows the current-iOS selection guidance; one patient retains the compact selected row.
- Patient-scoped push settings are omitted while there are zero patients. Analytics, legal/support and account controls remain available in the no-patient state.
- In-flight creation blocks only the modal surface, avoiding duplicate progress overlays while retaining the exact `更新中...` feedback.

## Runtime evidence

All files below are fresh API-35 production-Compose captures from this checkpoint:

| State | Evidence |
|---|---|
| Empty state and registration CTA | [`android-ui-208-caregiver-settings-empty-light.png`](raw/android/android-ui-208-caregiver-settings-empty-light.png) |
| Registration sheet, blank action disabled | [`android-ui-208-caregiver-create-sheet-light-matched.png`](raw/android/android-ui-208-caregiver-create-sheet-light-matched.png) |
| Multiple-patient menu with no selection | [`android-ui-208-caregiver-settings-selection-light-matched.png`](raw/android/android-ui-208-caregiver-settings-selection-light-matched.png) |
| Immediate post-create code guide | [`android-ui-208-caregiver-post-create-guide-light-matched.png`](raw/android/android-ui-208-caregiver-post-create-guide-light-matched.png) |
| Selected-patient hierarchy | [`android-ui-208-caregiver-settings-light.png`](raw/android/android-ui-208-caregiver-settings-light.png) |
| Initial loading / recovery | [`loading`](raw/android/android-ui-208-caregiver-settings-loading-light.png) / [`error`](raw/android/android-ui-208-caregiver-settings-error-light.png) |
| Linking-code / slot-time sheets | [`linking code`](raw/android/android-ui-208-caregiver-linking-code-sheet-light.png) / [`slot times`](raw/android/android-ui-208-caregiver-slot-times-sheet-light.png) |
| Dark 200% top / lower actions | [`top`](raw/android/android-ui-208-caregiver-settings-dark-font-2.0.png) / [`lower`](raw/android/android-ui-208-caregiver-settings-actions-dark-font-2.0.png) |
| Dark 200% sheets | [`linking code`](raw/android/android-ui-208-caregiver-linking-code-sheet-dark-font-2.0.png) / [`slot times`](raw/android/android-ui-208-caregiver-slot-times-sheet-dark-font-2.0.png) |

## Automated gate

- `CaregiverHomeScreenTest`: 20/20 on API 35.
- `CaregiverLargeTextUiTest`: 10/10 on API 35, including light/dark 200% Settings and both sheets.
- Complete API-35 instrumentation suite: 259/259, zero skipped or failed.
- JVM: 186/186, zero skipped or failed.
- `lintDebug`, `assembleDebug` and `assembleRelease`: pass.

The expanded 259-test suite is ready for the next API 26/33/35 emulator rerun before external Gate I work.
