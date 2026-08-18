# C132 Connected UI shard validation and release-gate status — 2026-08-18

- Working branch: `android-dev`
- Commit: `e65f54104d9354c694a493e7d26cffc28b73780f`
- Published baseline: `iOS 1.0.6 Build 51` / `main@432b34c`
- Working directory: `android/`

## Scope

No app behavior or UI code was changed in this run.  
This check revalidates local release-gate readiness and executes the reproducible connected-device UI shard runner on an emulator target.

## Commands and results

Executed:

1. `./gradlew verifyReleaseGates`
2. `./scripts/run-connected-ui-shards.sh emulator-5554` (4 shards total, disposable)

| Check | Result |
|---|---|
| `verifyReleaseGates` | PASS |
| `connected` shard 1/4 (`shard-1-of-4.xml`) | PASS |
| `connected` shard 2/4 (`shard-2-of-4.xml`) | PASS |
| `connected` shard 3/4 (`shard-3-of-4.xml`) | PASS |
| `connected` shard 4/4 (`shard-4-of-4.xml`) | PASS |
| Total connected UI tests in this run | PASS (`283` tests, `0` failed/skipped/error) |

Notes:

- `verifyReleaseGates` remains a local status gate (`gates=10 ready=3 blocked=7 verified=0 partialRequirements=6`) with owner actions still required for external residuals.
- `run-connected-ui-shards.sh` is a disposable-target runner and removes both app/test packages on every shard exit.
- A302SH was detected but keyguard remained active (`isKeyguardShowing=true`), so the script aborted its execution earlier; emulator-only shard evidence is retained in `app/build/reports/connected-ui-shards`.
- Previous one-shot shard-4 timeout was transient and not reproducible in this full rerun; therefore we kept this complete run and retained this evidence as the valid acceptance snapshot for emulator-connected checks.

## Evidence paths

- `android/app/build/reports/connected-ui-shards/results.tsv`
- `android/app/build/reports/connected-ui-shards/shard-1-of-4.xml`
- `android/app/build/reports/connected-ui-shards/shard-2-of-4.xml`
- `android/app/build/reports/connected-ui-shards/shard-3-of-4.xml`
- `android/app/build/reports/connected-ui-shards/shard-4-of-4.xml`
- `android/app/build/reports/connected-ui-shards/index.html`
