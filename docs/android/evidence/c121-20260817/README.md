# C121 warning-free immutable API workflow runtime

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Finding

The exact C120 hosted commit `05b135c768c9bd29f994dee0ec86426626dbade0` passed all three workflows, but green conclusions were insufficient:

- [Android CI run 32019169680](https://github.com/kt-git-1/med-manager_v3/actions/runs/32019169680): 35/35 steps passed and zero annotations.
- [API CI run 32019169666](https://github.com/kt-git-1/med-manager_v3/actions/runs/32019169666): Lint/Typecheck, isolated PostgreSQL Tests and Build all passed; each of the three jobs had one warning annotation.
- [API E2E run 32019169674](https://github.com/kt-git-1/med-manager_v3/actions/runs/32019169674): passed with two failure-only uploads skipped, but the job had one warning annotation.

All four warnings were identical: GitHub reported Node.js 20 deprecation and forced `actions/checkout@v4` plus `actions/setup-node@v4` to Node.js 24. The skipped `upload-artifact@v4` actions would retain the same stale runtime risk on a failing E2E run, so C121 audits them from source rather than waiting for a failure.

## Correction

Official GitHub release/tag APIs and each tagged `action.yml` were inspected directly:

- `actions/checkout` v7.0.1 -> `3d3c42e5aac5ba805825da76410c181273ba90b1`, `using: node24`;
- `actions/setup-node` v7.0.0 -> `820762786026740c76f36085b0efc47a31fe5020`, `using: node24`;
- `actions/upload-artifact` v7.0.1 -> `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a`, `using: node24`.

API CI's three jobs, API E2E and the manual Production workflow now use only those full SHAs. All three workflows declare `permissions: contents: read`; each checkout sets `persist-credentials: false`. The API runtime verifier requires exactly five Node 22 application-runtime setups and thirteen immutable Node 24 action-runtime invocations with the reviewed per-workflow counts. It rejects mutable/old/extra action use, missing or write permissions, persisted credentials and runtime/count drift.

## Verification

- API Node/runtime/action contract: one accepted and twenty-five rejected fixtures.
- Production workflow contract: three accepted modes and twenty-nine rejected trigger/config/guard/order/action drifts.
- C120 control-plane contract: seven accepted and thirty-four rejected fixtures remains green.
- Release-gate contract: one accepted and seventy rejected fixtures; RG-002 and RG-010 retain C121.
- Node 22 local regression: dependency install/generation, zero High/Critical audit, ESLint, Prettier, TypeScript and production Next.js build pass; the six known Moderate Firebase Storage/uuid transitive findings remain explicit.

The exact C121 implementation commit `a88b3cbb9381cf19ca11d52c23520d8427f3ab88` then passed the post-fix hosted acceptance:

- [API CI run 32020590554](https://github.com/kt-git-1/med-manager_v3/actions/runs/32020590554): Lint/Typecheck 15/15, isolated PostgreSQL Tests 16/16 and Build 8/8 steps succeeded; every one of the three jobs has zero annotations.
- [API E2E run 32020590555](https://github.com/kt-git-1/med-manager_v3/actions/runs/32020590555): all fifteen applicable steps succeeded, the two failure-only v7 upload steps were correctly skipped, and the job has zero annotations.
- [Android CI run 32020590499](https://github.com/kt-git-1/med-manager_v3/actions/runs/32020590499): 35/35 steps succeeded and the job has zero annotations.

This closes the observed Node 20 action-runtime warning class for the reviewed workflow source. It does not waive rechecking every exact final release job after a future action change.

Official sources: [checkout v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1), [setup-node v7.0.0](https://github.com/actions/setup-node/releases/tag/v7.0.0), [upload-artifact v7.0.1](https://github.com/actions/upload-artifact/releases/tag/v7.0.1), and [GitHub's Node 20 action-runtime deprecation notice](https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/).

## Authority boundary

C121 changes repository workflows only. It does not register the Production workflow on `main`, protect `main`, create the `android-api-production` environment, configure any secret/variable, dispatch a workflow, access Production, or close RG-002/RG-010. Those owner-controlled boundaries remain exactly as recorded by C120.
