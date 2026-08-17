# C104 verified production database TLS — 2026-08-17

**Source baseline:** published iOS 1.0.6 Build 51 at `main@432b34c`

**Android/API source before C104:** `android-dev@91b50fd320633d0368f3a12b65101e00911d1934`

**Release boundary:** `RG-002` remains unchecked; no production write, migration, deploy, secret change or database setting change occurred

## Finding

The retained production database URLs did not contain an SSL mode. The prior C98 remote guard rejected them before connecting. A temporary `sslmode=require` compatibility attempt was insufficient as a durable contract: current Node PostgreSQL parsing can map SSL modes differently depending on compatibility mode, and provider certificate-chain validation requires the Supabase root. The accepted security target is therefore exact `verify-full`, which verifies both the trusted CA chain and the requested database hostname.

A read-only production inspection also confirmed that incoming SSL enforcement is currently OFF. Enabling it restarts the database. C104 deliberately did not change that setting; the release owner must schedule it under RG-002 and rerun count-only preflight afterward.

## Implemented boundary

- API runtime recognizes only loopback or reviewed Supabase database hosts in production.
- Supabase runtime URLs lose all connection-string SSL overrides and receive the pinned Supabase Root 2021 CA through `pg` with `rejectUnauthorized=true`.
- The pinned certificate is a CA, self-issued as Supabase Root 2021 CA, valid from 2021-04-28 through 2031-04-26, with canonical DER SHA-256 `807025ad50d4ed219d2c9c7d299c004f824eb00cf7f65afef607d07b72e6cafa`.
- The manual production workflow downloads only the exact official CA URL over HTTPS with TLS 1.2 or newer, validates the CA/fingerprint/validity window, and prepares a masked URL with `sslmode=verify-full` plus an absolute `sslrootcert` path.
- The raw database secret is consumed by only that preparation step. Audit, Prisma and build steps receive the prepared environment value; always-run cleanup removes the ephemeral CA.
- The C98 production audit rejects `require`, missing or relative root paths and `uselibpqcompat` on remote targets.
- C96 expands only to the five reviewed C104 API paths, for a current total of four workflows and 47 API files.

## Privacy-safe live evidence

The official CA was downloaded to temporary storage, inspected and deleted. Exact `verify-full` C98 preflight passed in one read-only transaction and emitted anonymous counts only:

```text
prnRows=38 prnKeyed=0 inventoryRows=249 inventoryKeyed=0 valuesExposed=0 readOnly=passed
```

A separate Node connection using the pinned runtime policy proved:

```text
encrypted=true authorized=true protocol=TLSv1.3 cipher=TLS_AES_256_GCM_SHA384 rowsRead=0 valuesExposed=0
```

The test did not select identifiers, health/free-text fields or secret values. Temporary certificate files were removed after each check.

## Local contract evidence

- production URL/CA preparation: 1 accepted / 9 rejected; GitHub environment export and zero-value disclosure passed;
- runtime database TLS policy: 9/9 passed;
- production release workflow: 3 accepted / 29 rejected;
- mutation deployment audit: 2 accepted / 15 rejected, including exact remote TLS-policy rejections;
- isolated legacy upgrade: four migration statements, six legacy rows and two exact indexes passed with cleanup;
- complete PostgreSQL-backed API suite: 76 files / 342 tests passed from an empty disposable PostgreSQL 16 database;
- dependency audit: zero High/Critical; six known Moderate transitive findings remain;
- format, lint, typecheck and production build: passed;
- workflow YAML parse and Android release-gate/main-merge-surface contracts: passed.

## Residual owner action

RG-002 remains open until all of the following are true:

1. the release owner enables incoming SSL enforcement in an approved database restart window;
2. the default protected workflow preflight succeeds afterward with exact verify-full/count-only evidence;
3. an independently approved write window executes the migration and API deployment once;
4. postdeploy audit, API health and Android API/App Links smokes pass.

This checkpoint supplies transport controls and read-only evidence only. It does not authorize any production mutation or deployment.
