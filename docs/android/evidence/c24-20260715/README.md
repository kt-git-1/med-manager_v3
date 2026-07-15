# C24 — UI-202 caregiver medication list source calibration

Date: 2026-07-15

Branch: `android-dev`

Target: API 35 emulator, 1080 x 2400 px, light theme, font scale 1.0

## Source contract

- Production SwiftUI: `ios/MedicationApp/Features/MedicationList/MedicationListView.swift`
  - `medicationHeaderRow`
  - `medicationOverviewRow`
  - `medicationFilterRow`
  - `medicationRow`
  - `MedicationMetricTile`
- App-derived visual reference: `api/public/screenshots/caregiver-medications.png`
- Reference implementation: `CaregiverTutorialSampleView` in `CaregiverHomeView.swift`
- Same-data Android fixture: `田中 花子`, three active medicines, two scheduled, one PRN and zero ended; scheduled items use `朝・昼` / `夕` and inventory 18 / 10 tablets.

The checked-in iOS image is the in-app tutorial sample, not the production list. It intentionally uses the explanatory subtitle, omits the production `追加` action and composes strength into its sample names. Android therefore follows the production SwiftUI where these differ: patient-name subtitle, 44-unit add action and a separate dosage detail line. Shared visual metrics are compared directly.

## Closed drift

- Replaced the 54-unit pale initial avatar with the shared 62/50-unit current caregiver avatar.
- Matched the 34-unit title, 17-unit patient line and compact 44-unit capsule add action with exact `追加` copy.
- Rebuilt the overview as 2 x 2, 124-unit, 18-corner metric tiles with 30-unit status icons, tint borders and shadows.
- Replaced Material default filter chips with current 38-unit icon capsules and selected/unselected tint treatment.
- Matched 18-unit medication cards, 18-unit padding, 62-unit gradient symbols, accent borders/shadows and card-level edit behavior.
- Added the current title/type badge hierarchy, icon-bearing dosage/dose/schedule lines, inventory status capsule and 42-unit inset edit action.
- Added a code-native two-pill glyph so scheduled medicine symbols follow the iOS `pills.fill` silhouette instead of the stale Android bottle icon.
- Ended medicines remain separated by section/filter and no longer add an Android-only duplicate `終了` badge.

## Evidence

- `ios-ui-202-caregiver-medications-tutorial-reference-light.png`
  - SHA-256: `ad2af4ad1d42dc393c8f37e5c2e29429fdf69e1cd9847d463cabc1c5a5a3486d`
- `android-ui-202-caregiver-medications-source-calibrated-light.png`
  - SHA-256: `5fc64aa24be575cb93eb0cdbc10be1544c3c46b2dcc155ce6dfbbcdc46b41b52`
- `ui-202-light-side-by-side.png`
  - SHA-256: `f72495500b67a23a013154d4e0942659d7f3dce64ca39ef60be94500c5e73ebb`

## Verification

- `CaregiverMedicationScreenTest`: 11/11 on API 35.
- Medication + adaptive + dark/200%-font + accessibility group: 28/28 on API 35.
- JVM unit tests, Debug assembly and Lint pass.
- Full API-35 instrumentation suite: 183/183.

## Remaining external/matched work

- Matched production iOS list capture, exceptional states and dark/large-text pairs.
- Physical TalkBack traversal and real caregiver medication create/edit/archive verification.
