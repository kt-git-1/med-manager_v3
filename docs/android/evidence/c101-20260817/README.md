# C101 production dependency audit gate — 2026-08-17

## Result

C101 removes the High/Critical production dependency blocker discovered while validating C100 and makes that severity boundary fail closed before production database access. It does not deploy, read production, change Console configuration or complete RG-002.

Implementation commit: `fca55a68af9f33a1d7918ccb78964ba8bc20204d`.

## Bounded remediation

The pre-change complete `npm audit --json` reported 7 High, 13 Moderate and 0 Critical findings: five runtime-chain findings plus brace-expansion and js-yaml in build tooling. Temporary-directory lock-only trials proved all High findings could be closed without changing declared dependency ranges or application source. The committed `package-lock.json` refresh is bounded to the affected resolved chains:

| Package | Before | After |
|---|---:|---:|
| Next.js | 16.2.10 | 16.3.1 |
| fast-uri | 3.1.3 | 3.1.5 |
| nanoid | 3.3.15 | 3.3.18 |
| postcss | 8.4.31 | 8.5.23 |
| sharp | 0.34.5 | 0.35.3 |
| brace-expansion | 1.1.15 | 1.1.18 |
| js-yaml | 4.3.0 | 4.3.1 |

The lock changes 47 resolved entries, including expected Next SWC and sharp platform packages plus their helper graph. The post-change complete audit reports 0 High, 0 Critical and 13 Moderate. The remaining Moderate advisories are transitive through Prisma development tooling and Firebase Admin's Google Cloud dependencies. npm proposes a breaking Firebase Admin downgrade for part of that graph; C101 does not silently take that incompatible direction or claim zero vulnerability.

## Continuous gate

Both API CI and `.github/workflows/android-api-production-release.yml` now run:

```bash
npm audit --audit-level=high
```

In the production workflow it runs immediately after `npm ci`, before the production database secret-presence step or any C98 query. The C100 workflow contract requires the exact audit command and rejects its removal. Registry/audit failure also stops safely.

## Verification

- Complete locked dependency audit: 0 High / 0 Critical; 13 Moderate recorded.
- C100 production workflow contract: 3 accepted / 24 rejected.
- API tests: 75 files / 333 tests passed.
- C97 isolated upgrade: 1 accepted / 11 rejected; six legacy rows retained.
- C98 deployment audit: 2 accepted / 12 rejected; disposable PostgreSQL postdeploy passed with values exposed = 0.
- Prettier, ESLint, TypeScript and Next.js 16.3.1 production build: passed.
- Release-gate ledger: 10 gates, 3 ready, 7 dependency-blocked, 0 verified, 6 PARTIAL requirements.
- C96 allowlist: exactly three workflows and 38 API files; iOS remains excluded.

The committed surface passed with base `432b34c064d70a59c20753116b39390bee2c1cd0`, 208 commits, 1,183 files, 386,064,015 bytes and scopes `.github=3`, `.gitignore=1`, `android=182`, `api=38`, `docs/android=959`, `ios=0`.

## Hosted CI

- API CI: [run 31987147273](https://github.com/kt-git-1/med-manager_v3/actions/runs/31987147273) — all three jobs and 37/37 steps passed, including the complete dependency audit, C97/C98/C100 contracts, migration/postdeploy, 333 API tests and Next.js build.
- API E2E: [run 31987147278](https://github.com/kt-git-1/med-manager_v3/actions/runs/31987147278) — 15 execution/cleanup steps passed; two failure-only artifact steps were correctly skipped.
- Android CI: [run 31987147267](https://github.com/kt-git-1/med-manager_v3/actions/runs/31987147267) — 29/29 steps passed, including C96/C99-C101 ledgers, both JVM variants, Lint, APK/AAB compatibility, universal/device-split surfaces and Play assets.

## Residual boundary

- Re-evaluate and update the 13 Moderate advisories through compatible direct/transitive releases; do not mask them with a severity claim stronger than the command proves.
- The existing Prisma package emits a Node 22 engine advisory for an internal development subpackage while the project/CI currently target Node 20; all C97/C98/Prisma commands pass, but runtime/toolchain alignment requires a separate reviewed upgrade instead of an opportunistic engine change in this lock-only slice.
- RG-002 still requires the absent protected environment, named production inputs, explicit owner review and real preflight/deploy evidence.
