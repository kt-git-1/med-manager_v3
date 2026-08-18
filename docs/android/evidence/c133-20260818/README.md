# C133 Physical and emulator connected shard verification — 2026-08-18

- Working branch: `android-dev`
- Commit (before this evidence): `f53b08962ac19add8377391e3d6b6dca0d05827d`
- Published baseline: `iOS 1.0.6 Build 51` / `main@432b34c`
- Working directory: `android/`

## Scope

No app behavior or UI code was changed in this run.  
Objective was to run the same reproducible connected-device shard suite on:

1) emulator (`emulator-5554`) and  
2) physical A302SH (`SX3LHMB430113755`)

to close local C109/C82-style reproducibility evidence on both classes.

## Commands and results

Executed:

1. `./scripts/run-connected-ui-shards.sh emulator-5554`
2. `./scripts/run-connected-ui-shards.sh SX3LHMB430113755`

Both runs execute four shards with `ANDROID_UI_TEST_SHARDS=4`.

| Target | Shard | Result |
|---|---|---|
| emulator-5554 | 1/4 | PASS (`66` tests, `0` failed/skipped) |
| emulator-5554 | 2/4 | PASS (`59` tests, `0` failed/skipped) |
| emulator-5554 | 3/4 | PASS (`80` tests, `0` failed/skipped) |
| emulator-5554 | 4/4 | PASS (`78` tests, `0` failed/skipped) |
| A302SH (`SX3LHMB430113755`) | 1/4 | PASS (`66` tests, `0` failed/skipped) |
| A302SH (`SX3LHMB430113755`) | 2/4 | PASS (`59` tests, `0` failed/skipped) |
| A302SH (`SX3LHMB430113755`) | 3/4 | PASS (`80` tests, `0` failed/skipped) |
| A302SH (`SX3LHMB430113755`) | 4/4 | PASS (`78` tests, `0` failed/skipped) |

- Emulator total: `283` tests, `0` failures/errors/skips
- A302SH total: `283` tests, `0` failures/errors/skips
- Combined total: `566` tests, `0` failures/errors/skips

Notes:

- A302SH initially reported keyguard state. Running `wm dismiss-keyguard` plus the standard wake/launch state checks allowed the runner to proceed.
- Runner preconditions still require an awake+unlocked target before each shard; this state was prepared before invocation.
- If required for audit, keep raw session/build artifacts and screenshots in local CI artifact storage.

## Evidence paths

- `android/app/build/reports/connected-ui-shards/results.tsv`
- `android/app/build/reports/connected-ui-shards/shard-1-of-4.xml`
- `android/app/build/reports/connected-ui-shards/shard-2-of-4.xml`
- `android/app/build/reports/connected-ui-shards/shard-3-of-4.xml`
- `android/app/build/reports/connected-ui-shards/shard-4-of-4.xml`
- `android/app/build/reports/connected-ui-shards/index.html`
