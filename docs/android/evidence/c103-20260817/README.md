# C103 physical UI runner readiness — 2026-08-17

**Source baseline:** published iOS 1.0.6 Build 51 at `main@432b34c`
**Android source before C103:** `android-dev@91b50fd320633d0368f3a12b65101e00911d1934`
**Device:** retained SHARP A302SH, Android 15 / API 35, adb-authorized disposable target
**Parity boundary:** `XP-008` remains `PARTIAL`

**Implementation commit:** `ca78acb8c68dd4a0bcfadb148cbec07a0f097d9e`

## Finding

The C82 runner accepted an authorized target whose display had entered Doze and whose keyguard was active. The first shard then emitted four immediate `No compose hierarchies found in the app` failures out of 66 tests. The run was stopped rather than turning one invalid precondition into a broad regression result.

Read-only device inspection after stopping reported `mWakefulness=Dozing`, `mInputRestricted=true`, `isKeyguardShowing=true`, no focused app and no retained app/test package. Those conditions explain the missing activity hierarchy; they do not prove an application defect. The runner's existing exit trap removed both packages after interruption.

## Fix

`scripts/run-connected-ui-shards.sh` now:

- requires the selected target to report an awake state before any package inspection or installation;
- rejects an active dreaming/showing keyguard or input restriction;
- rejects the target when no recognized unlock-state signal can be read;
- repeats the same check immediately before every shard;
- never wakes, unlocks or changes power/lock policy on the user's behalf;
- retains refusal to overwrite an existing package and cleanup on success, failure, SIGINT and SIGTERM.

The hosted Android workflow executes a synthetic runner contract. It uses no emulator, device, account, credential or application data and proves:

| Fixture | Expected | Result |
|---|---|---|
| Awake and unlocked | One shard runs and both packages are cleaned | PASS |
| Dozing | Reject with status 2 before Gradle | PASS |
| Awake but keyguard locked | Reject with status 2 before Gradle | PASS |
| Unlock state unavailable | Reject with status 2 before Gradle | PASS |
| Shard returns status 17 | Preserve status 17 and clean both packages | PASS |

Local contract output:

```text
UI shard runner contract passed: awake=1 dozingRejected=1 lockedRejected=1 unknownRejected=1 failureCleanup=1
```

The real A302SH then fails fast with the exact awake/unlock guidance instead of installing or running tests while locked. The synthetic contract and hosted CI close the C103 runner behavior; a fresh unlocked real-device 4-shard result remains V01 physical evidence and is run only after the user manually unlocks the target. This evidence improves physical-result validity only; it does not close spoken TalkBack, old-supported/reference-device, FCM, Play signing/install or other external release rows.

## Hosted CI

[Android CI run 31991432672](https://github.com/kt-git-1/med-manager_v3/actions/runs/31991432672) passed 29/29 steps on the exact implementation commit. The connected-shard-runner contract passed before both JVM variants, Debug build, Lint, APK/AAB, universal/device-split and Play asset gates.
