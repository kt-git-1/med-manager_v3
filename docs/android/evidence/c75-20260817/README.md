# C75 Android mutation idempotency evidence

**Date:** 2026-08-17
**Branch:** `android-dev`
**Pinned product reference:** published iOS 1.0.6 Build 51, `main@432b34c`
**Scope:** local API/Android implementation and synthetic regression only

## Result

C75 closes the locally reproducible duplicate-effect gaps found after C74:

- Scheduled individual-dose creation now assigns event, inventory and push-attempt ownership only to the successful natural-key insert. A concurrent unique-key loser returns the winning record without repeating those effects.
- PRN creation and caregiver inventory adjustment accept an optional UUID-v4 `clientMutationId`; nullable storage preserves legacy iOS requests.
- Patient/Caregiver PRN and caregiver refill/correction retain one in-process key after a failed or uncertain response and reuse it for the same logical retry. A changed inventory intent receives a new key.
- Server replay checks suppress duplicate PRN records, inventory deltas and associated downstream work.
- Keys are protocol-only metadata and are excluded from Analytics, UI, notifications and logs.

## Automated evidence

| Gate | Result | Evidence |
|---|---|---|
| API typecheck/lint/format | PASS | TypeScript typecheck, ESLint and Prettier complete with no error |
| Prisma schema | PASS | `DIRECT_URL` placeholder validation accepts the schema and migration shape; no remote database was contacted |
| API test suite | PASS | 75 files, 332/332 tests |
| Android Debug JVM | PASS | 216/216 tests |
| Android Release JVM | PASS | 213/213 tests |
| Android lint | PASS | `lintDebug` completes |
| Android assemblies | PASS | Debug and Release APK assembly complete |
| Play assets | PASS | repository Play-asset verification completes |
| Release APK compatibility | PASS | application/SDK/permission/16-KiB compatibility checks complete; SHA-256 `733bd1fa611cccf7543e02964d2816b5a99e7a4912a088bea5fd81f59ccc10c8` |
| Debug APK | PASS | SHA-256 `062820186f947cf50ee3543046cd6388f2c7ff78afd2e40a794fdcf3764fe672` |
| Physical synthetic UI regression | PASS | A302SH Android 15/API 35, 278/278 tests, 0 failed/skipped; test package removed afterward |
| Production release-security configuration | BLOCKED | Fail-closed check correctly rejects this local shell because production DB/Supabase/FCM secrets are absent |

The physical suite uses synthetic instrumentation fixtures. It performs no production API request, health-data mutation, destructive operation or schema change.

## Contract regression coverage

- UUID-v4 validation, lowercase normalization and legacy omission
- Patient auth 401 refresh/retry retaining the exact PRN request body and key
- Patient and Caregiver manual PRN retry retaining the key after failure
- Caregiver inventory response-loss retry retaining the key for the same intent
- Changed refill/correction intent generating a distinct key
- PRN replay and concurrent unique-insert race returning the accepted record without repeat ownership
- Scheduled-dose concurrent unique-insert race assigning side-effect ownership once
- Inventory replay applying one delta for one key and applying a second delta for a distinct key

## Explicitly open external evidence

- Deploy `api/prisma/migrations/20260817090000_android_mutation_idempotency` through the approved environment pipeline.
- Verify the deployed API with disposable synthetic records and a controlled lost-response/retry sequence.
- Confirm the exact production runtime variables with the release-security gate; values must not be copied into this repository.
- Repeat the mutation-interruption rows on the remaining required physical device classes and exact signed Play artifact.

Until those rows pass, C75 is local implementation evidence only and does not claim production server idempotency or release readiness.
