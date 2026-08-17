# C118 Google Play downloaded base-APK byte receipt contract

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

`android/scripts/verify-play-downloaded-base-apks-receipt.py` binds APK bytes returned by the official Google Play Developer API v3 `generatedapks.download` method to the complete C117 receipt chain. It accepts exactly one C117 base-module master `downloadId` per app-signing-key group and refuses incomplete rotation coverage, configuration splits, duplicate selections or reused local files.

Every selected APK must be a real, bounded, non-symlink ZIP outside the immutable three-file handoff. Its inventory must be unique and traversal-free, contain `AndroidManifest.xml` plus primary DEX content and exclude private configuration/key material. Android SDK `aapt2` must report the production package and exact C117 `versionCode`/`versionName` with no nonempty split identity. Android SDK `apksigner` must verify exactly one signer, a v2-or-newer embedded signature and the certificate assigned to that exact C117 signing-key group.

The atomic receipt retains the fixed package/commit/version/AAB chain, C117 receipt hash, Android Build Tools version and, per signing key, only the public certificate fingerprint, APK SHA-256, byte/ZIP/DEX counts and verified signature-scheme names. Download IDs, request URLs, local paths, OAuth data, account/tester identity, credentials and patient data are excluded.

## Verification

- Downloaded base-APK receipt contract: 4 accepted/idempotent, 46 rejected.
- Accepted coverage: one signing key, byte-identical re-entry, CLI execution and complete two-key rotation coverage.
- Rejected coverage: C117-chain drift, missing/duplicate/non-master selections, incomplete or duplicate signing-key coverage, globally reused download IDs, unsafe/malformed/oversized/private APK ZIPs, package/version/split drift, tool failure, signer/scheme mismatch, APK mutation during tool verification, unsafe SDK tools and unsafe/conflicting output.
- Real-tool integration: Android Build Tools 36.0.0 `aapt2` and `apksigner` inspect the existing signed Debug APK as production package `com.afterlifearchive.medmanager`, version 1/1.0.6, one signer and verified v2 signature. This proves the SDK command/parsing path, not Play signing.
- Residual gate contract: one accepted, 57 rejected; RG-005 and RG-006 both retain C118 without changing their status.
- Hosted Android CI runs the dedicated Gradle contract; no Play authority or artifact is required for its synthetic fixtures.
- Clean local non-secret regression: 147 actionable Gradle tasks, 40 executed and 107 up-to-date; Debug/Release unit tests, Lint, assemblies, APK/AAB policies, universal/device-split surfaces, store assets and all local Play receipt contracts passed. The signed-AAB shell contract, twelve manifest-policy tests and connected-shard runner contract also passed.

Official method reviewed on 2026-08-17: [`generatedapks.download`](https://developers.google.com/android-publisher/api-ref/rest/v3/generatedapks/download) downloads one signed APK generated from the selected app bundle using a `downloadId` returned by `generatedapks.list`. Android's official [`apksigner`](https://developer.android.com/tools/apksigner) verifies the APK signature and prints signing-certificate data; official [`aapt2 dump badging`](https://developer.android.com/tools/aapt2) extracts manifest package/version metadata.

## Authority boundary

The contract fixtures do not call Play and the real-tool integration uses a local Debug APK. A real C118 receipt requires release-owner-controlled C114/C116/C117 inputs and actual downloaded base-master APK bytes for every Play signing-key group. It still does not prove Google Play installer identity, fresh install, prior-version update, App Links, FCM, session/lifecycle behavior or physical-device acceptance. RG-005/RG-006 remain unchecked until those owner/device steps and all dependencies pass.
