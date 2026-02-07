# Tasks: PRN Medications

**Input**: Design documents from `/specs/007-prn-medications/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1, US2, US3
- Each task includes Why, Files, Done criteria, Test command

---

## Phase 1: Tests (Required First)

**Purpose**: Tests-first coverage for API + iOS requirements.

### Backend contract tests

- [x] T001 [P] [US1] Add contract test for medication PRN fields in `api/tests/contract/medications.prn.contract.test.ts` | Why: validate `isPrn`/`prnInstructions` in create/update | Files: `api/tests/contract/medications.prn.contract.test.ts` | Done: tests assert fields present and validation responses | Test: `cd api && npm test`
- [x] T002 [P] [US2] Add contract test for PRN create endpoint shape in `api/tests/contract/prn-dose-records.contract.test.ts` | Why: lock POST payload/response | Files: `api/tests/contract/prn-dose-records.contract.test.ts` | Done: schema matches `PrnDoseRecordCreateResponse` and errors mapped | Test: `cd api && npm test`
- [x] T003 [P] [US3] Add contract test for history day PRN items in `api/tests/contract/history.prn.contract.test.ts` | Why: ensure PRN items appear in day payload | Files: `api/tests/contract/history.prn.contract.test.ts` | Done: response includes `prnItems[]` with required fields | Test: `cd api && npm test`

### Backend integration tests

- [x] T004 [US2] Add integration test for patient PRN create + deny delete in `api/tests/integration/prn-dose-records.test.ts` | Why: enforce patient-only create and no delete | Files: `api/tests/integration/prn-dose-records.test.ts` | Done: patient POST succeeds, DELETE returns 403/404 per policy | Test: `cd api && npm test`
- [x] T005 [US2] Add integration test for caregiver create/delete + concealment in `api/tests/integration/prn-dose-records.test.ts` | Why: linked caregiver rules + 404 conceal | Files: `api/tests/integration/prn-dose-records.test.ts` | Done: linked caregiver can create/delete; non-linked gets 404 | Test: `cd api && npm test`
- [x] T006 [US2] Add integration test for inventory adjustments in `api/tests/integration/prn-dose-records.test.ts` | Why: inventory changes on create/delete | Files: `api/tests/integration/prn-dose-records.test.ts` | Done: decrement on create, increment on delete, clamp >= 0 | Test: `cd api && npm test`
- [x] T007 [US2] Add integration test for reject non-PRN medication in `api/tests/integration/prn-dose-records.test.ts` | Why: enforce `isPrn=true` rule | Files: `api/tests/integration/prn-dose-records.test.ts` | Done: POST returns 422 when medication is not PRN | Test: `cd api && npm test`

### iOS unit/UI smoke tests

- [x] T008 [P] [US2] Add unit test for PRN section filtering in `ios/MedicationApp/Tests/Today/PrnSectionViewModelTests.swift` | Why: ensure isPrn filtering | Files: `ios/MedicationApp/Tests/Today/PrnSectionViewModelTests.swift` | Done: view model returns only PRN meds | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T009 [P] [US2] Add unit test for confirm debounce in `ios/MedicationApp/Tests/Today/PrnConfirmTests.swift` | Why: prevent double submit | Files: `ios/MedicationApp/Tests/Today/PrnConfirmTests.swift` | Done: confirm triggers exactly one API call | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T010 [P] [US1] Add UI smoke test for caregiver badges + PRN toggle in `ios/MedicationApp/Tests/Medication/PrnMedicationUiTests.swift` | Why: verify list badges + form behavior | Files: `ios/MedicationApp/Tests/Medication/PrnMedicationUiTests.swift` | Done: "定時/頓服" badges visible; schedule hides on PRN toggle | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T011 [P] [US2] Add UI smoke test for Today PRN record flow in `ios/MedicationApp/Tests/Today/PrnTodayUiTests.swift` | Why: confirm create flow | Files: `ios/MedicationApp/Tests/Today/PrnTodayUiTests.swift` | Done: PRN section shows, confirm creates record, no undo UI | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T012 [P] [US2] Add UI smoke test for full-screen overlay during PRN network calls in `ios/MedicationApp/Tests/Today/PrnOverlayUiTests.swift` | Why: block interactions on network | Files: `ios/MedicationApp/Tests/Today/PrnOverlayUiTests.swift` | Done: overlay appears and blocks taps while request in-flight | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

---

## Phase 2: DB migrations / schema

**Purpose**: Add PRN fields and PRN record table with indexes.

- [x] T013 Add `isPrn` + `prnInstructions` to Medication in `api/prisma/schema.prisma` | Why: persist PRN metadata | Files: `api/prisma/schema.prisma` | Done: fields added with defaults/nullability per spec | Test: `cd api && npm test`
- [x] T014 Add `prn_dose_records` model + indexes in `api/prisma/schema.prisma` | Why: store PRN records | Files: `api/prisma/schema.prisma` | Done: fields + indexes `(patientId, takenAt desc)` and `(patientId, medicationId, takenAt desc)` | Test: `cd api && npm test`
- [x] T015 Create migration for PRN schema changes in `api/prisma/migrations/*` | Why: apply DB changes | Files: `api/prisma/migrations/*` | Done: migration runs cleanly and aligns with schema | Test: `cd api && npm test`

---

## Phase 3: API implementation (+ inventory integration)

**Purpose**: Add PRN APIs, validation, inventory adjustments, and security rules.

- [x] T016 [US1] Update medication validators for PRN rules in `api/src/validators/medicationValidator.ts` | Why: enforce schedule required only when not PRN | Files: `api/src/validators/medicationValidator.ts` | Done: PRN bypasses regimen validation, non-PRN requires schedule | Test: `cd api && npm test`
- [x] T017 [US1] Update medication create/update handlers for PRN fields in `api/app/api/medications/route.ts` and `api/app/api/medications/[medicationId]/route.ts` | Why: accept new fields | Files: `api/app/api/medications/route.ts`, `api/app/api/medications/[medicationId]/route.ts` | Done: payload persists PRN fields and respects validation | Test: `cd api && npm test`
- [x] T018 [US2] Add PRN record service and repository in `api/src/services/prnDoseRecordService.ts` and `api/src/repositories/prnDoseRecordRepository.ts` | Why: encapsulate create/delete logic | Files: `api/src/services/prnDoseRecordService.ts`, `api/src/repositories/prnDoseRecordRepository.ts` | Done: create/delete handles role rules, server `takenAt`, inventory adjustments | Test: `cd api && npm test`
- [x] T019 [US2] Implement POST PRN endpoint in `api/app/api/patients/[patientId]/prn-dose-records/route.ts` | Why: enable patient/caregiver create | Files: `api/app/api/patients/[patientId]/prn-dose-records/route.ts` | Done: linked auth enforced, rejects non-PRN meds, returns record + inventory snapshot | Test: `cd api && npm test`
- [x] T020 [US2] Implement DELETE PRN endpoint in `api/app/api/patients/[patientId]/prn-dose-records/[prnRecordId]/route.ts` | Why: caregiver-only delete | Files: `api/app/api/patients/[patientId]/prn-dose-records/[prnRecordId]/route.ts` | Done: caregiver-only, 404 conceal for non-linked, inventory increment on delete | Test: `cd api && npm test`
- [x] T021 [US3] Extend history day payload to include PRN items in `api/app/api/patient/history/day/route.ts` and `api/app/api/patients/[patientId]/history/day/route.ts` | Why: history integration | Files: `api/app/api/patient/history/day/route.ts`, `api/app/api/patients/[patientId]/history/day/route.ts` | Done: `prnItems[]` included with takenAt/medicationName/quantity/actorType | Test: `cd api && npm test`
- [x] T022 [US3] Extend history month summary with optional PRN counts in `api/app/api/patient/history/month/route.ts` and `api/app/api/patients/[patientId]/history/month/route.ts` | Why: optional calendar summary | Files: `api/app/api/patient/history/month/route.ts`, `api/app/api/patients/[patientId]/history/month/route.ts` | Done: response includes `prnCountByDay` when available | Test: `cd api && npm test`
- [x] T023 [US2] Exclude PRN from notification scheduling in `api/src/services/scheduleService.ts` | Why: PRN must not notify | Files: `api/src/services/scheduleService.ts` | Done: PRN meds filtered out of generated schedules | Test: `cd api && npm test`

---

## Phase 4: iOS caregiver UI (badges + PRN toggle in add/edit)

**Purpose**: Caregiver list badges and PRN medication form behavior.

- [x] T024 [US1] Add "定時/頓服" badge rendering in medication list in `ios/MedicationApp/Features/MedicationList/*` | Why: caregiver sees type at a glance | Files: `ios/MedicationApp/Features/MedicationList/*` | Done: badge labels map to `isPrn` and are localized | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T025 [US1] Add PRN toggle + instructions field in medication form in `ios/MedicationApp/Features/MedicationForm/*` | Why: caregiver can set PRN and notes | Files: `ios/MedicationApp/Features/MedicationForm/*` | Done: PRN toggle persists `isPrn` and optional `prnInstructions` | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T026 [US1] Hide/disable schedule inputs when PRN is on in `ios/MedicationApp/Features/MedicationForm/*` | Why: enforce schedule rules | Files: `ios/MedicationApp/Features/MedicationForm/*` | Done: schedule UI hidden/disabled when PRN on; required when off | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T027 [US1] Ensure full-screen overlay used for medication save requests in `ios/MedicationApp/Features/MedicationForm/*` | Why: block interactions on network | Files: `ios/MedicationApp/Features/MedicationForm/*`, `ios/MedicationApp/Shared/Views/*` | Done: overlay blocks during create/update | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

---

## Phase 5: iOS patient UI (Today PRN section + confirm)

**Purpose**: Patient PRN recording flow from Today.

- [x] T028 [US2] Add PRN section UI in Today view in `ios/MedicationApp/Features/Today/PatientTodayView.swift` | Why: patient can see PRN meds | Files: `ios/MedicationApp/Features/Today/PatientTodayView.swift` | Done: PRN cards show name + dosage/notes and big "飲んだ" button | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T029 [US2] Add confirm dialog + API call for PRN create in `ios/MedicationApp/Features/Today/*` and `ios/MedicationApp/Networking/*` | Why: confirm before recording | Files: `ios/MedicationApp/Features/Today/*`, `ios/MedicationApp/Networking/*` | Done: confirmation required; server `takenAt`; success toast/banner shown | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T030 [US2] Add double-submit prevention in PRN flow in `ios/MedicationApp/Features/Today/*` | Why: avoid duplicate records | Files: `ios/MedicationApp/Features/Today/*`, `ios/MedicationApp/Shared/Views/*` | Done: button disabled + overlay during request | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T031 [US2] Remove any undo affordance for PRN records in patient UI in `ios/MedicationApp/Features/Today/*` | Why: patient cannot delete PRN record | Files: `ios/MedicationApp/Features/Today/*` | Done: no delete/undo controls for PRN entries | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

---

## Phase 6: History integration (API payload + iOS views)

**Purpose**: Show PRN history in day detail and optional calendar counts.

- [x] T032 [US3] Add PRN items mapping in history models in `ios/MedicationApp/Features/History/*` | Why: display PRN records | Files: `ios/MedicationApp/Features/History/*` | Done: PRN items parsed with takenAt/medicationName/quantity/actorType | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T033 [US3] Render PRN entries in day history UI in `ios/MedicationApp/Features/History/*` | Why: time-ordered PRN history | Files: `ios/MedicationApp/Features/History/*` | Done: PRN shows as "頓服: {薬名} HH:mm" in order | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T034 [US3] Add optional PRN count to calendar summary in `ios/MedicationApp/Features/History/*` | Why: show daily PRN count when available | Files: `ios/MedicationApp/Features/History/*` | Done: calendar includes "頓服 n回" if API provides counts | Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

---

## Phase 7: Docs (quickstart + openapi + data-model)

**Purpose**: Document PRN behavior, API, and data model.

- [x] T035 Update PRN quickstart in `specs/007-prn-medications/quickstart.md` | Why: onboarding and test commands | Files: `specs/007-prn-medications/quickstart.md` | Done: PRN create/record/delete/inventory effects documented | Test: N/A (docs)
- [x] T036 Update PRN data model in `specs/007-prn-medications/data-model.md` | Why: keep spec artifacts aligned | Files: `specs/007-prn-medications/data-model.md` | Done: entities and rules reflect final schema | Test: N/A (docs)
- [x] T037 Update PRN OpenAPI contract in `specs/007-prn-medications/contracts/openapi.yaml` | Why: contract alignment | Files: `specs/007-prn-medications/contracts/openapi.yaml` | Done: endpoints/payloads updated to final behavior | Test: N/A (docs)

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 (Tests) → Phase 2 (DB) → Phase 3 (API) → Phase 4 (Caregiver UI) → Phase 5 (Patient UI) → Phase 6 (History UI) → Phase 7 (Docs)

### User Story Dependencies

- **US1 (P1)**: Starts after DB + API validation tasks (T013–T017) are complete.
- **US2 (P2)**: Starts after PRN endpoints + services (T018–T020) are complete.
- **US3 (P3)**: Starts after history payload tasks (T021–T022) are complete.

### Parallel Opportunities

- Contract tests (T001–T003) can run in parallel.
- Integration tests (T004–T007) can run in parallel once fixtures exist.
- iOS unit/UI tests (T008–T012) can run in parallel.
- UI tasks within a phase marked [P] can be parallelized by feature area.

---

## Parallel Example: User Story 1

```bash
Task: "Add contract test for medication PRN fields in api/tests/contract/medications.prn.contract.test.ts"
Task: "Add UI smoke test for caregiver badges + PRN toggle in ios/MedicationApp/Tests/Medication/PrnMedicationUiTests.swift"
```

---

## Independent Test Criteria per Story

- **US1**: Caregiver can create/edit PRN medication; schedule hidden when PRN on; list shows 定時/頓服 badges.
- **US2**: Patient can record PRN dose with confirmation; no undo; inventory adjusts; overlay blocks input during network.
- **US3**: Day history shows PRN entries in time order; optional calendar counts visible if provided.

---

## Implementation Strategy

- MVP: Complete Phases 1–4 to deliver caregiver PRN setup and badges.
- Incremental: Add patient PRN recording (Phase 5), then history integration (Phase 6).
- Always run required tests per phase: `cd api && npm test` and `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`.
