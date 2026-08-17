# Google Play Graphic Assets

`phone-ja-JP/` contains the ordered Japanese phone screenshots for the Play main store listing. They are derived only from Android screenshot-test fixtures; no production or real-user data is present.

## Current phone set

- Format: JPEG
- Canvas: 1350 x 2400 px (9:16)
- Count: 8
- Source UI: Android API-35 light-theme captures
- Padding: horizontal only, `#F3FAFC`; source UI is not cropped or stretched

The original 1080 x 2400 emulator captures cannot be uploaded directly because the 2400 px long side is more than twice the 1080 px short side. The padded 1350 x 2400 exports preserve the full UI and satisfy the Play screenshot ratio rule.

Exact source mapping is machine-readable in [`phone-ja-JP/sources.tsv`](phone-ja-JP/sources.tsv), and Japanese alt text is maintained in [`../play-store-listing-ja.md`](../play-store-listing-ja.md). C122-C123 replace screenshots 02 and 01 with exact Patient Today and Mode Select fixtures recaptured against the unmodified published iOS 1.0.6 Build 51 runtime. The other six C107 sources remain deterministic post-parity Compose evidence, but they predate this stricter published-build recapture procedure and stay queued for the same audit. If UI behavior changes, recapture the deterministic source fixture, update the mapping and regenerate the corresponding store image; do not edit medical state or copy only in the marketing export.

Regenerate all eight outputs from any supported JDK/Gradle environment, then verify that the committed handoff is byte-for-byte current:

```bash
cd android
./gradlew updatePlayStoreScreenshots verifyPlayStoreAssets
```

`renderPlayStoreScreenshots` writes scratch output under `app/build/generated`; `updatePlayStoreScreenshots` is the only task that copies those bytes into the committed handoff. `verifyPlayStoreAssets` never updates source files implicitly: it fails with the regeneration command when any JPEG differs from the deterministic 90%-quality renderer.

`icon-512.png` is the Play store icon export from the shipping iOS app-icon source. The same source is wired into Android legacy-density and API-26 adaptive launcher resources so the store, launcher and iOS identity do not drift.

`feature-graphic-1024x500.jpg` is the alpha-free JPEG handoff. It uses only the shipping patient/family role illustrations, current Android colors and factual product copy. Regenerate it from the repository root on macOS with:

```bash
./android/scripts/render-play-feature-graphic.swift
```

Both assets still require a final ownership check and Play Console preview verification by the release owner.
