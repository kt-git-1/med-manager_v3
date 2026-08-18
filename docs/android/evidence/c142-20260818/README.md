# C142 Connected UI smoke on physical + API 35 emulator — 2026-08-18

**Date:** 2026-08-18

**Branch:** `android-dev`
**Commit:** `1d2162e`
**Devices:**
- `SX3LHMB430113755` (Sharp A302SH, Android 15)
- `emulator-5554` (API 35)

## Scope

Performed two focused `connectedDebugAndroidTest` sweeps to keep functional momentum before manual QA handoff:

1) `PatientTodayContentTest` (UI contract + rendering)
2) `PatientNavigationStateTest` + `CaregiverHomeScreenTest` (navigation + caregiver shell/settings paths)

## Commands

```bash
cd android

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.afterlifearchive.medmanager.ui.PatientTodayContentTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.afterlifearchive.medmanager.ui.PatientNavigationStateTest,com.afterlifearchive.medmanager.ui.CaregiverHomeScreenTest
```

## Results

- Run 1 (`PatientTodayContentTest`)
  - API 35 emulator: `28/28` passed
  - A302SH (physical): `28/28` passed

- Run 2 (`PatientNavigationStateTest`, `CaregiverHomeScreenTest`)
  - API 35 emulator: `23/23` passed
  - A302SH (physical): `23/23` passed

No failures, skips, or errors were observed in either run.

## Evidence artifacts

- `android/app/build/outputs/androidTest-results/connected/debug/TEST-MedicationApp_API_35(AVD) - 15-_app-.xml`
- `android/app/build/outputs/androidTest-results/connected/debug/TEST-A302SH - 15-_app-.xml`
- `android/app/build/outputs/androidTest-results/connected/debug/test-result.pb`

## Notes

- All executed tests were run with a clean, already-cached test APK and fixtures; no environment variables or app-logic configuration was changed by this sweep.
- This is a local regression slice; release external gates (Play, FCM, signed install, TalkBack-finger traversal) remain pending by design.
