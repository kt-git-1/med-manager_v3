# C13 UI-103 PRN Unavailable and Adaptive States — 2026-07-15

## Baseline and environment

- iOS/API baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android baseline: `android-dev@cf4fdee` before this parity repair
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, Japanese
- Captured surface: production `PatientTodayContent` PRN sheet, cropped to its 1080 x 1879 pixel semantic node

## Evidence

| Required state | Evidence |
|---|---|
| Insufficient inventory, light | [`android-ui-103-prn-insufficient-light.png`](android-ui-103-prn-insufficient-light.png) |
| PRN list, dark | [`android-ui-103-prn-list-dark.png`](android-ui-103-prn-list-dark.png) |
| PRN list, 200% font | [`android-ui-103-prn-list-font-2.0.png`](android-ui-103-prn-list-font-2.0.png) |

## Review disposition

- A PRN medicine whose inventory is below its per-dose quantity displays the red `在庫不足` badge and exposes a disabled record action. The API-35 test also attempts the click and proves that the record callback is not invoked.
- Source comparison found a visual drift in the unavailable action: iOS retains its teal background and applies 55% opacity to the whole button, while Android previously inherited Material's gray disabled treatment. Android now supplies teal enabled/disabled colors and applies the same 55% whole-button opacity.
- Dark theme preserves the orange PRN role, surface hierarchy, readable primary/secondary text and white-on-teal action treatment.
- At 200% font scale, the medicine name, dose count and instruction reflow without clipping, and the full record action remains reachable through the bounded sheet scroll.

`PatientTodayContentTest` now has 18 API-35 tests, including insufficient-inventory behavior, dark rendering and 200%-font action reachability. Matched iOS adaptive captures and physical TalkBack/device verification remain C01/Gate I work.

C45 supersedes this historical Android-only bottom-sheet checkpoint with fresh current-baseline same-data iOS/Android list, loading, error, insufficient, dark and largest-text pairs. Physical TalkBack/device verification remains Gate I work.
