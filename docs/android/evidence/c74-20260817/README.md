# C74 mutation single-flight and cancellation hardening

**Date:** 2026-08-17

**Branch:** `android-dev`

**Published behavior baseline:** iOS 1.0.6 Build 51

**Scope:** local implementation and synthetic verification only; no production health or destructive mutation

## Contract fixed

The physical matrix requires high-latency/double-tap operations to remain single-flight and an interrupted or uncertain write never to replay automatically. The source audit found that several repositories guarded only their own action type, relied solely on UI-local flags, or retained a busy flag when the calling coroutine was cancelled.

C74 gives each mutation owner one nonblocking `Mutex.tryLock()` boundary:

- Patient individual, bulk, PRN and revoke;
- Caregiver Today individual, delete, bulk and PRN;
- medication save/delete and regimen reconciliation;
- inventory settings/refill/correction;
- patient create, slot times, linking code, revoke/delete and account delete;
- History missed-dose backfill.

An overlapping action returns without contacting its data source. Cancellation is rethrown, clears the matching busy state and releases the boundary; it does not retry, mutate local content or report success. A later attempt is an explicit user/manual retry. For medication save, a confirmed core medication response updates the rendered snapshot and freshness revision before the separately fallible regimen reconciliation.

## Automated evidence

Six high-latency tests hold the first request with `CompletableDeferred`, attempt the same or a cross-type mutation, cancel the first coroutine, and then verify an explicit retry:

| Owner | Duplicate/cross-type proof | Cancellation proof |
|---|---|---|
| Patient | individual blocks bulk | one request only; dose remains retryable |
| Caregiver Today | individual blocks bulk | busy key clears; bulk runs only after explicit retry |
| Medication | save blocks delete | item snapshot is unchanged; delete runs only after explicit retry |
| Inventory | refill blocks correction | quantity is unchanged; correction runs only after explicit retry |
| Patient management | revoke blocks account delete | patient/selection remain; account delete runs only after explicit retry |
| History | backfill blocks duplicate backfill | busy state clears; second server call occurs only after explicit retry |

The existing success/failure/partial-success tests remain green, including successful mutation plus failed reconciliation and server-first destructive failure preservation.

## Gates

| Gate | Result |
|---|---|
| A302SH Android 15/API 35 synthetic Compose suite | PASS — 278/278, 0 failed, 0 skipped, 6m25s |
| Debug JVM | PASS — 212/212 |
| Release JVM | PASS — 209/209 |
| Lint | PASS |
| Debug and unsigned Release APK assembly | PASS |
| Release APK compatibility | PASS — application ID/SDK/permission exclusions and 16 KB ZIP/ELF alignment |
| Play Store assets | PASS |
| Unsigned Release APK SHA-256 | `64feafa85875af2abd9a1ae8802680c632aca775c1355cc9cd1eb2871877333f` |

The physical UI run used only instrumentation-owned synthetic fixtures. It did not authenticate, record a dose, change inventory, create/delete a patient, issue a live code, or delete an account. The instrumentation package was absent after the run.

## Remaining external evidence

C74 does not claim that a real request interrupted after server commit can be classified locally, and it does not prove endpoint idempotency. Those cases require disposable server records, controlled network interruption, before/after authoritative state and the exact physical/Play artifact procedure in `physical-device-matrix.md`. They remain open together with signed Play, other device classes, Firebase/FCM and spoken TalkBack.
