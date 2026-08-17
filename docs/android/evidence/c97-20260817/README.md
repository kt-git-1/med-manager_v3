# C97 Android mutation migration upgrade gate — 2026-08-17

## Result

C97 makes the pre-production upgrade semantics of `20260817090000_android_mutation_idempotency` executable. It does not connect to or migrate production, prove a real interrupted request, or authorize the eventual branch merge/deploy.

Implementation commit: `4a604c2251dbdc8ece5d29039e560adf1a28cd68`.

## Enforced migration contract

`api/scripts/verify-android-mutation-migration.mjs`:

1. accepts exactly four reviewed SQL statements in order: two nullable/default-free `TEXT` additions followed by two patient-scoped unique indexes;
2. refuses non-local database hosts unless an explicit exceptional override is present;
3. creates a random isolated schema and exact pre-migration PRN/inventory-adjustment table fixture;
4. seeds three legacy rows in each table and applies the real migration file statement by statement;
5. proves both columns' type/nullability/default and both indexes' uniqueness plus `patientId,clientMutationId` order;
6. proves all six legacy identities survive with null mutation identifiers;
7. proves repeated null identifiers remain valid for legacy requests and the same UUID remains valid for different patients;
8. proves a repeated same-patient UUID fails with SQLSTATE `23505` on the intended constraint for both mutation families;
9. rolls back mutation probes and removes the temporary schema even when verification fails.

The static contract accepts the checked-in migration and rejects missing/extra/destructive statements, required/defaulted/wrong-type columns, non-unique/global/reordered indexes, duplicate SQL and a remote database target.

## CI integration and merge isolation

API CI runs the static contract and isolated legacy upgrade before normal `prisma migrate deploy`, so a fresh-schema pass cannot hide an unsafe upgrade. The test uses only the workflow's disposable local PostgreSQL 16 service.

C96 is intentionally expanded from its historical one-workflow/30-API-file surface to exactly two reviewed workflows and 32 API files. Its pure Git fixture remains one accepted and ten rejected repositories. The committed implementation recheck passed with:

- base: `432b34c064d70a59c20753116b39390bee2c1cd0`;
- head: `4a604c2251dbdc8ece5d29039e560adf1a28cd68`;
- 201 commits, 1,168 changed files and 385,713,551 changed-tree bytes;
- scopes: `.github=2`, `.gitignore=1`, `android=180`, `api=32`, `docs/android=953`, `ios=0`.

The existing unstaged user `.gitignore` edit is intentionally excluded from all committed counts and was neither staged nor modified.

## Local regression

- Static migration contract: 1 accepted / 11 rejected.
- Disposable PostgreSQL 16.11 legacy upgrade: four statements, six legacy rows and two unique indexes passed; temporary-schema count returned zero.
- API tests: 75 files, 333/333 tests passed.
- Prettier, ESLint and TypeScript: passed.
- Next.js production build with CI-equivalent dummy database configuration: passed.
- C96 pure Git contract: 1 accepted / 10 rejected.
- `git diff --check`: passed.

No production database, health record, user identifier, runtime secret, signing key, APK or AAB was read or written by this slice.

## Hosted CI

- API CI: [run 31982946476](https://github.com/kt-git-1/med-manager_v3/actions/runs/31982946476) — all three jobs and 33/33 workflow steps passed, including the C97 static/legacy-upgrade contract before normal migrations, API 333/333, formatting, lint, typecheck and production build.
- API E2E: [run 31982946480](https://github.com/kt-git-1/med-manager_v3/actions/runs/31982946480) — completed successfully; 15 execution/cleanup steps passed and the two failure-only report uploads were correctly skipped.
- Android CI: [run 31982946481](https://github.com/kt-git-1/med-manager_v3/actions/runs/31982946481) — 28/28 workflow steps passed, including C96 ancestry/surface, both JVM variants, Lint, APK/AAB compatibility, universal/device-split surfaces and Play assets.

## Residual release boundary

- Inspect production migration status and anonymous row/index conditions without copying identifiers or health data.
- Choose the deployment window for unique-index creation, deploy through the normal controlled API release, then verify final migration status.
- Execute API replay and real uncertain-response recovery on approved test identities only.
- Keep signed Play, physical-device, production App Links/FCM and final merge approval as separate gates.
