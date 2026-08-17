# C119 Play-installed physical package receipt contract

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

`android/scripts/verify-play-installed-package-receipt.py` closes the automatable identity gap between a retained C118 downloaded-base-APK receipt and package-manager state on one explicitly selected physical Android device. It does not install, update, launch, sign in or mutate app data.

The verifier requires an authorized non-emulator serial on API 31 or newer, a package installed by `com.android.vending`, the exact C118 `versionCode`/`versionName`, the complete C118 app-signing certificate set, exactly one installed `base.apk`, at least one optimized split and only `www.okusuri-mimamori.com: verified`. It streams the installed base bytes using argument-vector `adb exec-out cat`, then requires SHA-256 and byte size to match exactly one C118 signing-key row.

Installer, version, package paths, installed base bytes and the C118 receipt are read again after domain verification. Any drift fails before output. The deterministic atomic receipt retains fixed release identity, the C118 receipt SHA-256, installed base hash/size, public signing fingerprints, package/split counts, canonical App Links state and bounded manufacturer/model/API. It excludes the ADB serial, installed APK paths, account/tester identifiers, credentials, tokens and patient data.

## Verification

- C119 contract: seven accepted/parser/idempotent/CLI paths and forty rejected receipt/schema/device/emulator/API/installer/version/path/base-byte/App-Link/TOCTOU/output paths.
- Residual release-gate contract: one accepted and sixty rejected fixtures; RG-006 retains C119 without changing its dependency-blocked status.
- Gradle provides `verifyPlayInstalledPackageReceiptContract` for hosted synthetic execution and `verifyPlayInstalledPackageReceipt` for the release-owner physical path.
- Hosted CI executes only the synthetic fake-ADB contract. It neither accesses Play nor fabricates a device pass.

Android's official [ADB documentation](https://developer.android.com/tools/adb) defines targeted device shell/stream operations, and the official [App Links verification procedure](https://developer.android.com/training/app-links/verify-android-applinks) documents package-manager re-verification and domain-state inspection. Play-generated APK byte identity remains rooted in the official [`generatedapks.download`](https://developers.google.com/android-publisher/api-ref/rest/v3/generatedapks/download) output already validated by C118.

## Owner execution

From `android/`, after the exact Internal-track install and production App Links deployment:

```bash
ANDROID_SERIAL=<physical-device-serial> \
PLAY_APP_SIGNING_CERT_SHA256_FINGERPRINTS=<complete-C118-colon-fingerprint-set> \
PLAY_DOWNLOADED_BASE_APKS_RECEIPT=<owner-path>/<handoff>.play-downloaded-base-apks-receipt.json \
PLAY_INSTALLED_PACKAGE_RECEIPT_OUTPUT=<owner-path>/<handoff>.play-installed-package-receipt.json \
./gradlew verifyPlayInstalledPackageReceipt
```

Retain the unchanged C118 and C119 receipts with owner-controlled Play responses/APKs. Do not copy serials, installed paths, tester identities or credentials into Git evidence.

## Authority boundary

The accepted contract uses a fake ADB executable and arbitrary fixture bytes whose hash is represented by a synthetic C118 receipt. It proves parser, binding, privacy and fail-closed behavior only. The currently connected A302SH has no exact Play-installed C118 artifact and therefore cannot supply C119 evidence. RG-006 remains incomplete until approved production dependencies pass, the real artifact is installed from Internal testing, C119 passes, a prior-version update is exercised and every required physical behavior row is retained.
