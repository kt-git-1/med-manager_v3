# C61 published iOS 1.0.6 rebaseline

**Date:** 2026-08-15

**Reference:** published iOS 1.0.6 Build 51, `main@432b34c`

**Android branch:** `android-dev`

**Baseline merge:** `36a6d4d`

## Completed checkpoints

- `36a6d4d`: merged the complete published main source into Android without editing the parallel iOS worktree.
- `89ecbfa`: added `takenAt` wire/domain mapping, the Tokyo recording policy and next-slot late-dose selection contract.
- `3e724c3`: matched Patient Today progress, actual-time, late-dose, next-action and post-record scroll behavior.
- `e849a42`: exposed scheduled/actual/delay/late information in Patient History.
- `972b511`: added Caregiver Today late alert, recorder information and actual-time timeline state.

## Verification

| Gate | Result |
|---|---|
| API after merge | 71 files / 322 tests passed; TypeScript typecheck passed |
| Android JVM | `:app:testDebugUnitTest` passed |
| Android lint | `:app:lintDebug` passed |
| Patient Today API 35 | 27/27 Compose tests passed |
| Patient History API 35 | 19/19 Compose tests passed |
| Caregiver Today API 35 | 20/20 Compose tests passed |

All fixtures use synthetic patients, medication names and timestamps. No identity, token or medical production data is stored in this evidence.

## Remaining C61 work

1. Strict slot-order validation in Android form/domain UI.
2. Published guided medication form and inventory calculator hierarchy.
3. Redesigned caregiver inventory editing.
4. Caregiver push patient selection before exact routing.
5. Full API/JVM/lint/build and API 26/33/35 regression, followed by updated visual evidence.
