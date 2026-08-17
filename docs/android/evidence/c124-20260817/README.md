# C124 published iOS 1.0.6 Patient History visual rebaseline

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Finding

The retained UI-104 image came from the older C37 runtime comparison. A clean launch of the unchanged published iOS Patient History preview exposed four production-visible Android drifts: the header and remaining-count pill used outline clocks instead of `clock.fill`, the streak card used a much weaker border, the streak value/action used the lighter teal instead of `primaryTealText`, and the deterministic Android fixture rendered `3/7日` while the published reference headline is `5/7日`. The shared Patient header also still inherited Material's 32/16 typography instead of SwiftUI's 34/17 `largeTitle`/`headline` metrics.

## Capture contract

- iOS: the unchanged Build 51 simulator app produced under C122, launched with `-PatientHistoryAchievementPreview` on iPhone 17e / iOS 26.5. The raw capture is 1170 x 2532.
- Android: API 35 AVD, 1080 x 2400 at 420 dpi, production `HistoryContent` inside the production theme. System UI demo mode fixes 9:41 and removes notification icons.
- Both use Japanese, light appearance, two of three scheduled doses recorded, a five-day streak and a `5/7日` weekly headline.
- Fixtures are deterministic and synthetic. No API, account, token, patient identifier, medication record or Analytics event is used.

## Evidence

| Artifact | File |
|---|---|
| Raw iOS | [`ios-ui-104-published-v105-light.png`](ios-ui-104-published-v105-light.png) |
| Raw Android | [`android-ui-104-published-v105-light.png`](android-ui-104-published-v105-light.png) |
| Normalized iOS | [`ios-ui-104-published-v105-light-normalized.png`](ios-ui-104-published-v105-light-normalized.png) |
| Normalized Android | [`android-ui-104-published-v105-light-normalized.png`](android-ui-104-published-v105-light-normalized.png) |
| Side by side | [`ui-104-published-v105-side-by-side.png`](ui-104-published-v105-side-by-side.png) |
| 50% overlay | [`ui-104-published-v105-overlay-50.png`](ui-104-published-v105-overlay-50.png) |

Normalization resizes both complete device captures to 1080 x 2400 for diagnostic alignment. Raw files remain authoritative.

## Drift closed

- The History header now uses a filled clock with reversed hands, matching `clock.fill` rather than Material's outline-only clock.
- The remaining-count pill uses the same filled-clock meaning at caption size.
- The streak card now uses the published 1.5-unit, 55%-teal accent border and the darker accessible teal for its value and next-step copy.
- The shared Patient header now owns the published 34/17 title/subtitle metrics. Today was recaptured under C122 and Settings inherits the same production component before its own queued visual audit.
- The exact production fixture asserts `2/3回分 記録済み`, `記録済み 2回分`, `残り 1回分`, `5日` and `5/7日` before capture.
- Play screenshots 02 and 03 are regenerated from the refreshed Today and Patient History production-Compose evidence.

## Production source wins over debug-preview-only shortcuts

The simulator preview hand-writes five weekday columns and shortens its taken pill to `記録済み 2`. The shipping `HistoryMonthView.patientSimpleHistory` renders the Monday-first seven-day range and formats the same pill through `caregiver.history.summary.taken` as `記録済み %d回分`. Android therefore preserves seven production days and `記録済み 2回分`; it does not copy preview-only shortcuts into the real UI. This follows the same production-source rule used by C122 for the debug route's missing selected-tab background.

## Accepted platform and adaptive differences

- SF Symbols and the Compose custom/Material equivalents retain native calendar/check details.
- Status/navigation bars and font rasterization remain platform-native.
- The iPhone viewport is 390 points wide while the Android reference is approximately 411 dp. Identical 16/18-unit margins and card content therefore wrap to fewer lines on Android and reveal more of the weekly card in the same full-device capture.

## Verification

- Exact published fixture instrumentation: 1/1 passed on API 35 and emitted the retained Android capture.
- Affected Patient History, Today and Settings instrumentation: 57/57 passed on API 35 with zero failure or skip.
- Complete API-35 connected UI suite: 283/283 passed in four retained shards (66 + 59 + 80 + 78), with zero failure, error or skip and automatic package cleanup.
- Debug JVM: 216/216; Release JVM: 213/213. Debug Lint, Debug/Release assembly, Release APK compatibility, deterministic Play assets and the ten-row release-gate ledger all passed. The Release APK SHA-256 was `0d91d7d51a8e73c225e91df7e2a0d1e3f56ab319b7521f7ca921a5bfe2a6d470`.
- Hosted CI is checked on the final pushed commit before delivery.
- Existing dark/200% and lifecycle fixtures remain part of UI-104; physical spoken TalkBack and Play-installed evidence remain external and C124 closes no RG gate.

## SHA-256

| File | SHA-256 |
|---|---|
| `ios-ui-104-published-v105-light.png` | `ed1bc4197e1621cf1fbbf0544e4adfff721d6f1e9399e38a5b7bc880a7105d33` |
| `android-ui-104-published-v105-light.png` | `2f810b296495bd92666368f1dd35867fa9c5c198a344197323c4ca238ae2318d` |
| `ios-ui-104-published-v105-light-normalized.png` | `69f3bc865b5b12476f48a4ce6dbc33b5e16fc31eb15f3b66a355fa0b0f19b626` |
| `android-ui-104-published-v105-light-normalized.png` | `4fcf0e8dad53084b368dabf338bf8c6d20258a209c075e873ac469fe2ab48d28` |
| `ui-104-published-v105-side-by-side.png` | `ccfc65a1650d459e4505a72c5c28f325b1c67f0b624b30d7849d0c67648c7041` |
| `ui-104-published-v105-overlay-50.png` | `223a34a9a2f94a6f2492db616386741efeda7244bf4018046c488c182c5e62bc` |
| `play-store-assets/phone-ja-JP/03-patient-history.jpg` | `1bce2239df4b84f1111cf8f2c17eb85ed6c1952136e213fc28b170def1f4339f` |
