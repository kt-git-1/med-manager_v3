# C148 Android accessibility smoke under constrained A302SH state

**Date:** 2026-08-18  
**Branch:** `android-dev`  
**Target:** SHARP A302SH (`SX3LHMB430113755`, Android 15) and Emulator (`emulator-5554`)

## Scope

- Re-check `PatientAccessibilityTest` (3 tests) after recent UI work.
- Verify whether failures on the retained OEM device are reproducible versus emulator behavior.
- Capture pre-run device state for deterministic diagnosis.

## Commands

```bash
cd android

# Retained OEM (explicit serial)
./gradlew :app:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.afterlifearchive.medmanager.ui.PatientAccessibilityTest

# Emulator (sanity contrast)
./gradlew :app:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.afterlifearchive.medmanager.ui.PatientAccessibilityTest
```

```bash
cd android
./scripts/run-connected-ui-shards.sh SX3LHMB430113755
```

## Result

### A302SH

- **Result:** 0 passed / 3 failed / 0 skipped  
- **Failure signature:** `No compose hierarchies found in the app` (all 3 cases).

Each failing case (`PatientAccessibilityTest`) reported the same underlying Compose test startup failure:

- `tutorialRemainsOperableAtTwoHundredPercentFontScale`
- `bottomNavigationExposesOrderedLabelsTabRolesAndSelection`
- `simpleHistoryExposesPatientFacingSummary`

The runner did not get past test execution due missing compose hierarchy on A302SH.

Pre-run lock-state checks collected from the device:

- `mWakefulness=Dozing`
- `mInputRestricted=true`
- `mShowingDream=false mDreamingLockscreen=true`
- `isKeyguardShowing=true`

### Emulator

- **Result:** 3 passed / 0 failed / 0 skipped  
- Same command on `emulator-5554` completed successfully.

## Notes

- This reproduces the same behavior previously observed in the C103 incident narrative: A302SH requires explicit awake + unlocked preconditions for connected UI execution, otherwise instrumented Compose trees are not available.
- The one-command shard runner now correctly fails fast with:

> `The selected adb target must be awake before running UI tests. Wake and unlock it, then retry.`

- No code changes were made in this sweep; this commit is evidence-only to separate environment-precondition failures from implementation regressions.

## Attached local evidence

- `c148-a302sh-patientaccessibility.log`
- `c148-emulator-patientaccessibility.log`
- `a302sh-power.txt`
