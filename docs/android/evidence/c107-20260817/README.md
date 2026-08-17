# C107 Play listing and source-bound asset evidence

**Date:** 2026-08-17
**Baseline:** published iOS 1.0.6 Build 51, `main@432b34c`
**Branch:** `android-dev`
**Requirements:** XP-009, XP-010

## Finding and repair

The previous Play handoff still nominated the support-page anchor for signed-out deletion and retained the original C01 marketing captures. Its Caregiver Today export showed the removed next-dose hero instead of the current status-first summary and action-owning timeline.

C107 changes the canonical deletion URL to `/account-deletion` with an explicit undeployed-state warning and refreshes all eight 1350 x 2400 JPEGs from the latest applicable post-parity production-Compose fixture evidence. No production or real-user data is used. `play-store-assets/phone-ja-JP/sources.tsv` pins the exact ordered output-to-source mapping.

## Fail-closed contract

`verify-play-store-listing.py` and its synthetic suite require:

- Japanese `ja-JP`, package `com.afterlifearchive.medmanager` and app name `お薬見守り`;
- exact four-block Play length limits;
- consent-only Analytics, prohibited medical/personal Analytics fields, deletion, non-medical-device, medical-professional and notification caveats;
- canonical website/privacy/terms/support/account-deletion URLs with no legacy support anchor;
- billing disabled, no ads and the exact eight screenshot rows/alt text/order;
- matching shipping application ID, app-name resource, cleartext denial, advertising-ID removal and billing default;
- eight unique safe PNG evidence paths under `docs/android/evidence`.

The Gradle asset task additionally rejects extra directory files, source/output dimension drift, alpha-bearing JPEGs, source-pixel divergence, non-`#F3FAFC` horizontal padding, store-icon/feature-graphic drift and Android/iOS icon pixel mismatch.

Synthetic result: one accepted and twenty rejected listing fixtures.

## Local verification

- `python3 scripts/test-verify-play-store-listing.py`: passed.
- `python3 scripts/verify-play-store-listing.py --repository-root ..`: passed with 4 text blocks, 8 screenshots, 8 source mappings and 5 public URLs.
- `./gradlew verifyPlayStoreAssets`: passed, including the listing contract and source-bound pixel checks.
- `./gradlew testDebugUnitTest testReleaseUnitTest lintDebug verifyReleaseApkCompatibility verifyReleaseBundleContent verifyReleaseBundleInstallSurface verifyReleaseDeviceSplitSurface verifyPlayStoreAssets`: passed (112 tasks; Release SDK inventory 175 modules, manifest policy, APK/AAB/install surfaces and Play assets all green).
- `python3 scripts/test-verify-release-gates.py` and the canonical release-gate verifier: passed with 1 accepted/20 rejected fixtures; 10 gates remain 3 ready, 7 dependency-blocked and 0 verified.
- `./gradlew verifyMainMergeSurface`: passed against `origin/main@432b34c` with 217 commits, 1,210 files, 386,239,550 bytes and zero iOS paths (`.github=4`, `.gitignore=1`, `android=185`, `api=52`, `docs/android=968`, `ios=0`).
- `git diff --check`: passed.
- The eight refreshed exports were visually inspected after generation; all represent current role-correct surfaces and retain the full 1080 x 2400 source without crop or stretch.

## External boundary

C107 does not prove the undeployed account-deletion URL, a Play Console preview, the exact release-owner-signed build, vendor disclosure freshness, Console submission or track installation. RG-009 and RG-010 remain unchecked until those owner-controlled checks produce evidence.
