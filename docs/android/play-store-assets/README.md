# Google Play Graphic Assets

`phone-ja-JP/` contains the ordered Japanese phone screenshots for the Play main store listing. They are derived only from Android screenshot-test fixtures; no production or real-user data is present.

## Current phone set

- Format: JPEG
- Canvas: 1350 x 2400 px (9:16)
- Count: 8
- Source UI: Android API-35 light-theme captures
- Padding: horizontal only, `#F3FAFC`; source UI is not cropped or stretched

The original 1080 x 2400 emulator captures cannot be uploaded directly because the 2400 px long side is more than twice the 1080 px short side. The padded 1350 x 2400 exports preserve the full UI and satisfy the Play screenshot ratio rule.

Source mapping and Japanese alt text are maintained in [`../play-store-listing-ja.md`](../play-store-listing-ja.md). If UI behavior changes, recapture the source fixture and regenerate the corresponding store image; do not edit medical state or copy only in the marketing export.

`icon-512.png` is the Play store icon export from the shipping iOS app-icon source. The same source is wired into Android legacy-density and API-26 adaptive launcher resources so the store, launcher and iOS identity do not drift.

`feature-graphic-1024x500.jpg` is the alpha-free JPEG handoff. It uses only the shipping patient/family role illustrations, current Android colors and factual product copy. Regenerate it from the repository root on macOS with:

```bash
./android/scripts/render-play-feature-graphic.swift
```

Both assets still require a final ownership check and Play Console preview verification by the release owner.
