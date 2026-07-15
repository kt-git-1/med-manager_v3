# C23 — UI-201 caregiver Today timeline source calibration

Date: 2026-07-15

Branch: `android-dev`

Target: API 35 emulator, 1080 x 2400 px, light theme, font scale 1.0

## Source contract

- Current SwiftUI source: `ios/MedicationApp/Features/Today/CaregiverTodayView.swift`
  - `timelineRows`
  - `CaregiverTodayTimelineRow`
  - `CaregiverTodayDoseLine`
- Current Japanese copy: `ios/MedicationApp/Resources/Localizable.strings`
- Same-data fixture: patient `田中 花子`, `08:00 / 12:30 / 19:00 / 22:00`, taken morning, two pending noon medicines, missed evening, and an empty bedtime slot.

The source is authoritative because the current app-derived UI-201 image ends above the timeline. C23 therefore locks the exact production SwiftUI metrics and state hierarchy in an Android production-component fixture rather than inventing a visual reference below the captured viewport.

## Closed drift

- Timeline now always renders all four configured slots, including `予定なし` rows.
- Each row uses the current 14-unit card, slot-colored 42-unit icon, separate slot/time hierarchy and status pill.
- State copy now matches current iOS: `飲みました`, `次に記録`, `まだです`, `飲み忘れ`, `予定なし`, and `在庫不足`.
- Dose lines use a 30-unit medication symbol, two-line display name, `1回%@錠`, a 34-unit status indicator and the current taken-only 24-unit undo action.
- Pending/missed doses are recorded through the 48-unit `%d件をまとめて記録` action; out-of-stock doses are excluded and a fully blocked slot has no action.
- The next-action row uses the current orange tint/border while non-next rows retain their canonical slot color.

## Evidence

- `android-ui-201-caregiver-today-timeline-source-calibrated-light.png`
  - SHA-256: `b7d8d6af268316f24d0268b1a6c6793413c913b83fe11db98b057bfb3926e5ca`
  - The single full-height capture contains taken, next, missed and no-plan rows.

## Verification

- `CaregiverTodayScreenTest`: 13/13 on API 35.
- Caregiver Today + adaptive + large-text + accessibility group: 30/30 on API 35.
- JVM unit tests, Debug assembly and Lint pass.
- Full API-35 instrumentation suite: 182/182.

## Remaining external/matched work

- Matched iOS loading/error/PRN/dark/large-text screenshots.
- Physical TalkBack traversal, OEM font/display scaling and real caregiver API mutation verification.
