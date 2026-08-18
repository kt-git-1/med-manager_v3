# C147 A302SH caregiver-home smoke check — 2026-08-18

**Date:** 2026-08-18

**Branch:** `android-dev`  
**Commit:** `2a9f625`
**Device:** `SX3LHMB430113755` (Sharp A302SH, Android 15)

## Scope

Re-ran focused UI regression on physical A302SH to keep live-device parity confidence after recent local evidence updates.

## Command

```bash
cd android
ANDROID_SERIAL=SX3LHMB430113755 \
  ./gradlew :app:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.afterlifearchive.medmanager.ui.CaregiverHomeScreenTest
```

## Result

- Completed tests: `22`
- Failures: `0`
- Errors: `0`
- Skipped: `0`

Build output confirms successful device execution (`BUILD SUCCESSFUL`), with no app behavior changes made during this run.
