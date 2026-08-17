# C116 Google Play Internal-track receipt contract

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

`android/scripts/verify-play-internal-track-receipt.py` binds an existing, freshly revalidated C114 upload receipt to two official Google Play Developer API v3 responses for Internal testing: the post-commit `applications.tracks.releases.list` result and `edits.tracks.get` from a fresh inspection edit.

Google documents the default Internal testing track identifier as `qa`. The releases-list verifier requires the target `versionCode` exactly once with lifecycle `RELEASE_LIFECYCLE_STATE_PUBLISHED`; because that lifecycle can also encompass a halted resumable release, the fresh Track resource must independently contain the same target exactly once with status `completed`. It rejects draft, review, rejected, unspecified, halted and mismatched target states.

The generated receipt is canonical, deterministic and atomic. It records only package/handoff/commit/version/AAB identity, hashes of the pre-existing C114 receipt and both raw responses, `qa`, the published lifecycle/completed status enums and active numeric version codes. It excludes release name/notes/free text, OAuth tokens, authorization headers, edit IDs, account/tester identity, credentials and patient data.

## Verification

- Internal-track receipt contract: 4 accepted/idempotent, 48 rejected.
- Existing C114 receipt: required and revalidated byte-for-byte against the same handoff and Bundle response.
- Official response contract: exact releases-list and Track envelopes, at most 20 strict releases, exact `qa` track, one published target version and one completed target version.
- Output: atomic, conflict-detecting and outside the immutable three-file handoff.
- Hosted Android CI: runs the dedicated Gradle contract.
- Clean local Android regression: 145 actionable Gradle tasks, 143 executed and 2 up-to-date; Debug/Release unit tests, Lint, Debug/Release assembly, APK/AAB policy, universal/device-split surfaces, store assets and all non-secret release contracts passed.
- Local `verifyFirebaseRuntime` intentionally remains unavailable without the four untracked runtime values; the pushed hosted run must execute it from GitHub Actions secrets before C116 acceptance.

Official schema/semantics reviewed on 2026-08-17:

- [APKs and Tracks](https://developers.google.com/android-publisher/tracks) (`qa`, update/commit workflow);
- [`applications.tracks.releases`](https://developers.google.com/android-publisher/api-ref/rest/v3/applications.tracks.releases) (release lifecycle and active artifact schema);
- [`edits.tracks`](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks) (Track release/status schema).

## Authority boundary

All fixtures are synthetic. This evidence does not access Play, create or commit an edit, assign a real track, publish an artifact, prove Play app signing or install the app. `RG-006` remains blocked until its dependencies pass and the release owner retains the real C114 plus C116 responses/receipts and completes exact Play installation/update/device verification.
