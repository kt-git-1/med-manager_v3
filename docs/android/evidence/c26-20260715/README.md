# C26 — UI-204 caregiver inventory list source calibration

Date: 2026-07-15

Branch: `android-dev`

Target: API 35 emulator, 1080 x 2400 px, light theme, font scale 1.0

## Source contract

- Production SwiftUI: `ios/MedicationApp/Features/Inventory/InventoryListView.swift`
  - `inventoryHeaderRow`
  - `inventorySummaryTile`
  - `inventoryGuideRow`
  - `filterPickerRow`
  - `inventoryRow`
  - `InventoryIllustrationView`
- App-derived visual reference: `api/public/screenshots/caregiver-inventory.png`
- Same-state Android fixture: `田中 花子`, one low-stock scheduled medicine (`血圧の薬 5 mg`, 4 tablets), one healthy managed medicine, one unconfigured medicine and no ended medicine.

The checked-in iOS image is the in-app tutorial sample rather than the production inventory list. It uses a generic subtitle, omits the production priority action button and uses a tutorial cube illustration. Android follows production SwiftUI where they differ: selected-patient subtitle, actionable priority card and pills/PRN inventory illustration. Shared visual metrics and the 1/2/1/0 state are compared directly.

## Closed drift

- Replaced the pale 54-unit archive icon with the shared 62/50-unit selected-patient avatar and matched the 34/17-unit header hierarchy.
- Rebuilt the summary as a 2 x 2 grid of 124-unit, 16-corner tiles with source-aligned icon/value/label hierarchy, semantic borders and shadows.
- Matched the 18-corner priority guide, 34-unit status symbol, exact current copy and production-only 46-unit priority action.
- Replaced default Material filter chips with 38-unit icon capsules and teal/orange/red selected and unselected treatments.
- Rebuilt inventory cards with 18-unit padding, 62-unit gradient medicine illustration, 22-unit names, inline status capsules and a 34-unit emphasized remaining count.
- Replaced the nested unconfigured card with the current elevated tap guide.
- Removed the Android-only full-width outline detail button; the entire card now opens detail and exposes the named accessibility action while retaining the compact trailing detail hint.
- Kept the 52-unit one-week refill action, low/out/period-ended ordering, filters, server-authoritative mutation flow and stale/retry behavior intact.

## Evidence

- `ios-ui-204-caregiver-inventory-reference-light.png`
  - SHA-256: `ecad553cf10e398de3934e4ada8bbd7ee6f58f867a66920ac1599d90b2f5f5fb`
- `android-ui-204-caregiver-inventory-source-calibrated-light.png`
  - SHA-256: `5b1739404b5601d5f65f3398fe828b5aa1e4a8eb6c45d37ddae162ad56b5c080`
- `ui-204-light-side-by-side.png`
  - SHA-256: `e9491663e7b979146c5ffad076988149bc4a0f4ac887ef29b2ce1afa011ae436`

## Verification

- `CaregiverInventoryScreenTest`: 10/10 on API 35, including list/filter, detail, quick refill, correction, enable, retry, loading/empty/updating and source fixture coverage.
- JVM unit tests, Debug assembly and Lint pass.
- Full API-35 instrumentation suite: 184/184.

## Remaining external/matched work

- Matched iOS empty/error/ended/unconfigured and dark/large-text pairs.
- Physical TalkBack traversal and real caregiver inventory refill/correction verification.
