# C108 deterministic Play screenshot rendering evidence

**Date:** 2026-08-17
**Baseline:** published iOS 1.0.6 Build 51, `main@432b34c`
**Branch:** `android-dev`
**Requirements:** XP-009, XP-010

## Gap and implementation

C107 proved each committed JPEG came from one current Compose evidence PNG, but the export itself was produced manually with macOS `sips`. The repository did not own encoder settings or prove another development/CI environment could reproduce the same bytes.

C108 adds three separate Gradle responsibilities:

- `renderPlayStoreScreenshots` reads the exact C107 TSV map and writes only to `app/build/generated/play-store-phone-ja-JP`;
- `updatePlayStoreScreenshots` is the explicit reviewed operation that copies those eight files into the committed handoff;
- `verifyPlayStoreAssets` renders independently and rejects any committed JPEG that differs byte-for-byte.

The JDK renderer fixes a 1350 x 2400 RGB canvas, 135-pixel `#F3FAFC` horizontal padding, unchanged 1080 x 2400 source placement, non-progressive JPEG and explicit 0.90 compression quality. It retains the existing pixel/source/padding checks as an independent decoded-image layer.

## Contract evidence

`verifyPlayStoreScreenshotRendererContract` renders the same synthetic input twice and requires identical bytes. It also rejects three malformed invocations: wrong canvas width, wrong canvas height and out-of-range JPEG quality.

Before the explicit update, `verifyPlayStoreAssets` failed on `01-mode-select.jpg` because the previous `sips` bytes were not the deterministic renderer output. After `updatePlayStoreScreenshots`, all eight committed and generated SHA-256 values matched, their dimensions remained 1350 x 2400 with no alpha, and the complete asset gate passed. The refreshed Caregiver Today export was visually inspected and retained the same current status-first UI without crop or stretch.

## Verification

- `./gradlew verifyPlayStoreScreenshotRendererContract renderPlayStoreScreenshots`: passed.
- Pre-update `./gradlew verifyPlayStoreAssets`: failed closed with the exact regeneration instruction.
- `./gradlew updatePlayStoreScreenshots verifyPlayStoreAssets`: the update executed; the subsequent standalone asset verification passed after the task-order contract was made explicit.
- `./gradlew clean verifyPlayStoreAssets`: passed from empty build output, proving regeneration does not depend on retained scratch files.
- `./gradlew testDebugUnitTest testReleaseUnitTest lintDebug verifyReleaseApkCompatibility verifyReleaseBundleContent verifyReleaseBundleInstallSurface verifyReleaseDeviceSplitSurface verifyPlayStoreAssets`: passed (114 tasks; 111 executed, 3 up-to-date) after the clean regeneration.
- Release-gate synthetic/canonical verification: passed with 1 accepted/22 rejected fixtures; 10 residual gates remain 3 ready, 7 dependency-blocked and 0 verified.
- `./gradlew verifyMainMergeSurface`: passed against `origin/main@432b34c` with 218 commits, 1,211 files, 385,724,552 bytes and zero iOS paths (`.github=4`, `.gitignore=1`, `android=185`, `api=52`, `docs/android=969`, `ios=0`).
- `sips` inspection: 8/8 JPEG, 1350 x 2400, no alpha.
- Generated/committed SHA-256 comparison: 8/8 exact matches.

## External boundary

Deterministic repository bytes do not prove upload, Play transcoding/preview, the exact release-owner-signed app UI or Console submission. RG-009/RG-010 remain unchecked until the exact signed build and these eight files are compared in the owner-controlled Play preview.
