# C89 Release AAB content and manifest evidence

**Date:** 2026-08-17
**Branch:** `android-dev`
**Source baseline:** published iOS/API `main@432b34c`
**Parity rows:** XP-009 and XP-010 remain `PARTIAL`

## Contract

- Resolve bundletool 1.18.0 and all 17 verifier coordinates through strict Gradle lock state.
- Validate the generated AAB with bundletool before inspecting it.
- Require `BundleConfig.pb`, the base protobuf manifest and primary DEX.
- Allow exactly the reviewed base module; reject an unreviewed feature module.
- Reject embedded `.env`, `google-services.json`, local/secrets properties, service-account files and private key/keystore extensions.
- Dump `base/manifest/AndroidManifest.xml` and apply the C88 security/privacy policy to the AAB, not only the APK.
- Make this content gate a dependency of `bundleSignedRelease` before signature/certificate acceptance.

## Local results

- Synthetic AAB structure contract: approved base-only fixture passed; missing bundle config, added feature manifest, Firebase config, `.env` and private keystore fixtures failed.
- Strict lock: 187 unique coordinate lines total; Release runtime remains 174, isolated bundletool classpath is 17, with four shared.
- bundletool validation: passed.
- Unsigned Release AAB: base-only, four DEX files, eight native libraries.
- AAB protobuf manifest: six permissions, three reviewed exported components and three authentication links; C88 policy passed.
- Manifest policy: 12/12 synthetic contracts passed, including APK/AAB signature-protection numeric normalization.
- AAB SHA-256 is emitted by the gate for the per-build evidence ledger; it is not treated as stable across later runtime/signing inputs.
- Hosted Android CI [run #31972360476](https://github.com/kt-git-1/med-manager_v3/actions/runs/31972360476): every step passed for implementation commit `2bb0fdc`, including strict bundletool resolution, clean AAB generation, validation, content/manifest policy and all existing Android gates.

## Deliberately incomplete external evidence

- The inspected artifact is the current unsigned Release AAB, not the release-owner-signed production artifact.
- Production runtime values, upload/app-signing certificates, Play scan, Internal/Closed installation and Console declarations remain required.
