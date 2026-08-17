# C100 fail-closed production API release control — 2026-08-17

## Result

C100 creates an enforceable RG-002 execution path without executing it. Production database, Vercel, Firebase, Play Console, user/patient data and connected devices were not read or changed.

Implementation commit: `09f9ebe5cc10606e7fec22b57f96d7bd4f2e3c13`.

## Pre-implementation live configuration audit

A name-only GitHub CLI audit found no `android-api-production` environment. Existing generated Preview/Production environment names had no configured protection rules. Repository variables were empty and the only repository secret names were the four already-scoped Firebase Android CI values; none of the required production database/Vercel/release-arm names existed. No values were read or printed. Therefore the new workflow is intentionally non-runnable for production until an authorized release owner creates and protects the environment.

`api/vercel.json` previously disabled Git auto-deployment only for `android-dev`; `main` could deploy without a migration step. C100 disables it for both branches so the controlled workflow owns the order after the configuration reaches `main`.

## Enforced dispatch and operation contract

`.github/workflows/android-api-production-release.yml` has `workflow_dispatch` only and defaults to `preflight`. It requires:

1. the exact lowercase full SHA supplied by the operator, GitHub's checked-out SHA and freshly fetched `origin/main` to match;
2. `refs/heads/main`, a clean checkout and locked dependencies/source checks;
3. exact mode-specific confirmation text;
4. for writes, both `ANDROID_API_PRODUCTION_RELEASE_ENABLED=true` and `ANDROID_API_PRODUCTION_REVIEWERS_CONFIGURED=true` plus the protected environment;
5. exactly three named secrets and four named variables—unknown or renamed references fail the contract;
6. full-commit-pinned GitHub actions and first-deploy order: C98 preflight -> Prisma migration -> C98 postdeploy -> pinned Vercel 59.1.3 pull/build/deploy -> exact health -> strict production App Links;
7. later/recovery release mode to require exact postdeploy state before the idempotent migration/deploy path;
8. count-only C98 evidence retention and unconditional deletion of ephemeral `.vercel`/`.next` state.

Migration and Vercel command output stays in runner-temporary files and is not uploaded. The artifact includes only C98's anonymous count output. No identifier, health value, URL, token or secret is retained.

## Contract verification

- Accepted dispatch fixtures: `preflight`, `deploy`, `release` (3).
- Rejected fixtures: 23 covering shortened/uppercase/mismatched SHA, wrong ref/mode/confirmation, missing arms, Vercel branch-policy drift, automatic trigger, non-preflight default, secret/variable drift, unpinned CLI/action, unguarded migration/deploy/health, operation reordering, continue-on-error and force behavior.
- C96 merge surface: one accepted / ten rejected temporary Git repositories.
- Current allowlist: exactly three workflows and 37 API paths; no iOS path is admitted.

The committed C100 surface passed at implementation HEAD with base `432b34c064d70a59c20753116b39390bee2c1cd0`, 207 commits, 1,181 files, 385,816,872 bytes and scopes `.github=3`, `.gitignore=1`, `android=182`, `api=37`, `docs/android=958`, `ios=0`. C101 subsequently adds only the reviewed dependency lock and reruns the superset gates.

The C101 superset at `fca55a68af9f33a1d7918ccb78964ba8bc20204d` passes [API CI](https://github.com/kt-git-1/med-manager_v3/actions/runs/31987147273), [API E2E](https://github.com/kt-git-1/med-manager_v3/actions/runs/31987147278) and [Android CI](https://github.com/kt-git-1/med-manager_v3/actions/runs/31987147267). No production workflow was dispatched.

## External boundary

All ten residual release-gate rows remain unchecked. RG-002 remains `READY_FOR_OWNER_ACTION`: this source and CI evidence proves only that the execution control fails closed. Completion still requires protected-environment review, a real authorized count-only preflight, approved migration window, successful production migration/API deployment, exact postdeploy/health/App Links evidence and subsequent mutation/FCM checks.
