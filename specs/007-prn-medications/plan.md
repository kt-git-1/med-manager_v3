# Implementation Plan: PRN Medications

**Branch**: `007-prn-medications` | **Date**: 2026-02-07 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/007-prn-medications/spec.md`

## Summary

Add PRN medications with caregiver setup, patient recording from Today, history integration, and inventory adjustments, while excluding PRN from notifications and scheduled dose flows. The plan covers new PRN record data model, API endpoints with role rules, and iOS UI updates with full-screen blocking overlays and tests-first sequencing.

## Technical Context

**Language/Version**: TypeScript (Node.js >=20), Swift 6.2.1 (SwiftUI, iOS 26 SDK)  
**Primary Dependencies**: Next.js App Router, Prisma, Supabase Realtime; SwiftUI, XCTest  
**Storage**: PostgreSQL via Prisma  
**Testing**: Vitest (API unit/integration/contract), XCTest (iOS unit + UI smoke)  
**Target Platform**: Web API + iOS app  
**Project Type**: Mobile + API  
**Performance Goals**: PRN record creation shows confirmation within 3 seconds for 95% of attempts; history reflects PRN updates within 5 seconds  
**Constraints**: No PRN notifications; PRN does not create scheduled doses; server time for takenAt; patient cannot delete PRN records; full-screen "更新中" overlay during network calls  
**Scale/Scope**: MVP limited to caregiver setup, patient recording, history display, and inventory adjustments

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: Pass (spec is the source of truth).
- Traceability: Pass (plan ties steps to FR/NFR and tests).
- Test strategy: Pass (contract/integration/UI smoke; no external calls in CI).
- Security & privacy: Pass (deny-by-default, concealment).
- Performance guardrails: Pass (explicit timing targets).
- UX/accessibility: Pass (blocking overlay, i18n-ready copy).
- Documentation: Pass (contracts, quickstart, data model updated).

## Project Structure

### Documentation (this feature)

```text
specs/007-prn-medications/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── openapi.yaml
└── tasks.md
```

### Source Code (repository root)

```text
api/
├── app/api/
│   ├── patient/
│   │   └── prn-dose-records/
│   └── patients/
│       └── [patientId]/
│           ├── prn-dose-records/
│           │   └── [prnRecordId]/
│           ├── history/
│           └── medications/
├── src/
│   ├── services/
│   ├── repositories/
│   ├── validators/
│   └── auth/
└── tests/
    ├── contract/
    ├── integration/
    └── unit/

ios/MedicationApp/
├── Features/
│   ├── MedicationList/
│   ├── MedicationForm/
│   ├── Today/
│   └── History/
├── Networking/
├── Shared/
│   └── Views/
└── Tests/
    ├── Medication/
    ├── Today/
    └── History/
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). PRN APIs live under patient routes; iOS updates target existing Today, Medication form/list, and History modules.

## Complexity Tracking

No constitution violations.

## Phase 0: Outline & Research

### Research Tasks

- Confirm server-timestamp usage for PRN takenAt to avoid device clock drift.
- Confirm inventory adjustment hooks for PRN create/delete align with existing inventory rules.
- Confirm history day payload extension pattern used by 004 for new item types.
- Confirm patient vs caregiver authorization boundaries for PRN create/delete.

### Output

- `research.md` with decisions, rationales, and alternatives.

## Phase 1: Design & Contracts

### Data Model

- Define PRN medication fields, PRN dose record entity, and inventory adjustment rules for create/delete.
- Capture validation rules for PRN vs scheduled medications and role-based delete constraints.

### API Contracts

- Document PRN record create/delete endpoints for patient and caregiver roles.
- Document medication payload extensions for PRN fields.
- Document history day response extension for PRN items (and optional month summary counts).

### Agent Context Update

- Run `.specify/scripts/bash/update-agent-context.sh cursor-agent`.

### Output

- `data-model.md`
- `contracts/openapi.yaml`
- `quickstart.md`
- Updated agent context

### Constitution Check (Post-Design)

- Spec-Driven Development: Pass
- Traceability: Pass
- Test strategy: Pass
- Security & privacy: Pass
- Performance guardrails: Pass
- UX/accessibility: Pass
- Documentation: Pass

