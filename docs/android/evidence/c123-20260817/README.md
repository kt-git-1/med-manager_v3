# C123 published iOS 1.0.6 Mode Select visual rebaseline

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Finding

C122 established that source review and old runtime captures were insufficient proof of current visual fidelity. Applying the same exact-runtime procedure to UI-001 Mode Select confirms that the shared geometry, typography, role illustrations, text, colors and responsive scroll layout already follow the published SwiftUI implementation. It also exposes two visible symbol drifts that the semantic tests did not cover: Android used a medicine-bottle header instead of the published pills symbol, and tinted both role badge symbols while SwiftUI leaves those symbols at the primary neutral color.

## Capture contract

- iOS: the unmodified `main@432b34c` app built in the fresh C122 DerivedData directory, launched with simulator-only `UITEST_SESSION_BOOTSTRAP=1` on iPhone 17e / iOS 26.5. Analytics already has an explicit disabled decision, so no consent alert obscures the screen. The raw capture is 1170 x 2532.
- Android: API 35 AVD, 1080 x 2400 at 420 dpi, production `ModeSelectScreen` inside the production theme and no Analytics service, which represents the decided/no-alert state without emitting an event. System UI demo mode fixes 9:41 and removes notification icons from the retained marketing-safe capture.
- Both use Japanese, light appearance, no selected role and the shipping role illustrations/copy.
- No API, account, token, patient identifier, medication record or Analytics event is used.

## Evidence

| Artifact | File |
|---|---|
| Raw iOS | [`ios-ui-001-published-v105-light.png`](ios-ui-001-published-v105-light.png) |
| Raw Android | [`android-ui-001-published-v105-light.png`](android-ui-001-published-v105-light.png) |
| Normalized iOS | [`ios-ui-001-published-v105-light-normalized.png`](ios-ui-001-published-v105-light-normalized.png) |
| Normalized Android | [`android-ui-001-published-v105-light-normalized.png`](android-ui-001-published-v105-light-normalized.png) |
| Side by side | [`ui-001-published-v105-side-by-side.png`](ui-001-published-v105-side-by-side.png) |
| 50% overlay | [`ui-001-published-v105-overlay-50.png`](ui-001-published-v105-overlay-50.png) |

Normalization resizes both complete device captures to 1080 x 2400 for diagnostic alignment. Raw files remain authoritative.

## Drift closed

- The 28-unit app badge now uses the shared pills glyph instead of Material's medicine-bottle symbol, matching `pills.fill` semantically and visually.
- Patient and family badge symbols now use the neutral on-surface color while the label text and capsule retain their role tint, matching SwiftUI's actual `Label` styling.
- The exact published copy and two repeated primary actions are asserted before the Android capture.
- Play screenshot 01 is moved from the pre-published C01 image to this production-Compose evidence.

## Accepted platform and adaptive differences

- SF Symbols and the equivalent Compose vector/custom glyph retain native outline details.
- Status/navigation bars and font rasterization remain platform-native.
- The iPhone viewport is 390 points wide while the API-35 reference device is approximately 411 dp wide. The shared 22-unit margins, 112-unit illustrations and text metrics therefore make the iOS patient subtitle wrap while Android keeps it on one line. This is the intended responsive result of the same shipping layout, not a fixed-width divergence.

## Verification

- Exact published fixture instrumentation: 1/1 passed on API 35 and emitted the retained raw Android capture.
- iOS simulator build from the exact pinned source: passed under C122; C123 launches those unchanged build bytes without source modification.
- Full `ModeSelectScreenTest`: 6/6 passed on API 35, including the exact published fixture and existing canonical copy, interaction, 200% reachability and Analytics-consent cases.
- Complete API-35 connected UI suite: 282/282 passed in four retained shards (66 + 59 + 80 + 77), with zero failure, error or skip and automatic package cleanup.
- Debug JVM: 216/216; Release JVM: 213/213; Debug Lint, Debug/Release assembly, Release APK compatibility, deterministic Play assets and release-gate ledger verification all passed.
- Existing dark/200% tests remain part of UI-001; physical spoken TalkBack and Play-installed evidence remain external and C123 closes no RG gate.

## SHA-256

| File | SHA-256 |
|---|---|
| `ios-ui-001-published-v105-light.png` | `24167f8f2ea458039b136d4549a2477126734dcb8b79ad195007ac9dcf25b65d` |
| `android-ui-001-published-v105-light.png` | `bc060f4221257f92dd769e1f0599dceca94059c75e302e9ccea8fc96745591f2` |
| `ios-ui-001-published-v105-light-normalized.png` | `f2b3a099f7e32d5b429f2a07faaebb2529913156e193f739b7a278ddac5d9ef9` |
| `android-ui-001-published-v105-light-normalized.png` | `6a365521be1c732e3cc7c704ff7cd54464b30ea5721da13604d51a7bd198eddf` |
| `ui-001-published-v105-side-by-side.png` | `81a190f09329a06b73742e96a95135f5268b7372f1e1eabbff1111506034bdfb` |
| `ui-001-published-v105-overlay-50.png` | `f3fd34510b491b75dc9e2f51712157bf73600147538f5197a559acf1b6c78600` |
| `play-store-assets/phone-ja-JP/01-mode-select.jpg` | `63901b28a48ba0288148cd05a37e91170d8fca9da4b7df71c7a723adc666dbe7` |
