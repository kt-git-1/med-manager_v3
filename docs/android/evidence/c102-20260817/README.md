# C102 Node 22 API runtime alignment — 2026-08-17

## Result

C102 removes the API build/runtime major-version split, aligns supported Prisma/Firebase dependencies and reduces the remaining dependency audit without touching production. No production database, Vercel deployment, Firebase delivery, Console state, user/patient data or connected device was read or changed.

Implementation commit: `e127320fac15ea9dfa2a7fcc9a11d8940060050b`.

## Why the runtime changed

- The checked-in API engine was `>=20`, which Vercel maps to its latest supported Node major rather than 20.
- API CI, API E2E and the C100 production workflow explicitly selected Node 20.
- Node 20 is EOL, while the current Prisma streams helper and Firebase Admin 14 require Node 22 or later.

C102 selects `22.x`, a Vercel-supported major, in `api/package.json` and exactly five setup entries across API CI, API E2E and the production workflow. This makes local/hosted build, tests and deployed functions use the same major.

Authoritative references:

- [Vercel supported Node.js versions](https://vercel.com/docs/functions/runtimes/node-js/node-js-versions)
- [Node.js 22 LTS support statement](https://nodejs.org/en/blog/release/v22.20.0)
- [Firebase Admin Node.js v14 release notes](https://firebase.google.com/support/release-notes/admin/node#version_1400_-_08_june_2026)

## Dependency and source alignment

| Boundary | C101 | C102 |
|---|---:|---:|
| Node engine / workflow setups | `>=20` / 20 | `22.x` / 22 |
| Prisma CLI/client/adapter | mixed 7.8.0/7.3.0 | 7.9.1 |
| `@types/node` | 25.1.0 | 22.20.1 |
| Firebase Admin | 13.10.0 | 14.2.0 |
| Complete audit | 13 Moderate | 6 Moderate |

The API already used only Firebase's supported modular entry points (`firebase-admin/app`, `firebase-admin/messaging`) and current `getMessaging(app).send(message)`, so the removed legacy namespace/Instance ID/legacy messaging APIs were absent. Node 22's stricter crypto types exposed two ambient Web-versus-Node JWK casts; `JwkKey` now explicitly extends `crypto.JsonWebKey` and both key construction paths typecheck without changing validation or signature behavior.

The lock changes 142 resolved entries. That larger mechanical count is the reviewed dependency closure for synchronized Prisma CLI/client/adapter/Studio/engine packages, Firebase Admin 14 and their optional platform/helper packages; declared application dependencies change only the three Prisma ranges, Node types and Firebase Admin baseline described above.

The remaining six Moderate findings all reduce to the Firebase Admin optional Cloud Storage chain ending in an older uuid dependency. npm offers only incompatible major graph changes; no forced `overrides`, downgrade or false zero-vulnerability claim is used.

## Fail-closed contract

`verify-api-node-runtime.mjs` requires:

1. exact `22.x` package and root-lock engine;
2. declared/resolved Prisma 7.9.1 CLI/client/adapter and its Node-22 streams helper;
3. matching `@types/node` 22.20.1 and Firebase Admin 14.2.0;
4. exactly three API CI, one API E2E and one production Node setup, all major 22;
5. all three workflow files present with no extra or missing setup.

The synthetic contract accepts one exact state and rejects nineteen engine, declared package, resolved lock, helper, type, Firebase, workflow-count/major and cross-trigger drifts. C100 also rejects a Node-20 production workflow, bringing its release-policy fixtures to 25 rejected cases.

## Local verification

- Runtime contract: 1 accepted / 19 rejected; summary `node=22.x prisma=7.9.1 firebaseAdmin=14.2.0 workflows=3 setups=5`.
- C100 production release contract: 3 accepted / 25 rejected.
- Node 22.23.2 clean install: no engine warning.
- Complete dependency audit: 0 High / 0 Critical / 6 Moderate.
- C97 isolated legacy upgrade: 1 accepted / 11 rejected; six rows retained.
- C98 deployment audit: 2 accepted / 12 rejected; disposable PostgreSQL 16 postdeploy passed with zero exposed values.
- API tests: 75 files / 333 tests passed, including Firebase Messaging and Supabase JWT coverage.
- Prettier, ESLint, TypeScript and Next.js 16.3.1 production build: passed.
- Release-gate ledger remains 10 total / 3 ready / 7 blocked / 0 verified / 6 PARTIAL requirements.

The committed merge surface passes with base `432b34c064d70a59c20753116b39390bee2c1cd0`, 210 commits, 1,189 files, 386,127,304 bytes and scopes `.github=4`, `.gitignore=1`, `android=182`, `api=42`, `docs/android=960`, `ios=0`.

## Hosted CI

- API CI: [run 31988521203](https://github.com/kt-git-1/med-manager_v3/actions/runs/31988521203) — all three Node 22 jobs and 38/38 steps passed, including runtime/audit/migration/postdeploy/333-test/build gates.
- API E2E: [run 31988521188](https://github.com/kt-git-1/med-manager_v3/actions/runs/31988521188) — Node 22 build/server/Playwright execution passed; 15 steps succeeded and two failure-only uploads were skipped.
- Android CI: [run 31988521225](https://github.com/kt-git-1/med-manager_v3/actions/runs/31988521225) — 29/29 steps passed, including C96/C99-C102 contracts, both JVM variants, Lint, APK/AAB compatibility, universal/device-split surfaces and Play assets.

## Residual boundary

- C102 is local/hosted implementation evidence, not proof of the Vercel production runtime until RG-002 performs an authorized deployment and health/App Links checks.
- The six Moderate Firebase Storage/uuid findings remain explicit and must be revisited when a compatible upstream resolution is available.
- The protected `android-api-production` environment and named values remain absent; the production workflow was not dispatched.
