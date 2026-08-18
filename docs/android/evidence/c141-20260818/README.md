# C141 Life-cycle smoke on physical + emulator — 2026-08-18

**Date:** 2026-08-18

**Branch:** `android-dev`
**Commit:** `bde8005`
**Devices:**
- `SX3LHMB430113755` (Sharp A302SH, Android 15)
- `emulator-5554` (API 35)

## Scope

Performed a lightweight life-cycle smoke across both devices:

- force-stop app
- launch
- home
- relaunch
- kill process
- relaunch
- verify focused activity/state and process existence

## Command

```bash
cd android
for s in SX3LHMB430113755 emulator-5554; do
  adb -s $s shell am force-stop com.afterlifearchive.medmanager || true
  adb -s $s shell am start -n com.afterlifearchive.medmanager/.MainActivity
  sleep 1
  adb -s $s shell input keyevent KEYCODE_HOME
  sleep 1
  adb -s $s shell am start -n com.afterlifearchive.medmanager/.MainActivity
  sleep 1
  adb -s $s shell am kill com.afterlifearchive.medmanager || true
  adb -s $s shell am start -n com.afterlifearchive.medmanager/.MainActivity
  sleep 1
  adb -s $s shell dumpsys window | rg -n "mCurrentFocus|mFocusedApp" | sed -n '1,4p'
  adb -s $s shell ps -A | rg com.afterlifearchive.medmanager | head -n 2
done
```

## Results

Both devices completed the sequence and returned to `com.afterlifearchive.medmanager/.MainActivity` focus after each relaunch.

- Sharp A302SH: `mCurrentFocus=Window{df31691 u0 com.afterlifearchive.medmanager/com.afterlifearchive.medmanager.MainActivity}`
- API 35 emulator: `mCurrentFocus=Window{e56aa51 u0 com.afterlifearchive.medmanager/com.afterlifearchive.medmanager.MainActivity}`

## Notes

- This is a life-cycle behavior smoke intended for manual QA continuation; no production/smoke telemetry settings were changed.
