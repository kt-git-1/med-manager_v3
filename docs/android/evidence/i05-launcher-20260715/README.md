# I05 Android launcher icon evidence — 2026-07-15

## Scope

- Branch/commit before this fix: `android-dev@4287da9`
- Device: API 35 ARM64 emulator, 1080 x 2400 px
- Artifact: locally built Debug APK installed through adb
- Launcher: AOSP/Pixel launcher app drawer, circular adaptive mask
- Source identity: shipping iOS app icon; no iOS source file changed

## Evidence

| Capture | Result |
|---|---|
| [`android-api35-launcher-icon.png`](./android-api35-launcher-icon.png) | The first adaptive-icon implementation resolves to `お薬見守り` and renders the intended artwork, but the clipboard top and side medicines sit too close to the circular mask. |
| [`android-api35-launcher-icon-inset.png`](./android-api35-launcher-icon-inset.png) | The final adaptive foreground applies an 8% inset. The clipboard, capsule character and supporting medicine artwork remain recognizable inside the circular mask without changing the Play/iOS source icon. |

The emulator produced one GPU-corrupted black-region capture during reinstall. It was rejected, the emulator was rebooted, and only the clean post-boot capture above was retained.

## Verification

- `assembleDebug`: passed
- `lintDebug`: passed
- `verifyPlayStoreAssets`: passed
- Clean uninstall/install: passed
- Cold `MainActivity` launch after emulator reboot: passed (`Status: ok`, process resumed)
- Launcher label: `お薬見守り`
- Circular adaptive-mask visual inspection: passed after the 8% foreground inset

The Play store icon remains the full 512 x 512 RGBA source export. The inset is intentionally Android-launcher-only so the Play and iOS identity assets do not drift.
