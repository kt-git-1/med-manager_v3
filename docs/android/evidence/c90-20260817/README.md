# C90 Upload keystore pre-build evidence

**Date:** 2026-08-17

**Branch:** `android-dev`

**Source baseline:** published iOS/API `main@432b34c`

**Parity row:** XP-010 remains `PARTIAL`

## Contract

- Keep the release owner in control of key creation, selection and backup.
- Before AAB generation, open the configured keystore and exact alias.
- Require that alias to be a private-key entry and prove its key password by signing and verifying an ephemeral local JAR.
- Normalize the configured SHA-256 representation and require an exact match with the selected alias certificate.
- Emit only the accepted certificate SHA-256; never print a store/key password.
- Retain the existing post-generation signed-AAB signature/single-signer/certificate check.

## Synthetic verification

- Upload-keystore contract: one valid JKS fixture passed.
- Seven fixtures failed closed: mismatched certificate, unknown alias, incorrect store password, incorrect key password, malformed fingerprint, missing store and a trusted-certificate-only alias.
- The Gradle `verifyUploadKeystore` path passed with the synthetic alias and rejected the same store when the expected certificate was changed.
- A complete synthetic `bundleSignedRelease` passed 72 tasks: production-runtime shape, upload keystore, 175-module SDK policy, actual APK manifest/16 KB policy, validated base-only AAB content, JAR signature and post-build certificate identity.
- The synthetic signed AAB contained four DEX files, eight native libraries, six permissions, three exported components and three authentication links.
- `./gradlew clean` removed the synthetic key and artifact. No generated `.jks`, `.keystore` or `.p12` remained under `app/build`.

## Clean ordinary regression

- Upload-keystore synthetic contract: 1 accepted, 7 rejected.
- Debug JVM: 216/216 passed.
- Release JVM: 213/213 passed.
- Lint passed.
- Release SDK policy passed for 175 resolved modules.
- Release APK compatibility and manifest policy passed.
- Unsigned Release AAB validation/content/manifest policy passed as base-only with four DEX files and eight native libraries.
- Play listing assets passed.
- Hosted Android CI [run #31973374763](https://github.com/kt-git-1/med-manager_v3/actions/runs/31973374763) passed every step for implementation commit `04555ff`, including the Linux upload-keystore verifier contract and all existing Android release gates.

## Deliberately incomplete external evidence

- No release-owner upload keystore, password, alias or certificate was read, generated or selected.
- The synthetic certificate and AAB are disposable contract fixtures, not Play-upload artifacts.
- The exact production runtime signed AAB, independent Play Console fingerprint comparison, Play processing/scan, track installation and rollout evidence remain required.
