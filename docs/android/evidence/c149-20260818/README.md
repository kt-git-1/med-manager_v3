# C149 Emulator connected UI shard evidence

**Date:** 2026-08-18  
**Branch:** `android-dev`  
**Device:** `emulator-5554` (SDK 35)

## Scope

- Re-run the connected UI suite in `ANDROID_UI_TEST_SHARDS=4` mode after C148 evidence work.
- Keep this run as an explicit baseline for emulator reliability while the A302SH physical target remains locked/dozing.

## Command

```bash
cd android
ANDROID_UI_TEST_SHARDS=4 ./scripts/run-connected-ui-shards.sh emulator-5554
```

## Result

All 4 shards passed, zero failures/errors/skips:

| Shard | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| 1/4 | 66 | 0 | 0 | 0 |
| 2/4 | 59 | 0 | 0 | 0 |
| 3/4 | 80 | 0 | 0 | 0 |
| 4/4 | 78 | 0 | 0 | 0 |
| **Total** | **283** | **0** | **0** | **0** |

`run-connected-ui-shards.sh` preserved per-shard XML output and the aggregate `results.tsv`.

## Attached evidence

- `c149-emulator-shards.log`
- `results.tsv`

## Note

- This preserves an emulator parity sweep in the current state.
- Physical A302SH still requires explicit awake+unlock for execution (and is currently keyguard/restricted), so the physical comparison remains environment-bound for now.
