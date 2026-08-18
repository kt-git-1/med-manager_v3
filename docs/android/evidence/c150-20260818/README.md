# C150 Android connected UI shards (A302SH awake/unlocked pass)

**Date:** 2026-08-18  
**Branch:** `android-dev`  
**Device:** `SX3LHMB430113755` (Sharp A302SH, Android 15 / API 35)

## Why this run

This is the resumed physical-device verification after a retry where the device was confirmed
awake and unlocked (as documented in `a302sh-postrun-power.txt` / `a302sh-end-power.txt`).

## Command

```bash
cd android
ANDROID_UI_TEST_SHARDS=4 ./scripts/run-connected-ui-shards.sh SX3LHMB430113755
```

## Result

All four shards passed:

| Shard | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| 1/4 | 66 | 0 | 0 | 0 |
| 2/4 | 59 | 0 | 0 | 0 |
| 3/4 | 80 | 0 | 0 | 0 |
| 4/4 | 78 | 0 | 0 | 0 |
| **Total** | **283** | **0** | **0** | **0** |

The runner preserved each shard XML under:

- `shard-1-of-4.xml`
- `shard-2-of-4.xml`
- `shard-3-of-4.xml`
- `shard-4-of-4.xml`

and emitted aggregate evidence in `results.tsv`.

## Attached artifacts

- `c150-a302sh-shards.log` (full terminal log)
- `results.tsv`
- `shard-*.xml` (per-shard preserved XML)
- `a302sh-postrun-power.txt`
- `a302sh-end-power.txt`

## Note

The same suite on this physical A302SH target now passes when state is awake/unlocked;
when device is dozing/locked, execution is rejected by `run-connected-ui-shards.sh`
before install (as previously recorded in C103/C148 evidence).
