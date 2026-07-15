# C25 — UI-203 caregiver medication form source calibration

Date: 2026-07-15

Branch: `android-dev`

Target: API 35 emulator, 1080 x 2400 px, light theme, font scale 1.0

## Source contract

- Production SwiftUI: `ios/MedicationApp/Features/MedicationForm/MedicationFormView.swift`
  - `formHeroSection`
  - basic-information `Section`
  - `medicationTypeSection`
  - `typeChoiceButton`
  - `guidePill`
- App-derived visual reference: `api/public/screenshots/caregiver-medication-form.png`
- Same-data Android fixture: new scheduled medication named `血圧の薬`, strength `5 mg`, one unit per intake and morning/evening slots.

The iOS image is an app-derived add-form reference populated with representative values. Android drives the production form through its public UI before capture; fixture values are not compiled into production behavior. Android retains its explicit Cancel action because this editor is hosted inside the medication tab rather than an iOS navigation container.

## Closed drift

- Rebuilt the form chrome with a centered 17-unit title and retained an explicit, reachable Cancel action.
- Matched the 20-corner hero card, 54-unit two-pill symbol, 22-unit title, current scheduled/PRN help copy, accent border/shadow and three live completion capsules.
- Consolidated name, strength/unit and per-intake count from three Material form cards into the iOS grouped-row hierarchy.
- Added the current basic-information header/help text, row-level semantic colors and compact half-unit stepper.
- Replaced free-form-only unit entry with an editable field plus the current dosage-unit selection list, including the iOS `不明` behavior that clears/disables strength.
- Replaced two compact type chips with full 16-corner, icon-bearing scheduled/PRN choice cards, exact titles/subtitles and selected-state treatment.
- Preserved regular/PRN switching, schedule/date/inventory/notes validation, create/edit/delete API behavior, blocking mutation overlay and every established automation tag.

## Evidence

- `ios-ui-203-caregiver-medication-form-reference-light.png`
  - SHA-256: `536faf53cdcafcd541ecb2f1093acdad8dd8dd776db593a03511502061df8d68`
- `android-ui-203-caregiver-medication-form-source-calibrated-light.png`
  - SHA-256: `12be0c63049a6255e7bf1bc65c8ceaf8605b1c43dc31792a91fd5cba548516a4`
- `ui-203-light-side-by-side.png`
  - SHA-256: `8a115ea6213a87e166f76c86d0b124536817ddf4c7672c73dd9e8a47de20e576`

## Verification

- `CaregiverMedicationScreenTest`: 12/12 on API 35, including add/edit/PRN/weekly/delete and dosage-unit picker coverage.
- JVM unit tests, Debug assembly and Lint pass.
- Full API-35 instrumentation suite: 184/184.

## Remaining external/matched work

- Matched iOS edit/PRN/error and dark/large-text pairs.
- Physical TalkBack traversal, OEM keyboard/picker behavior and real caregiver medication create/edit/archive verification.
