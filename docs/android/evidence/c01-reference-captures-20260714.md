# C01 Current-iOS Reference Capture Evidence — 2026-07-14

## Baseline and environment

- iOS/API source baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android work branch: `android-dev@6d50fa8` before the current consent-evidence slice
- Rebaseline check: `git diff android-dev..main -- ios api package.json` returned no runtime differences.
- iOS build: `MedicationApp`, Debug, iOS Simulator, `BUILD SUCCEEDED`
- iOS device: iPhone 17 Pro, iOS 26.5, 1206 x 2622 pixels
- Android device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, font scale 1.0
- Locale/time zone: Japanese / `ja_JP`, `Asia/Tokyo`
- Appearance: light
- Analytics: collection disabled. The consent prompt was captured before recording the explicit declined state; later launches used `-disableAnalytics`.

## Stable captures

| Contract | State | Evidence | Deterministic setup |
|---|---|---|---|
| UI-001 | First-run analytics consent | [`c01-20260714/ui-001-analytics-consent-light.png`](c01-20260714/ui-001-analytics-consent-light.png) | Fresh install; no consent decision |
| UI-001 | Android first-run analytics consent | [`c01-20260714/android-ui-001-analytics-consent-light.png`](c01-20260714/android-ui-001-analytics-consent-light.png) | Fresh app data; production `MainActivity`, 411 x 914 dp standard viewport |
| UI-001 | Android analytics consent, compact | [`c01-20260714/android-ui-001-analytics-consent-light-compact.png`](c01-20260714/android-ui-001-analytics-consent-light-compact.png) | Fresh app data; 1080 x 1920 at 420 dpi, approximately 411 x 731 dp |
| UI-001 | Android analytics consent, large phone | [`c01-20260714/android-ui-001-analytics-consent-light-large.png`](c01-20260714/android-ui-001-analytics-consent-light-large.png) | Fresh app data; 1344 x 2992 at 480 dpi, 448 x 997 dp emulator override |
| UI-001 | Android analytics consent, 200% font | [`c01-20260714/android-ui-001-analytics-consent-font-2.0.png`](c01-20260714/android-ui-001-analytics-consent-font-2.0.png) | Fresh app data; production `MainActivity`, standard viewport, `font_scale=2.0` |
| UI-001 | Mode select default | [`c01-20260714/ui-001-mode-select-light.png`](c01-20260714/ui-001-mode-select-light.png) | Consent decided=false for collection; no selected mode/session |
| UI-001 | Android comparison after safe-area repair | [`c01-20260714/android-ui-001-mode-select-light.png`](c01-20260714/android-ui-001-mode-select-light.png) | Fresh Android app data, production `ModeSelectScreen` |
| UI-001 | Mode select dark | [`c01-20260714/ui-001-mode-select-dark.png`](c01-20260714/ui-001-mode-select-dark.png) | iOS system dark appearance, default content size |
| UI-001 | Android mode select dark | [`c01-20260714/android-ui-001-mode-select-dark.png`](c01-20260714/android-ui-001-mode-select-dark.png) | Android system dark appearance, production theme |
| UI-001 | iOS largest accessibility content size | [`c01-20260714/ui-001-mode-select-large-text.png`](c01-20260714/ui-001-mode-select-large-text.png) | `accessibility-extra-extra-extra-large`; this source screen's fixed-size text remains visually fixed |
| UI-001 | Android font scale 2.0, initial viewport | [`c01-20260714/android-ui-001-mode-select-font-2.0.png`](c01-20260714/android-ui-001-mode-select-font-2.0.png) | Android `font_scale=2.0`; production scroll container |
| UI-001 | Android font scale 2.0, family action reached | [`c01-20260714/android-ui-001-mode-select-font-2.0-scrolled.png`](c01-20260714/android-ui-001-mode-select-font-2.0-scrolled.png) | Same state after scrolling; family primary action is fully reachable |
| UI-003 | Caregiver auth choice | [`c01-20260714/ui-003-caregiver-auth-choice-light.png`](c01-20260714/ui-003-caregiver-auth-choice-light.png) | UI-test bootstrap with caregiver mode and no session |
| UI-003 | Android caregiver auth choice | [`c01-20260714/android-ui-003-caregiver-auth-choice-light.png`](c01-20260714/android-ui-003-caregiver-auth-choice-light.png) | Fresh app data; family mode selected from production UI |
| UI-004 | Caregiver login empty | [`c01-20260714/ui-004-caregiver-login-light.png`](c01-20260714/ui-004-caregiver-login-light.png) | Current universal-link login landing, no credentials |
| UI-004 | Android caregiver login empty | [`c01-20260714/android-ui-004-caregiver-login-light.png`](c01-20260714/android-ui-004-caregiver-login-light.png) | Production choice-to-login navigation, no credentials |
| UI-002 | iOS patient link empty | [`c01-20260714/ui-002-patient-link-empty-light.png`](c01-20260714/ui-002-patient-link-empty-light.png) | Current iOS Debug build; deterministic patient mode with no patient session |
| UI-002 | Android patient link empty | [`c01-20260714/android-ui-002-patient-link-empty-light.png`](c01-20260714/android-ui-002-patient-link-empty-light.png) | Production mode-select-to-patient navigation, no code entered |
| UI-002 | Android network error, 200% font | [`c01-20260714/android-ui-002-patient-link-network-error-font-2.0.png`](c01-20260714/android-ui-002-patient-link-network-error-font-2.0.png) | Production `PatientLinkContent`; valid code, localized network error, all actions reached |
| UI-005 | Android caregiver signup empty | [`c01-20260714/android-ui-005-caregiver-signup-empty-light.png`](c01-20260714/android-ui-005-caregiver-signup-empty-light.png) | Production mode-select-to-family-to-signup navigation, no credentials entered |
| UI-005 | Android confirmation required, 200% font | [`c01-20260714/android-ui-005-caregiver-signup-confirmation-font-2.0.png`](c01-20260714/android-ui-005-caregiver-signup-confirmation-font-2.0.png) | Production signup flow with deterministic no-access-token response; scrolled component fixture shows full notice and resend cooldown |
| UI-100 | Patient Today tutorial step 1/4 | [`c01-20260714/ui-100-patient-tutorial-today-light.png`](c01-20260714/ui-100-patient-tutorial-today-light.png) | `-ForceModeTutorial.patient`; production tutorial overlay and sample view |
| UI-101 | Patient Today typical content | [`c01-20260714/ui-101-patient-today-light.png`](c01-20260714/ui-101-patient-today-light.png) | `-ForceModeTutorial.patient -PatientMarketingScreenshot.today`; production shell with deterministic sample data and no overlay |
| UI-101 | Android inventory-partial Today | [`c01-20260714/android-ui-101-patient-inventory-partial-light.png`](c01-20260714/android-ui-101-patient-inventory-partial-light.png) | Production `TodayContent`; two scheduled medicines, one inventory shortage, partial bulk action and PRN entry |
| UI-102 | Android dose detail | [`c01-20260714/android-ui-102-patient-dose-detail-light.png`](c01-20260714/android-ui-102-patient-dose-detail-light.png) | Production bottom-sheet content with taken state, notes, 1.5-tablet dose, strength and tracked inventory |
| UI-104 | Android no-schedule history | [`c01-20260714/android-ui-104-patient-history-no-schedule-light.png`](c01-20260714/android-ui-104-patient-history-no-schedule-light.png) | Production `HistoryContent`; current-iOS no-plan progress, week and recent-record state |
| UI-106 | Android notification-denied settings | [`c01-20260714/android-ui-106-patient-settings-notification-denied-light.png`](c01-20260714/android-ui-106-patient-settings-notification-denied-light.png) | Production `SettingsContent`; disabled notification control and settings-app guidance reached by scrolling |

