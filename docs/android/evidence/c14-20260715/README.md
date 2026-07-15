# C14 UI-102 Dose Detail Dark and 200% Text — 2026-07-15

## Baseline and environment

- iOS/API baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android baseline: `android-dev@a1e793e`
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, Japanese
- Surface: production `PatientDoseDetailContent`

## Evidence

| Required state | Evidence |
|---|---|
| Dose detail, dark | [`android-ui-102-patient-dose-detail-dark.png`](android-ui-102-patient-dose-detail-dark.png) |
| Dose detail, 200% font | [`android-ui-102-patient-dose-detail-font-2.0.png`](android-ui-102-patient-dose-detail-font-2.0.png) |

## Review disposition

- Dark theme preserves the current three-card hierarchy, readable primary/secondary text, teal recorded-status treatment and distinct inset memo surface.
- At 200% font scale, the duplicated navigation/card medicine titles, dosage, date/time, status, memo and per-intake value reflow without clipping.
- The final per-intake card remains reachable through the production lazy list. The API-35 test scrolls to and asserts both `1回に飲む量` and `1回1.5錠` before persisting the image.
- Platform typography and clock glyph remain native; no production UI repair was required.

`PatientTodayContentTest` passes 20/20 on API 35, including the existing UI-102 content, empty-note, loading and retryable-error contracts plus these two adaptive variants. Matched iOS loading/error/dark/largest-text and physical TalkBack/device verification remain C01/Gate I work.
