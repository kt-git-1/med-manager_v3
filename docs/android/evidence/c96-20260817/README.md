# C96 committed main-merge surface gate — 2026-08-17

## Result

C96 makes the committed `android-dev` to `main` isolation/rebaseline boundary fail-closed. It does not merge either branch, deploy the API, or close any Play/physical row.

After a fresh fetch, implementation commit `778cc29509c8442000b41cf3d35b151c445c3d85` passed with:

- base and exact merge base: `origin/main@432b34c064d70a59c20753116b39390bee2c1cd0`;
- 199 commits after main;
- 1,164 added/modified files and no deletion/rename;
- 385,687,550 bytes in the changed head-tree blobs;
- scopes: `.github=1`, `.gitignore=1`, `android=180`, `api=30`, `docs/android=952`, `ios=0`.

The existing unstaged user `.gitignore` edit is intentionally not part of these committed counts and was neither staged nor modified. A final operator must still require a clean worktree; this gate proves committed history only.

## Enforced contract

`verifyMainMergeSurface` requires:

1. freshly available `origin/main` and `HEAD` commits;
2. `origin/main` as both ancestor and exact merge base of Android HEAD;
3. only added/modified paths—no deletion, rename or copy;
4. exactly the five reviewed top-level scopes: Android, `docs/android`, Android CI, Android ignore policy and API;
5. exactly `.github/workflows/android-ci.yml`, no other workflow;
6. exactly the reviewed 30-file API allowlist covering Android FCM registration/envelopes, mutation idempotency, privacy/security and Digital Asset Links plus their tests/migration;
7. zero `ios/` or non-Android documentation paths;
8. the exact reviewed committed `.gitignore` delta, so a later `/docs` override or other broad ignore cannot silently pass;
9. no environment, Firebase config, service-account, key/keystore, APK/AAB/APKS or generated build/IDE/dependency directory;
10. ordinary/executable blobs only—no symlink or submodule;
11. at most 1,250 files, 2 MiB per blob and 400 MiB in the changed tree.

The size bounds retain the existing high-fidelity visual evidence instead of degrading it while preventing unreviewed growth. The pre-C96 input tree contained 842 PNG files and its largest blob was about 1.63 MiB; no image was recompressed or removed.

## Contract verification

One isolated temporary Git repository is accepted. Ten are rejected for:

- iOS drift;
- unreviewed API path;
- unreviewed workflow;
- private signing material;
- generated IDE output;
- oversized evidence blob;
- deletion of a main file;
- unexpected root file;
- unreviewed `.gitignore` override;
- a divergent/non-ancestor main.

The fixtures create no remote branch and are deleted after each run.

## Local regression

- C96 committed merge surface: passed.
- C96 pure Git contract: 1 accepted / 10 rejected.
- C95 production App Links contract: 2 accepted / 17 rejected.
- C95 Play-installed App Links contract: 3 accepted / 13 rejected.
- Debug JVM: 216/216 passed.
- Release JVM: 213/213 passed.
- Lint Debug: passed.
- Release APK compatibility: six permissions, three reviewed exports, two auth links, API 26/35, advertising exclusion and 16 KiB ZIP/native alignment passed.
- Workflow YAML parse: passed.
- Generated build output and Python caches were removed after verification.

## Hosted CI

- Implementation commit: `778cc29509c8442000b41cf3d35b151c445c3d85`.
- Android CI: [run 31981843738](https://github.com/kt-git-1/med-manager_v3/actions/runs/31981843738) — 28/28 workflow steps passed, including full-history checkout, fresh main fetch, C96, C95, both JVM variants, Lint, APK, AAB, universal/device-split and Play asset gates.
- `actions/checkout` now uses full history, followed by an explicit `origin/main` fetch before `verifyMainMergeSurface`; a shallow isolated Android HEAD cannot falsely pass ancestry.

## Residual release boundary

- Rerun the task after every `origin/main` advance. If it fails, bring main into `android-dev`, directly review conflicts/API behavior and rerun the complete Android/API gates.
- Updating the API allowlist or limits is a review event, not a routine way to make CI green.
- Before merge, separately require a clean worktree, the exact release-owner C92 handoff, remaining physical/Play rows and explicit owner approval.
- After the approved merge/deploy, complete production App Links and FCM registration/delivery; C96 does not make those external states true.
