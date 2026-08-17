# C120 GitHub production control-plane read-only preflight

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

`api/scripts/verify-android-production-control-plane.mjs` adds the missing live metadata check in front of C100's source-only production workflow contract. It is read-only: it cannot create an environment, change protection, write a secret/variable, arm a release, dispatch a workflow, query the database or deploy Vercel.

For the fixed `kt-git-1/med-manager_v3` repository it requires:

- the exact reviewed 40-character `main` SHA and a protected, active repository;
- exactly one active `.github/workflows/android-api-production-release.yml` registered on the default branch;
- exactly one `android-api-production` environment;
- one required-reviewer rule with at least one User/Team reviewer and self-review prevention;
- protected-branch-only deployment with no custom branch policy;
- exactly the three reviewed environment secret **names** and four variable names;
- reviewer attestation `true`, bounded nonempty Vercel IDs, and the release arm matching explicit `safe=false` or `armed=true` mode.

The fine-grained token is accepted only through `GITHUB_CONTROL_PLANE_TOKEN`, bounded, sent only as an authorization header and never printed. API failures expose endpoint path and status only. The success line contains fixed repository identity, a 12-character public main prefix, workflow state, protection/count facts and arm mode; it excludes raw API responses, token, secret values, Vercel IDs and reviewer identities.

## Verification

- Control-plane contract: seven accepted function/safe/armed surfaces and thirty-four rejected repository/default-branch/SHA/protection/workflow/environment/reviewer/self-review/branch-policy/secret-name/variable-name/count/value/arm states.
- Production workflow source contract: three accepted modes and twenty-nine rejected trigger/config/guard/order/control-plane drifts.
- Missing-token CLI control: fails with only `GITHUB_CONTROL_PLANE_TOKEN is missing or malformed`.
- Release-gate contract: one accepted and sixty-four rejected fixtures; RG-002 directly retains C120 and remains `READY_FOR_OWNER_ACTION`.
- C96 current merge allowlist expands by exactly the C120 verifier and test, to four workflows and fifty-four reviewed API paths; no iOS path is admitted.

The fixture suite runs in API CI and the manual Production workflow's source-quality contract. Hosted CI does not call live administration APIs or fabricate a protected environment.

## Live read-only observation

The public GitHub and production surfaces were re-read without authentication or state change:

- `origin/main` remains `432b34c064d70a59c20753116b39390bee2c1cd0`, is the Android baseline ancestor and the public branch response reports `protected=false`.
- The repository has four registered workflows and none has the Production workflow path; the path-specific runs endpoint therefore returns HTTP 404.
- The repository exposes eight generated Preview/Production environments; none is named `android-api-production`, and all report zero protection rules.
- `https://www.okusuri-mimamori.com/api/health` returns HTTP 200, `application/json`, no redirect and the expected 15-byte health body.
- `/.well-known/assetlinks.json` and `/account-deletion` both return redirect-free HTTP 404 HTML.

No secret or variable value, reviewer identity, database value or token was requested or printed.

## Owner execution

After the reviewed API/workflow source is present on protected `main`, the release owner creates `android-api-production`, configures independent review/self-review prevention/protected-branch-only deployment, and adds only the names documented in the runbook. Inject a short-lived least-privilege token securely, then run from `api/`:

```bash
node scripts/verify-android-production-control-plane.mjs \
  --expected-main-sha "$(git -C .. rev-parse origin/main)" \
  --arm-state safe
```

Retain only the success line. Immediately before an approved write window, switch the release arm to `true`, rerun with `--arm-state armed`, obtain independent environment approval and dispatch. Always return the arm to `false` and rerun `safe` afterward.

Official APIs: [Actions workflows](https://docs.github.com/en/rest/actions/workflows), [deployment environments](https://docs.github.com/en/rest/deployments/environments), [Actions secrets](https://docs.github.com/en/rest/actions/secrets), and [Actions variables](https://docs.github.com/en/rest/actions/variables).

## Authority boundary

C120 supplies a verifier, not authorization. The live prerequisite is presently absent, so no C120 success receipt exists and RG-002 remains unchecked. Creating/protecting the environment, configuring metadata, changing Supabase SSL enforcement, dispatching preflight/deploy/release or promoting workflow/API source to `main` requires explicit release-owner action and review.
