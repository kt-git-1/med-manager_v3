# C99 canonical residual release gates — 2026-08-17

## Result

C99 replaces scattered narrative-only residual tracking with one machine-readable ledger at `docs/android/release-gates.json`. It does not complete, waive or authorize any external gate.

Implementation commit: `efc51f2b951c6fe8cc2abfab40eea4b4c6c376ae`.

## Audit correction

The early unchecked `SH-007/SH-009` umbrella no longer reflected current evidence:

- C67 already proves same-installation Caregiver/Patient restoration plus fresh Debug reinstall non-restoration on the available A302SH;
- C88 fail-closes Release backup/data-transfer resources and `allowBackup`;
- exact Play reinstall, OEM transfer and the two remaining device classes are still external and now live explicitly under `RG-006/RG-007`.

The obsolete umbrella is checked only for its completed A302SH/Release scope. No Play/OEM/cross-device result is inferred.

## Canonical ledger

The ten ordered gates are:

1. `RG-001` Firebase Analytics Explore verification;
2. `RG-002` production API migration and deploy;
3. `RG-003` real mutation interruption recovery;
4. `RG-004` production App Links and Android FCM;
5. `RG-005` release-owner signing and exact AAB handoff;
6. `RG-006` Play Internal install and update verification;
7. `RG-007` old-supported and Google-reference physical devices;
8. `RG-008` assisted spoken TalkBack traversal;
9. `RG-009` Play Console Data safety and Health declarations;
10. `RG-010` closed test, final rebaseline and main merge.

`RG-001`, `RG-002` and `RG-005` have no unmet ledger dependency and are `READY_FOR_OWNER_ACTION`. The other seven are `BLOCKED_BY_DEPENDENCIES`. All ten backlog rows remain unchecked and zero gates are `VERIFIED`.

Together, unresolved gates cover exactly `XP-004`, `XP-005`, `XP-006`, `XP-008`, `XP-009` and `XP-010`, which are the six and only six current `PARTIAL` parity rows.

## Fail-closed verification

`android/scripts/verify-release-gates.py` rejects:

- noncanonical JSON or unknown/missing schema fields;
- published main, iOS release, branch or current-checkpoint drift;
- noncontiguous gate identity/order;
- missing/extra PARTIAL requirement coverage;
- forward/missing dependencies or status inconsistent with dependency state;
- unknown authority/owner, duplicate values or incomplete C prerequisites;
- missing/out-of-scope evidence paths;
- title/checkbox drift between JSON and backlog;
- reintroduction of the stale unchecked SH-007/SH-009 row.

A gate can later move to `VERIFIED` only after every dependency is verified and the corresponding backlog row is checked in the same reviewed change. Unresolved coverage must still exactly equal the then-current PARTIAL rows.

## Local regression

- C99 synthetic contract: 1 accepted / 15 rejected.
- Current C99 ledger: 10 gates, 3 ready, 7 dependency-blocked, 0 verified, 6 PARTIAL requirements.
- Gradle `verifyReleaseGates`: passed both contract and current-ledger tasks.
- Debug JVM: 216/216, zero failed/skipped.
- Release JVM: 213/213, zero failed/skipped.
- Lint Debug: passed.
- Release APK compatibility: package/API 26/35, six permissions, three exports, two auth links, advertising exclusion and 16 KB ZIP/native alignment passed.
- C96 pure Git contract: 1 accepted / 10 rejected.
- Android CI workflow YAML, Python compilation and `git diff --check`: passed.

No production service, Console, key, account, patient, health record or connected device was read or changed by C99.

## Merge isolation

The committed implementation recheck passed with:

- base: `432b34c064d70a59c20753116b39390bee2c1cd0`;
- head: `efc51f2b951c6fe8cc2abfab40eea4b4c6c376ae`;
- 205 commits, 1,175 changed files and 385,780,777 changed-tree bytes;
- scopes: `.github=2`, `.gitignore=1`, `android=182`, `api=34`, `docs/android=956`, `ios=0`.

The existing unstaged user `.gitignore` edit is excluded from committed counts and was neither staged nor modified.

## Hosted CI

- Android CI: [run 31985101618](https://github.com/kt-git-1/med-manager_v3/actions/runs/31985101618) — 29/29 steps passed. The new Residual release gates step passed before Firebase/runtime/signing policies, both JVM variants, Lint, APK/AAB compatibility, universal/device-split surfaces and Play assets.

## Residual boundary

The next actions must follow `RG-001`–`RG-010`. A ledger state or green local/hosted test is not acceptance evidence for Firebase/production/Play/physical/TalkBack/Console/merge work. Each gate remains unchecked until its own `doneWhen` evidence is actually recorded.
