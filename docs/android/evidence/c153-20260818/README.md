# C153 Android connected UI shards (A302SH awake/unlocked pass)

**Date:** 2026-08-18  
**Branch:** `android-dev`  
**Device:** `SX3LHMB430113755` (Sharp A302SH, Android 15 / API 35)

## Why this run

A follow-up full 4-shard connected-debug suite on physical A302SH while explicitly awake and unlocked.

## Command

```bash
cd /Users/kaito/workspace/med-manager_v3-android-worktree
./android/scripts/run-connected-ui-shards.sh SX3LHMB430113755
```

## Result

| Shard | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| 1/4 | 66 | 0 | 0 | 0 |
| 2/4 | 59 | 0 | 0 | 0 |
| 3/4 | 80 | 0 | 0 | 0 |
| 4/4 | 78 | 0 | 0 | 0 |
| **Total** | **283** | **0** | **0** | **0** |

`run-connected-ui-shards.sh` preserved XML/summary artifacts under `android/app/build/reports/connected-ui-shards` and this directory.

## Attached artifacts

- `results.tsv`
- `shard-1-of-4.xml`
- `shard-2-of-4.xml`
- `shard-3-of-4.xml`
- `shard-4-of-4.xml`
- `a302sh-run-1-power.txt`
- `a302sh-run-1-window.txt`

