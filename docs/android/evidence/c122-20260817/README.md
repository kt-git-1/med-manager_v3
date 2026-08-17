# C122 published iOS 1.0.6 Patient Today visual rebaseline

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Finding

C61-C64 completed the source-level published-build audit, but the retained UI-101 comparison images under C42/C43 were still captured from the earlier `main@1cf8aef` runtime. The deterministic Android `PatientModePreview` and the Japanese Play screenshot still used that older fixture. A fresh comparison against the checked-in current SwiftUI `PatientTodayV105DebugPreview` exposed material visual drift that state/copy tests did not detect.

## Capture contract

- iOS: the unmodified `main@432b34c` source was built in a fresh DerivedData directory and launched with `-PatientTodayV105Preview` on iPhone 17e / iOS 26.5. The raw capture is 1170 x 2532.
- Android: API 35 AVD, 1080 x 2400, production `PatientModePreview` shell and production `TodayContent` component tree. C124 recaptures the same fixture at a clean 9:41 after moving the shared Patient header to SwiftUI's exact 34/17 title/subtitle metrics.
- Both sides use 2026-08-17 Tokyo time with the same 13:47 logical clock, four slot times, six scheduled doses, morning actual time and next bedtime action.
- The data is deterministic and synthetic. No API, account, token, patient identifier, medication record or Analytics event was used.

## Evidence

| Artifact | File |
|---|---|
| Raw iOS | [`ios-ui-101-published-v105-light.png`](ios-ui-101-published-v105-light.png) |
| Raw Android | [`android-ui-101-published-v105-light.png`](android-ui-101-published-v105-light.png) |
| Normalized iOS | [`ios-ui-101-published-v105-light-normalized.png`](ios-ui-101-published-v105-light-normalized.png) |
| Normalized Android | [`android-ui-101-published-v105-light-normalized.png`](android-ui-101-published-v105-light-normalized.png) |
| Side by side | [`ui-101-published-v105-side-by-side.png`](ui-101-published-v105-side-by-side.png) |
| 50% overlay | [`ui-101-published-v105-overlay-50.png`](ui-101-published-v105-overlay-50.png) |

Normalization resizes both complete device captures to 1080 x 2400 for diagnostic alignment. Raw files remain authoritative.

## Drift closed

- Today now uses the current calendar header instead of the obsolete pills glyph.
- The four-slot progress strip now matches the published 142-unit card height, 20-unit radius, 10-unit gaps, 52-unit status circles, 22/19-unit type hierarchy, eight-unit connector and completed-prefix connector.
- Completed status uses a simple check, bedtime uses a moon and a missing slot uses a minus; status is still conveyed by text and color as well.
- Actual record/current times use the current iOS `H:mm` presentation while scheduled slot times retain `HH:mm`.
- The next-dose header now renders exact `予定 HH:mm` copy, current 58/30-unit clock hierarchy and the 64-unit primary action.
- The unavailable primary action retains the effective iOS disabled teal opacity and no longer shows the obsolete Android-only recording-window guide.
- `PatientModePreview` now uses the exact published fixture instead of the July pre-1.0.6 sample, and the Japanese Play Patient Today source is moved to this evidence.
- C124 centralizes the published 34/17 Patient header type contract for Today, History and Settings; this evidence and Play screenshot 02 are regenerated so C122 does not retain the older 32/16 Material defaults.

## Accepted platform differences

- SF Symbols and Material icons retain native outlines; the semantic calendar/clock/check/moon meanings match.
- Status/navigation bar glyphs and font rasterization remain platform-native.
- The iOS debug route manually mirrors the bottom bar without the production selected background. Published `PatientBottomTabBar` source applies a 13% teal selected surface, so Android correctly retains its matching production selected treatment rather than copying the debug-route omission.

## Verification

- Exact published fixture instrumentation: 1/1 passed on API 35 and emitted the retained raw Android capture.
- `PatientTodayContentTest`: 28/28 passed on API 35, covering Today lifecycle, late/actual-time behavior, inventory, PRN, dose detail, dark and 200% states.
- Complete API 35 connected UI suite: 281/281 passed in four retained shards (66 + 59 + 79 + 77), with zero failure, error or skip and automatic package cleanup.
- Debug JVM: 216/216; Release JVM: 213/213; Debug Lint, Debug/Release assembly, Release APK compatibility, deterministic Play assets and release-gate ledger verification all passed.
- iOS simulator build from the exact pinned source: passed.
- Remaining dark/200% and physical TalkBack evidence stays in the existing UI-101/V01 matrix; C122 does not promote any external release gate.

## SHA-256

| File | SHA-256 |
|---|---|
| `ios-ui-101-published-v105-light.png` | `8c27122a001e89a19b6bcc0133abdb48c568874cdd0bd1dad3654a7b06d82fb7` |
| `android-ui-101-published-v105-light.png` | `76295b8d1cda3d190ec0e60fca13424a19e9684aad4b5645fc3d0eb11ce9fdf2` |
| `ios-ui-101-published-v105-light-normalized.png` | `63ba601cab00e26f525508c4dd5b3197152d86f3ee2ee2f67e482b522888e818` |
| `android-ui-101-published-v105-light-normalized.png` | `55c4cb8d4ae02564a819932ebb0d7d67088e25b98357067f8e281c7a72cae7a5` |
| `ui-101-published-v105-side-by-side.png` | `9bc77740d0be7e226cb67908317a856b55f4debd54229dbf1ab10331d59f2b2c` |
| `ui-101-published-v105-overlay-50.png` | `683007a2da84386b9654be968aebcef50e1cedd8657d0e08a30622ad6976049d` |
| `play-store-assets/phone-ja-JP/02-patient-today.jpg` | `2d5970fbb762c41768c81648a5d6f89f98d4c23822addbd5dd78bddd0db500d3` |
