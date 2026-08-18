# C143 Full connected Android UI suite on physical + API 35 emulator — 2026-08-18

**Date:** 2026-08-18

**Branch:** `android-dev`
**Commit:** `5ee91a6`
**Devices:**
- `SX3LHMB430113755` (Sharp A302SH, Android 15)
- `emulator-5554` (API 35)

## Scope

Executed the full `connectedDebugAndroidTest` matrix on both connected devices to verify full local UI regression parity after the previous targeted smoke slices.

## Command

```bash
cd android
./gradlew :app:connectedDebugAndroidTest
```

## Results

- Total tests executed per device: `283`
- Failures: `0`
- Errors: `0`
- Skipped: `0`

- A302SH (physical): `283/283` passed
- API 35 emulator: `283/283` passed

## Evidence artifacts

- `android/app/build/outputs/androidTest-results/connected/debug/test-result.pb`
- `android/app/build/outputs/androidTest-results/connected/debug/TEST-A302SH - 15-_app-.xml`
- `android/app/build/outputs/androidTest-results/connected/debug/TEST-MedicationApp_API_35(AVD) - 15-_app-.xml`

## Notes

- All tests completed successfully with existing cached APK/fixtures and no environment changes.
- This preserves local functional regression confidence; external production/Play/FCM/manual gates remain in the remaining workflow.