## Phase 2: Implementation Plan (Tasks)

### Phase 1: Tests / Contracts (test-first)

1) Contract tests for PRN endpoints and medication fields
   - **Done**: Request/response shapes validated; medication create/update includes `isPrn` and `prnInstructions`.
   - **Files**: `api/tests/contract/prn-dose-records.contract.test.ts`, `api/tests/contract/medications.contract.test.ts`
   - **Tests**: `cd api && npm test`

2) Integration tests for PRN create/delete and inventory adjustments
   - **Done**: Patient can create but cannot delete; caregiver can create/delete; inventory adjusts and clamps to zero; non-PRN medication rejected; concealment enforced.
   - **Files**: `api/tests/integration/prn-dose-records.test.ts`
   - **Tests**: `cd api && npm test`

3) iOS unit/UI smoke tests for PRN flows
   - **Done**: PRN section view model mapping; confirm dialog only triggers one API call; caregiver badges + PRN toggle hide schedule; patient PRN record create; no undo UI; blocking overlay verified.
   - **Files**: `ios/MedicationApp/Tests/Today/*`, `ios/MedicationApp/Tests/Medication/*`, `ios/MedicationApp/Tests/History/*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 2: DB / Migrations

4) Add PRN fields and PRN record table
   - **Done**: Medication includes `isPrn`, `prnInstructions`; new `prn_dose_records` table with indexes and defaults.
   - **Files**: `api/prisma/schema.prisma`, `api/prisma/migrations/*`
   - **Tests**: `cd api && npm test`

### Phase 3: API

5) Medication create/update PRN support
   - **Done**: Validation enforces regimen required when `isPrn=false`, omitted when `isPrn=true`.
   - **Files**: `api/src/validators/medicationValidator.ts`, `api/app/api/medications/*`
   - **Tests**: `cd api && npm test`

6) PRN record create/delete endpoints + inventory integration
   - **Done**: Patient create and caregiver create/delete implemented; inventory adjusts on create/delete when enabled; server sets `takenAt`.
   - **Files**: `api/app/api/patients/[patientId]/prn-dose-records/route.ts`, `api/app/api/patients/[patientId]/prn-dose-records/[prnRecordId]/route.ts`, `api/src/services/prnDoseRecordService.ts`
   - **Tests**: `cd api && npm test`

7) History integration
   - **Done**: Day detail includes PRN items; month summary includes optional PRN counts if available.
   - **Files**: `api/app/api/patients/[patientId]/history/day/route.ts`, `api/app/api/patient/history/day/route.ts`
   - **Tests**: `cd api && npm test`

8) Notification exclusion
   - **Done**: PRN medications are excluded from local notification scheduling and refresh logic.
   - **Files**: `ios/MedicationApp/Features/Notifications/*`, `api/src/services/notificationService.ts`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 4: iOS Caregiver UI

9) Medication list badges and PRN toggle
   - **Done**: List shows "定時" or "頓服" badge; PRN toggle hides schedule fields and shows PRN instructions input.
   - **Files**: `ios/MedicationApp/Features/MedicationList/*`, `ios/MedicationApp/Features/MedicationForm/*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 5: iOS Patient UI

10) Today PRN section and record creation
   - **Done**: PRN section lists PRN meds; "飲んだ" shows confirmation; double-submit prevention via overlay + disabled button; success banner shown.
   - **Files**: `ios/MedicationApp/Features/Today/*`, `ios/MedicationApp/Networking/*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 6: History UI Integration

11) Day history PRN entries (and optional calendar counts)
   - **Done**: Day history shows PRN entries in time order; calendar shows PRN count per day if available.
   - **Files**: `ios/MedicationApp/Features/History/*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 7: Docs

12) Update quickstart, contracts, and data model
   - **Done**: Spec docs updated with endpoints, rules, and test commands.
   - **Files**: `specs/007-prn-medications/quickstart.md`, `specs/007-prn-medications/contracts/openapi.yaml`, `specs/007-prn-medications/data-model.md`
   - **Tests**: N/A (doc update)
