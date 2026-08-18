# C140 Emulator connected UI shard verification — 2026-08-18

**Date:** 2026-08-18

**Branch:** `android-dev`
**Commit:** `ecf5c97`
**Target:** `emulator-5554` (API 35)

## Scope

After keeping the branch evidence-only and release-gated, we re-ran the reproducible connected-device 4-shard UI suite on emulator with an empty disposable target state.

## Command

```bash
cd android
unset ANDROID_UI_TEST_SHARD_INDEX
export ANDROID_UI_TEST_SHARDS=4
./scripts/run-connected-ui-shards.sh emulator-5554
```

### Results

- All four shards passed with zero failures/errors/skips.

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

- The runner enforces uninstall of test/app packages after completion.
- `./gradlew :app:installDebug` was run after this run to reinstall app on both devices for manual verification handoff.
