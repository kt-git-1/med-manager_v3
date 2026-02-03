---

description: "Task list for dose recording implementation"
---

# Tasks: Dose Recording (003)

**Input**: Design documents from `/specs/003-dose-recording/`  
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md  
**Tests**: Required per spec (contract, integration, iOS UI smoke)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)  
- **[Story]**: User story label (US1, US2, US3)
- Paths are relative to repo root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prep shared structure for API + iOS changes

- [x] T001 [P] Confirm existing API auth/session helpers in `api/src/auth/` for patient and caregiver access
- [x] T002 [P] Confirm iOS feature module location for new Today screen under `ios/MedicationApp/Features/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data model and shared services required for all stories  
**⚠️ CRITICAL**: Must complete before any user story work

- [x] T003 Add DoseRecord model and unique constraint to `api/prisma/schema.prisma`
- [x] T004 Create migration for dose records in `api/prisma/migrations/` (via Prisma migrate)
- [x] T005 [P] Add dose record repository in `api/src/repositories/doseRecordRepo.ts`
- [x] T006 [P] Add dose record service logic (idempotent create/delete) in `api/src/services/doseRecordService.ts`
- [x] T007 [P] Add validation for dose record create/delete in `api/src/validators/doseRecord.ts`
- [x] T008 Update schedule derivation to include effectiveStatus in `api/src/services/scheduleService.ts`
- [x] T009 Update schedule response shaping in `api/src/services/scheduleResponse.ts`
- [x] T010 [P] Add shared DTOs for dose records and schedule in `ios/MedicationApp/Networking/DTOs/DoseRecordDTO.swift`

**Checkpoint**: Foundation ready for story implementation

---

## Phase 3: User Story 1 - Patient records a taken dose (Priority: P1) 🎯 MVP

**Goal**: Patient can record a scheduled dose as taken with confirmation and idempotent behavior.

**Independent Test**: Patient can open Today, tap “Taken,” confirm, and see taken status after refresh.

### Tests for User Story 1

- [x] T011 [P] [US1] Contract tests for patient endpoints in `api/tests/contract/dose-recording-patient.contract.test.ts`
- [x] T012 [P] [US1] Integration test for idempotent create in `api/tests/integration/dose-recording-patient.test.ts`
- [x] T013 [P] [US1] Integration test for patient revoke access in `api/tests/integration/patient-session-revoke.test.ts`
- [x] T014 [P] [US1] iOS UI smoke for patient Today flow in `ios/MedicationApp/Tests/TodayPatientFlowTests.swift`

### Implementation for User Story 1

- [x] T015 [US1] Add patient today endpoint in `api/app/api/patient/today/route.ts`
- [x] T016 [US1] Add patient dose record create endpoint in `api/app/api/patient/dose-records/route.ts`
- [x] T017 [US1] Wire patient auth verification in `api/src/middleware/auth.ts` for patient routes
- [x] T018 [US1] Add patient Today view model in `ios/MedicationApp/Features/Today/PatientTodayViewModel.swift`
- [x] T019 [US1] Add patient Today UI with large “Taken” button in `ios/MedicationApp/Features/Today/PatientTodayView.swift`
- [x] T020 [US1] Add confirm dialog and “already recorded” feedback in `ios/MedicationApp/Features/Today/PatientTodayView.swift`
- [x] T021 [US1] Add full-screen updating overlay using shared container in `ios/MedicationApp/Shared/Views/FullScreenContainer.swift`
- [x] T022 [US1] Add API client methods for patient today + create in `ios/MedicationApp/Networking/APIClient.swift`

**Checkpoint**: User Story 1 works end-to-end for patient flow

---

## Phase 4: User Story 2 - Caregiver records or cancels a taken dose (Priority: P2)

**Goal**: Caregiver can create or delete a taken record for a selected patient, with concealment for non-owned patients.

**Independent Test**: Caregiver can record and delete a taken dose for an owned patient; non-owned returns not found.

### Tests for User Story 2

- [x] T023 [P] [US2] Contract tests for caregiver endpoints in `api/tests/contract/dose-recording-caregiver.contract.test.ts`
- [x] T024 [P] [US2] Integration test for caregiver create/delete in `api/tests/integration/dose-recording-caregiver.test.ts`
- [x] T025 [P] [US2] Integration test for concealment on non-owned patient in `api/tests/integration/caregiver-concealment.test.ts`
- [x] T026 [P] [US2] iOS UI smoke for caregiver record/delete flow in `ios/MedicationApp/Tests/TodayCaregiverFlowTests.swift`

### Implementation for User Story 2

- [x] T027 [US2] Add caregiver today endpoint in `api/app/api/patients/[patientId]/today/route.ts`
- [x] T028 [US2] Add caregiver dose record create/delete endpoints in `api/app/api/patients/[patientId]/dose-records/route.ts`
- [x] T029 [US2] Enforce caregiver ownership concealment in `api/src/services/doseRecordService.ts`
- [x] T030 [US2] Add caregiver Today view model in `ios/MedicationApp/Features/Today/CaregiverTodayViewModel.swift`
- [x] T031 [US2] Add caregiver Today UI with record/delete actions in `ios/MedicationApp/Features/Today/CaregiverTodayView.swift`
- [x] T032 [US2] Add API client methods for caregiver today/create/delete in `ios/MedicationApp/Networking/APIClient.swift`

**Checkpoint**: User Story 2 works end-to-end for caregiver flow

---

## Phase 5: User Story 3 - Patients see missed doses and reminders (Priority: P3)

**Goal**: Patients see missed emphasis and receive reminder notifications with auto-refresh behavior.

**Independent Test**: A dose passes the 60-minute threshold and shows missed; reminder scheduled and delivered.

### Tests for User Story 3

- [ ] T033 [P] [US3] Integration test for missed status derivation in `api/tests/integration/dose-missed-status.test.ts`
- [ ] T034 [P] [US3] iOS test for reminder scheduling in `ios/MedicationApp/Tests/ReminderSchedulingTests.swift`
- [ ] T035 [P] [US3] Integration test for no persisted missed/pending records in `api/tests/integration/dose-recording-persistence.test.ts`
- [ ] T036 [P] [US3] iOS test for max 2 reminders per dose in `ios/MedicationApp/Tests/ReminderSchedulingTests.swift`

### Implementation for User Story 3

- [ ] T037 [US3] Add missed emphasis styling in `ios/MedicationApp/Features/Today/PatientTodayView.swift`
- [ ] T038 [US3] Add local notification scheduler in `ios/MedicationApp/Services/ReminderService.swift`
- [ ] T039 [US3] Trigger reminder scheduling on refresh/foreground in `ios/MedicationApp/Features/Today/PatientTodayViewModel.swift`
- [ ] T040 [US3] Add auto-refresh hooks on appear/foreground in `ios/MedicationApp/Features/Today/PatientTodayViewModel.swift`
- [ ] T041 [US3] Add localized strings for reminders/confirm copy in `ios/MedicationApp/Resources/Localizable.strings`

**Checkpoint**: User Story 3 behavior complete and independently testable

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, validation, and cross-story hardening

- [ ] T042 [P] Update API error mapping docs in `api/src/middleware/error.ts` if needed for new errors
- [ ] T043 [P] Add logging for dose record operations in `api/src/logging/logger.ts`
- [ ] T044 [P] Run basic accessibility verification for Today UI (Dynamic Type + VoiceOver labels) in `ios/MedicationApp/Features/Today/`
- [ ] T045 [P] Add Today refresh performance validation note (<5s) in `specs/003-dose-recording/quickstart.md`
- [ ] T046 [P] Update quickstart validation steps in `specs/003-dose-recording/quickstart.md`
- [ ] T047 Run full API test suite from `api/` and iOS tests from `ios/MedicationApp`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup completion
- **User Stories (Phase 3+)**: Depend on Foundational completion
- **Polish (Phase 6)**: Depends on all selected stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational
- **US2 (P2)**: Can start after Foundational
- **US3 (P3)**: Can start after Foundational

### Within Each User Story

- Tests (if included) should be written and fail before implementation
- Services depend on repositories
- Endpoints depend on services and validators

---

## Parallel Example: User Story 1

```bash
Task: "Contract tests for patient endpoints in api/tests/contract/dose-recording-patient.contract.test.ts"
Task: "Integration test for idempotent create in api/tests/integration/dose-recording-patient.test.ts"
Task: "iOS UI smoke for patient Today flow in ios/MedicationApp/Tests/TodayPatientFlowTests.swift"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 → Phase 2  
2. Implement US1 with tests  
3. Validate patient flow end-to-end

### Incremental Delivery

1. Add US2 after US1 passes  
2. Add US3 after US2 passes  
3. Finish polish tasks
