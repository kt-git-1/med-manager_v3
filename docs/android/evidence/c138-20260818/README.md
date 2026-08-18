# C138 Connected UI shard verification — 2026-08-18

**Date:** 2026-08-18

**Branch:** `android-dev`
**Commit:** `58570377248dc0b98b17f013e9435f535264af87`
**Target:** `SX3LHMB430113755` (Sharp A302SH, Android 15 / API 35, awake & unlocked)

## Scope

- Re-ran the reproducible connected-device shard runner on the physical target after an earlier intermittent physical shard pass issue.
- Confirmed launcher flow and app startup before test execution.
- Kept target disposable as required by the runner contract; runner uninstalled app/test packages on completion.

## Command

```bash
cd /Users/kaito/workspace/med-manager_v3-android-worktree
./android/scripts/run-connected-ui-shards.sh SX3LHMB430113755
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

- `adb installDebug` was not part of this run; runner handled install/uninstall internally.
- Physical target was clean/removed after execution by the runner’s trap handler.
- The earlier flaky single-shard result is no longer reproducible in this rerun.
