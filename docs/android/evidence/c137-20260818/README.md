# C137 Connected UI shard verification — 2026-08-18

**Date:** 2026-08-18

**Branch:** `android-dev`
**Commit:** `fe914e6` (`fe914e65a5a87c21aad7cb96bf5303c1e288f823`)
**Target:** `SX3LHMB430113755` (Sharp A302SH, Android 15 / API 35, awake & unlocked)

## Scope

- Re-ran the reproducible connected-device shard runner on the physical target.
- Confirmed launcher flow and app startup before test execution.
- Kept target disposable as required by the runner contract; runner uninstalled app and test packages on completion.

## Command

```bash
cd android
export ANDROID_UI_TEST_SHARDS=4
./scripts/run-connected-ui-shards.sh SX3LHMB430113755
```

## Results

All shards passed with no failures/errors/skips.

| Shard | Tests | Failures | Errors | Skipped | XML |
|---|---:|---:|---:|---:|---|
| 1/4 | 66 | 0 | 0 | 0 | `shard-1-of-4.xml` |
| 2/4 | 59 | 0 | 0 | 0 | `shard-2-of-4.xml` |
| 3/4 | 80 | 0 | 0 | 0 | `shard-3-of-4.xml` |
| 4/4 | 78 | 0 | 0 | 0 | `shard-4-of-4.xml` |
| **Total** | **283** | **0** | **0** | **0** | `app/build/reports/connected-ui-shards/results.tsv` |

## Evidence paths

- `android/app/build/reports/connected-ui-shards/results.tsv`
- `android/app/build/reports/connected-ui-shards/shard-1-of-4.xml`
- `android/app/build/reports/connected-ui-shards/shard-2-of-4.xml`
- `android/app/build/reports/connected-ui-shards/shard-3-of-4.xml`
- `android/app/build/reports/connected-ui-shards/shard-4-of-4.xml`

## Notes

- `adb installDebug` was not part of this run; runner executed install/uninstall internally.
- Physical target was clean/removed after execution by the runner’s trap handler.
- This is consistent with prior local physical regression evidence in this phase.