## Review disposition

- The first Android UI-001 capture exposed a real edge-to-edge defect: its content began inside the status-bar inset while iOS applies 52 points after the safe area. `ModeSelectScreen` now applies `safeDrawingPadding()` before its pinned 52 dp top padding.
- Dark comparison exposed black Android content on teal role-action circles while iOS keeps white content. The dark `onPrimary` token now follows the iOS white-on-teal contract; the repaired dark capture confirms both actions.
- The repaired Android capture matches the iOS section order, safe-area origin, 22-unit horizontal rhythm, 24-unit section rhythm, card structure, role imagery, semantic teal/orange treatment and action placement.
- At Android font scale 2.0 both cards reflow, remain scrollable and expose their primary actions. `ModeSelectScreenTest.familyActionRemainsReachableAtTwoHundredPercentFontScale` protects this behavior.
- The production first-run Analytics dialog now has Android captures at standard, compact, large-phone and 200% font configurations. Its full privacy copy and both explicit actions fit every captured viewport; Material 3 stacks the actions vertically at 200% without hiding either action. `ModeSelectScreenTest.analyticsDecisionActionsRemainReachableAtTwoHundredPercentFontScaleAndDeclineKeepsCollectionOff` protects the large-text decline path and confirms collection remains disabled.
- UI-003 matches current iOS section order, card geometry, role colors, safe area and back action. System-symbol artwork remains platform-native.
- UI-004 comparison found Android's form back action was a full-width button while current iOS uses a circular navigation action. Android now uses a 52 dp circular control and preserves the iOS-equivalent 147-unit form-card origin.
- UI-002 and UI-005 empty states are now captured from the production `MainActivity` navigation path. `PatientLinkScreenTest.errorSubmitAndBackRemainReachableAtTwoHundredPercentFontScale` protects the long network error, enabled submit action and mode-reset action at 200% font scale. `CaregiverAuthFlowScreenTest.confirmationRequiredAndResendCooldownRemainReachableAtTwoHundredPercentFontScale` drives the real typed repository transition to confirmation-required state and protects both the confirmation notice and resend countdown at 200%.
- The current iOS UI-002 empty state was rebuilt and captured on the same iPhone 17 Pro reference simulator. Android matches the 48-unit safe-area offset, header/card/action order, disabled treatment and Japanese copy; platform-native field and symbol rendering remain intentionally different.
- UI-004/UI-005 error auditing found Android was collapsing multiple Supabase message/status failures into login-generic copy. `AuthFailure` and `SessionUserMessage` now reproduce current iOS message-first and HTTP-status classification for invalid credentials, confirmation delivery/required, duplicate email, weak password, invalid email, credential check, forbidden, not-found, rate-limit, network and unavailable states; displayed strings are exact current-iOS Japanese copy. Resend keeps its operation-specific 429 and unknown-failure states. Loading regressions also prove Patient link and Caregiver signup disable duplicate submission while their progress indicators replace the primary action content. Leaving login/signup now clears transient error, confirmation and resend state, so re-entering a form cannot restore a stale success notice.
- Remaining UI-001 differences are platform typography/icon rendering and viewport-class height. Android standard/compact/large/200%-font Analytics consent evidence is complete; matched iOS compact/large variants, full TalkBack traversal and physical verification remain required before UI-001 can be marked `VERIFIED`.
- UI-100 and UI-101 use the iOS production component tree and deterministic in-app sample data; no API response, identity, medication record or auth token from a real user was captured.
- Android UI-101/UI-102/UI-104/UI-106 exceptional-state captures are emitted by their existing production-component assertions, so visual evidence and behavior cannot silently diverge. `ScreenshotFixtures.kt` writes to app cache during ordinary regression runs and, only when `persistScreenshotFixtures=true`, publishes API 29+ evidence through MediaStore under `Download/med-manager-screenshot-fixtures` for collection after Gradle uninstalls the test app.

