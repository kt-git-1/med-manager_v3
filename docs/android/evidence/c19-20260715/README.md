# C19 UI-101 Matched Dark and Maximum-Text Comparison — 2026-07-15

## Matrix

Both platforms use `ja_JP`, `Asia/Tokyo`, 2026-07-15 (Wednesday), a 12:30 noon slot, the same two pending medicines and one PRN medicine. The iOS reference is the unchanged Debug `PatientMarketingScreenshot.today` production-shell preview built from this worktree; Android renders the production `TodayContent` component on API 35.

| Variant | iOS reference | Android result | Diagnostic comparison |
|---|---|---|---|
| Dark, default text | [`ios-ui-101-patient-today-dark.png`](ios-ui-101-patient-today-dark.png) | [`android-ui-101-patient-today-dark-matched.png`](android-ui-101-patient-today-dark-matched.png) | [`side-by-side`](ui-101-dark-side-by-side.png), [`50% overlay`](ui-101-dark-overlay-50.png) |
| Light, maximum supported text | [`ios-ui-101-patient-today-accessibility-xxxl.png`](ios-ui-101-patient-today-accessibility-xxxl.png) | [`android-ui-101-patient-today-font-2.0-matched.png`](android-ui-101-patient-today-font-2.0-matched.png) | [`side-by-side`](ui-101-font-2.0-side-by-side.png), [`50% overlay`](ui-101-font-2.0-overlay-50.png) |

The diagnostic files normalize both raw captures to 1080 x 2400 only for alignment. Raw PNGs remain authoritative; platform status-bar glyphs and exact system font scaling are not treated as app defects.

## Differences found and closed

Source and image comparison found four material Android drifts:

1. Patient shell headers used a pale container with a teal glyph instead of the iOS 62-unit teal circle, white glyph, five-unit card-colored ring and shadow.
2. The next-dose hero and PRN cards used 24 dp corners and 2 dp strokes instead of the iOS 18-unit card family and 1.5-unit accent stroke.
3. The 32-unit slot heading and approximately 20-unit medication names inherited smaller Material typography.
4. The primary bulk action inherited Material's pill shape and 64 dp fixed height instead of the iOS 18-unit corner and 72-unit minimum height.

Android now uses a shared `PatientHeaderIcon` for Today, History and Settings, plus exact UI-101 shape, typography and button metrics. The maximum-text action may wrap on Android because its 2.0 font scale is larger than iOS's scaled title style; it stays fully visible, enabled and reachable, which satisfies the adaptive contract without truncation.

## Regression coverage

- `PatientTodayContentTest.currentIosMatchedTodayFixtureRendersInDarkTheme`
- `PatientTodayContentTest.currentIosMatchedTodayFixtureRendersAtTwoHundredPercentFontScale`
- The complete `PatientTodayContentTest` class passes 23/23 on API 35 and emits both Android raw fixtures from the production component.
- The unchanged iOS Debug reference build succeeds on the iPhone 17e simulator with iOS 26.5.

This closes the typical-content UI-101 matched dark/maximum-text pair. Exceptional-state matched variants, physical TalkBack and physical-device rendering remain in C01/C06/Gate I.
