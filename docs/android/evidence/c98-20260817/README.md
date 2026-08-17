# C98 read-only mutation deployment audit — 2026-08-17

## Result

C98 makes the production observation boundary around `20260817090000_android_mutation_idempotency` executable without performing a migration. It does not connect to production in this slice, authorize deployment, or prove real interrupted-request recovery.

Implementation commit: `057b4bd11ba07840e81c153847fe1947e8df1414`.

## Safety boundary

`api/scripts/verify-android-mutation-deployment.mjs` requires:

1. exactly one `--mode preflight|postdeploy` argument;
2. `ANDROID_MUTATION_DEPLOYMENT_AUDIT` to exactly match that mode;
3. `ALLOW_REMOTE_ANDROID_MUTATION_AUDIT=1` for every non-loopback host;
4. `sslmode=require` or `verify-full` and the `public` schema for remote execution;
5. a database-enforced `BEGIN READ ONLY` transaction with 10-second statement and 2-second lock timeouts;
6. only `SELECT`/`SHOW` data statements after the transaction controls;
7. count-only output with no database URL, host, record identifier, mutation identifier or health value.

This list records the historical C98 contract. C104 supersedes item 4 for every remote execution: only exact `sslmode=verify-full` with an absolute `sslrootcert` path is now accepted, and `sslmode=require`/compatibility mode are rejected. See `evidence/c104-20260817/README.md` for the pinned-CA runtime/workflow boundary and live read-only verification.

Unexpected database errors are reduced to a safe generic message plus an optional SQLSTATE. The direct database URL remains an operator secret and must not be written to shell history, chat, Git or evidence.

## State contracts

`preflight` fails unless:

- the Prisma migration table and both legacy target tables exist;
- the target migration has no Prisma record;
- neither `clientMutationId` column exists;
- neither target unique index exists.

It returns only total rows for both tables so the release owner can assess the index-build window.

`postdeploy` fails unless:

- exactly one target Prisma record is finished, not rolled back and records one applied step;
- its SHA-256 checksum matches the exact checked-in migration bytes;
- both columns remain nullable, default-free `TEXT`;
- both indexes are unique, valid, ready, non-partial, non-expression and ordered `patientId,clientMutationId`;
- aggregate duplicate patient/mutation-key group counts are zero.

It returns only total and non-null-key counts for both tables.

## Local contract verification

- Policy/database fixtures: 2 accepted / 12 rejected; every temporary schema removed.
- Accepted fixtures: exact preflight state and exact postdeploy state with one non-null key.
- Rejected policy fixtures: invalid mode, mismatched confirmation, invalid schema, missing remote opt-in, missing remote TLS and non-public remote schema.
- Rejected database fixtures: preflight already-recorded/partial states and postdeploy missing record, checksum drift, rollback and missing-index states.
- Disposable PostgreSQL 16.11 full Prisma deploy: all migrations applied and C98 postdeploy passed with zero exposed values.
- C97 static migration contract: 1 accepted / 11 rejected.
- API tests: 75 files, 333/333 tests passed.
- Prettier, ESLint, TypeScript and Next.js production build: passed.
- C96 pure Git contract: 1 accepted / 10 rejected.
- API CI workflow YAML and `git diff --check`: passed.

## Merge isolation

The C96 API allowlist is deliberately expanded by only the two C98 verifier files. The committed implementation recheck passed with:

- base: `432b34c064d70a59c20753116b39390bee2c1cd0`;
- head: `057b4bd11ba07840e81c153847fe1947e8df1414`;
- 203 commits, 1,171 changed files and 385,742,410 changed-tree bytes;
- scopes: `.github=2`, `.gitignore=1`, `android=180`, `api=34`, `docs/android=954`, `ios=0`.

The existing unstaged user `.gitignore` edit is excluded from the committed counts and was neither staged nor modified.

## Hosted CI

- API CI: [run 31983959090](https://github.com/kt-git-1/med-manager_v3/actions/runs/31983959090) — all three jobs and 35/35 steps passed. The Tests job passed C97 upgrade, C98 two-mode fixtures, normal migrations, C98 postdeploy and API 333/333 in that order.
- API E2E: [run 31983959210](https://github.com/kt-git-1/med-manager_v3/actions/runs/31983959210) — completed successfully; 15 execution/cleanup steps passed and two failure-only report uploads were correctly skipped.
- Android CI: [run 31983959067](https://github.com/kt-git-1/med-manager_v3/actions/runs/31983959067) — 28/28 steps passed, including C96 ancestry/surface, both JVM variants, Lint, APK/AAB compatibility, universal/device-split surfaces and Play assets.

## Residual release boundary

- Obtain explicit release authority and an approved secret-handling path before any production URL is supplied.
- Run C98 preflight, separately execute the controlled production migration, then run C98 postdeploy and retain only anonymous output.
- Verify API replay and ambiguous-response recovery with approved dedicated test identities.
- Keep production App Links/FCM, signed Play, remaining physical devices, spoken TalkBack, Console declarations and final merge approval as separate gates.