## Remaining C01 reference work

- UI-001 matched iOS compact/large variants and physical/TalkBack verification; UI-002 valid/loading/additional failure captures and UI-005 matched iOS signup plus validation/loading/resend-result captures.
- UI-100 tutorial steps 2–4, completion/skip and 200% text.
- UI-101 remaining exceptional Today variants and matched iOS inventory-partial capture; UI-102 empty-notes/inventory variants and matched iOS detail capture; UI-103 PRN error/submitting variants.
- UI-104 remaining progress/retry/retention variants and matched iOS no-plan capture; UI-105 History day variants; UI-106 remaining settings/error states and matched iOS notification-denied capture.
- Compact/large viewport pairs and dark/large-text variants required by `ui-screen-contracts.md`.

Android dark captures for UI-101/UI-104/UI-106 and an Android UI-101 200% font capture are now recorded separately under `c06-20260714/`. They reduce the Android adaptive-matrix residual but do not replace the missing matched iOS pairs above.

C01 remains in progress until the full scoped state inventory is captured. These files are the first current-baseline references and replace older evidence only for the states listed above.

Latest post-fix verification: JVM tests, Debug/Release assembly and Lint pass; the final instrumentation suite passes 108/108 on API 26, API 33 and API 35 (324/324), plus 108/108 on the separate API-35 448 x 997 dp large-phone override. The current iOS Debug reference build also succeeds, and `git diff --check` passes.

To regenerate persistent Android fixtures on API 29 or later:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.afterlifearchive.medmanager.ui.PatientTodayContentTest,com.afterlifearchive.medmanager.ui.PatientHistoryContentTest,com.afterlifearchive.medmanager.ui.PatientSettingsContentTest \
  -Pandroid.testInstrumentationRunnerArguments.persistScreenshotFixtures=true
```
