# C31 Formal Main Rebaseline

## Pinned sources

- Previous Android source: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- New Android source: `main@1cf8aef5d214718a27175ae0e8290e079e66f922`
- Development branch/worktree: `android-dev` / `/Users/kaito/workspace/med-manager_v3-android-worktree`

The complete main delta was merged at one explicit checkpoint. API, iOS, tests, specs/legal and release files were reviewed together; no runtime file was cherry-picked in isolation.

## Semantic findings

1. Patient History adds authenticated `GET /api/patient/history/streak` and a supplementary `連続記録` card. Ordinary history remains usable if the streak request fails.
2. Caregiver Today removes the prior next-action hero/list/top bulk action. Current hierarchy is header, optional missed alert, `今日の服薬状況`, optional PRN, then the four-slot timeline whose eligible rows own recording actions.
3. The backend adds missed-dose caregiver notifications. Remote navigation strictly accepts `DOSE_TAKEN` and `DOSE_MISSED`, with the same validated patient/date/slot destination.
4. Public guide/site, cron operations and release metadata changes introduce no additional Android runtime behavior.

## Conservative disposition

- `PH-009`: `RECHECK_REQUIRED` until C32.
- `CG-008` / `UI-201`: `RECHECK_REQUIRED` until C33.
- `XP-002`: `RECHECK_REQUIRED` until C34.
- Previously passing evidence remains useful for unaffected behavior, but cannot close these changed contracts.

## Gate for this checkpoint

- The source baseline, API contract, parity matrix, screen contract, gap audit and execution backlog all pin the same new main SHA.
- New backend unit/integration tests are run from the merged tree.
- Android JVM/build/lint gates prove the merge did not regress already implemented code; they do not claim C32–C34 complete.

## Results

- Backend focused gate: 4 files / 25 tests passed (`history-streak`, streak route, missed-dose notifications and push send trigger).
- Android `testDebugUnitTest`: passed.
- Android `assembleDebug`: passed.
- Android `lintDebug`: passed.
- The worktree-local SDK path and generated Prisma client are ignored environment artifacts and are not part of the commit.
