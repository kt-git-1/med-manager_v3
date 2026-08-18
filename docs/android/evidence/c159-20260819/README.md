# C159 Local connected UI regression on emulator (4 shards)

- Date: 2026-08-19
- Device: `emulator-5554` (MedicationApp_API_35)
- Result: PASS
- Command: `scripts/run-connected-ui-shards.sh emulator-5554` with `ANDROID_UI_TEST_SHARDS=4`
- Total tests: 283
- Failures: 0
- Errors: 0
- Skipped: 0

Preconditions confirmed before execution:
- `dumpsys power` and `dumpsys window` captured and passed awake/unlocked checks.

Artifacts:
- `results.tsv`
- `shard-1-of-4.xml`
- `shard-2-of-4.xml`
- `shard-3-of-4.xml`
- `shard-4-of-4.xml`
- `emulator-5554-run3-power.txt`
- `emulator-5554-run3-window.txt`
