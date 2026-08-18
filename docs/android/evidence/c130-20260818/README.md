# C130 physical UI regression rerun evidence

**Date:** 2026-08-18

**Target:** SHARP A302SH (SX3LHMB430113755), Android 15 / API 35, awake and unlocked

**Branch:** `android-dev@b9a4e86`

## Result

A full four-shard physical connected-UI rerun passed on the retained non-Google OEM target with the exact existing runner contract.

| Shard | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| 1/4 | 66 | 0 | 0 | 0 |
| 2/4 | 59 | 0 | 0 | 0 |
| 3/4 | 80 | 0 | 0 | 0 |
| 4/4 | 78 | 0 | 0 | 0 |
| **Total** | **283** | **0** | **0** | **0** |

The runner preserved per-shard XML evidence under `android/app/build/reports/connected-ui-shards/` during execution and removed both app/test packages on exit. A subsequent package check on `SX3LHMB430113755` returned no `com.afterlifearchive.medmanager` package.

## Retained build artifact

`results.tsv` hash:
`20dd5d6f52d8be1ebbc430976eb784e0aae600d5a0867994336651b1ac334047`

