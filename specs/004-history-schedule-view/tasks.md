# Tasks: History Schedule View

**Input**: Design documents from `/specs/004-history-schedule-view/`  
**Prerequisites**: plan.md (required), spec.md, research.md, data-model.md, contracts/  
**Tests**: Tests are REQUIRED by spec (contract, integration, iOS UI smoke)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1/US2/US3)
- Each task includes Purpose (Why), Files, Done Criteria (AC), and Test command

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Feature scaffolding and shared DTOs

- [x] T001 Create History feature folder structure (Why: UI modules) in `ios/MedicationApp/Features/History/` (AC: folder exists with placeholder view files; Test: N/A)
- [x] T002 [P] Add history DTOs for month/day responses in `ios/MedicationApp/Networking/HistoryDTO.swift` (Why: decoding API responses; AC: DTOs compile; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T003 [P] Add history API route files (Why: endpoint stubs) in `api/app/api/patient/history/month/route.ts`, `api/app/api/patient/history/day/route.ts`, `api/app/api/patients/[patientId]/history/month/route.ts`, `api/app/api/patients/[patientId]/history/day/route.ts` (AC: routes compile; Test: `npm test` from `api/`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared schedule/status logic and request validation

- [x] T004 Implement `getScheduleWithStatus(patientId, from, to, tz)` in `api/src/services/scheduleService.ts` (Why: reuse effectiveStatus logic; AC: returns schedule + status; Test: `npm test`)
- [x] T005 [P] Add slot aggregation + summary builders in `api/src/services/scheduleResponse.ts` (Why: month slotSummary with precedence; AC: MISSED > PENDING > TAKEN enforced; Test: `npm test`)
- [x] T006 [P] Add timezone-aware range helpers in `api/src/services/scheduleService.ts` (Why: Asia/Tokyo month/day boundaries; AC: range matches spec; Test: `npm test`)
- [x] T007 [P] Add year/month/date validators in `api/src/validators/schedule.ts` (Why: stable inputs; AC: invalid inputs return 422; Test: `npm test`)
- [x] T008 [P] Add History API methods in `ios/MedicationApp/Networking/APIClient.swift` (Why: fetch month/day for patient/caregiver; AC: methods decode DTOs; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T009 [P] Add History view model for state + fetch orchestration in `ios/MedicationApp/Features/History/HistoryViewModel.swift` (Why: centralize data flow; AC: exposes month/day state and errors; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

**Checkpoint**: Foundation ready - user story work can begin

---

## Phase 3: User Story 1 - View monthly history and day detail (Priority: P1) 🎯 MVP

**Goal**: Patient can view month calendar and day details with derived status

**Independent Test**: Open History tab as patient → month loads with dots → tap day shows ordered list

### Tests for User Story 1

- [x] T010 [P] [US1] Contract test patient month history in `api/tests/contract/history-month-patient.contract.test.ts` (Why: response shape; AC: 200/401 validated; Test: `npm test`)
- [x] T011 [P] [US1] Contract test patient day history in `api/tests/contract/history-day-patient.contract.test.ts` (Why: response shape; AC: 200/401 validated; Test: `npm test`)
- [x] T012 [P] [US1] Integration test effectiveStatus + 60m rule in `api/tests/integration/history-day-status.test.ts` (Why: TAKEN/MISSED/PENDING derivation; AC: passes edge cases; Test: `npm test`)
- [x] T013 [P] [US1] Integration test slotSummary precedence in `api/tests/integration/history-month-aggregation.test.ts` (Why: MISSED > PENDING > TAKEN; AC: aggregation matches spec; Test: `npm test`)

### Implementation for User Story 1 (API → iOS)

- [x] T014 [US1] Implement patient month endpoint in `api/app/api/patient/history/month/route.ts` (Why: month summaries; AC: slotSummary per day; Test: `npm test`)
- [x] T015 [US1] Implement patient day endpoint in `api/app/api/patient/history/day/route.ts` (Why: day detail list; AC: ordered by slot then name; Test: `npm test`)
- [x] T016 [US1] Replace patient history placeholder with real view in `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift` (Why: enable History tab; AC: History tab shows month view; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T017 [US1] Build month calendar + dots + legend in `ios/MedicationApp/Features/History/HistoryMonthView.swift` (Why: month UI; AC: 4 slot dots, legend visible; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T018 [US1] Build day detail list in `ios/MedicationApp/Features/History/HistoryDayDetailView.swift` (Why: day detail; AC: slot then name ordering; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T019 [US1] Add patient history strings in `ios/MedicationApp/Resources/Localizable.strings` (Why: i18n-ready copy; AC: history labels localized; Test: N/A)

**Checkpoint**: User Story 1 is independently functional and testable

---

## Phase 4: User Story 2 - View history for a linked patient (Priority: P2)

**Goal**: Caregiver can view history for selected patient or see empty state if none

**Independent Test**: Caregiver with selected patient → history works; no selection → empty state + CTA to patients tab

### Tests for User Story 2

- [ ] T020 [P] [US2] Contract test caregiver history + concealment in `api/tests/contract/history-caregiver.contract.test.ts` (Why: 401/404 behavior; AC: non-owned patient 404; Test: `npm test`)

### Implementation for User Story 2 (API → iOS)

- [ ] T021 [US2] Implement caregiver month endpoint in `api/app/api/patients/[patientId]/history/month/route.ts` (Why: caregiver month summaries; AC: 404 concealment; Test: `npm test`)
- [ ] T022 [US2] Implement caregiver day endpoint in `api/app/api/patients/[patientId]/history/day/route.ts` (Why: caregiver day detail; AC: 404 concealment; Test: `npm test`)
- [ ] T023 [US2] Add caregiver History tab + order (薬→履歴→連携/患者) in `ios/MedicationApp/Features/PatientManagement/CaregiverHomeView.swift` (Why: UX tab order; AC: tab order matches spec; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T024 [US2] Add caregiver empty state + CTA in `ios/MedicationApp/Features/History/CaregiverHistoryView.swift` (Why: no patient selected flow; AC: shows 「患者を選択してください」 and CTA; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T025 [US2] Add caregiver history strings in `ios/MedicationApp/Resources/Localizable.strings` (Why: i18n-ready copy; AC: caregiver labels localized; Test: N/A)

**Checkpoint**: User Story 2 works independently with caregiver auth

---

## Phase 5: User Story 3 - Loading and retry behavior (Priority: P3)

**Goal**: Full-screen updating overlay blocks all interaction and supports retry

**Independent Test**: Trigger any history fetch → overlay blocks input; error shows copy and retry works

### Tests for User Story 3

- [ ] T026 [P] [US3] iOS UI smoke tests for overlay + retry in `ios/MedicationApp/Tests/HistoryOverlayTests.swift` (Why: blocking UX enforcement; AC: overlay blocks scroll/tap; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

### Implementation for User Story 3

- [ ] T027 [US3] Implement full-screen overlay state in `ios/MedicationApp/Features/History/HistoryViewModel.swift` and `ios/MedicationApp/Shared/Views/FullScreenContainer.swift` (Why: block interactions; AC: overlay on all fetches; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T028 [US3] Add error + retry UX wiring in `ios/MedicationApp/Features/History/HistoryMonthView.swift` and `ios/MedicationApp/Features/History/HistoryDayDetailView.swift` (Why: recovery flow; AC: error text + retry uses overlay; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T029 [US3] Add accessibility labels for dots, legend, and empty state in `ios/MedicationApp/Features/History/HistoryMonthView.swift`, `ios/MedicationApp/Features/History/HistoryDayDetailView.swift`, `ios/MedicationApp/Features/History/CaregiverHistoryView.swift` (Why: VoiceOver clarity; AC: labels describe slot/status; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

**Checkpoint**: User Story 3 independently meets overlay and retry requirements

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and final verification

- [ ] T030 [P] Verify no manual refresh control in History UI (`ios/MedicationApp/Features/History/*`) (Why: auto-sync only; AC: no refresh button or pull-to-refresh; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T031 [P] Record history load timing check (Why: NFR-001) in `specs/004-history-schedule-view/quickstart.md` (AC: note manual perf check <= 3s; Test: N/A)
- [ ] T032 [P] Update user-facing release notes/help copy in `specs/README.md` (or existing user docs) (Why: NFR-004; AC: History tab + status legend described; Test: N/A)
- [ ] T033 [P] Refresh docs and contracts if needed in `specs/004-history-schedule-view/quickstart.md` and `specs/004-history-schedule-view/contracts/openapi.yaml` (Why: keep docs accurate; AC: docs match implementation; Test: N/A)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Phase 1
- **User Stories (Phase 3-5)**: Depend on Phase 2
- **Polish (Phase 6)**: Depends on all required user stories

### User Story Dependencies

- **US1 (P1)**: No dependency on other stories
- **US2 (P2)**: Can run after Foundational; reuses US1 components but remains testable
- **US3 (P3)**: Can run after Foundational; depends on History views for wiring

### Parallel Opportunities

- T002, T003 can run in parallel
- T005-T009 can run in parallel after T004
- Contract/integration tests (T010-T013) can run in parallel
- iOS view tasks (T017-T018) can run in parallel after T016

---

## Parallel Example: User Story 1

```bash
# Tests in parallel
Task: "T010 Contract test patient month history"
Task: "T011 Contract test patient day history"
Task: "T012 Integration test effectiveStatus + 60m rule"
Task: "T013 Integration test slotSummary precedence"

# iOS views in parallel after History tab wiring
Task: "T017 Build month calendar + dots + legend"
Task: "T018 Build day detail list"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 → Phase 2
2. Build and validate User Story 1
3. Stop and verify acceptance scenarios

### Incremental Delivery

1. US1 → validate patient flows
2. US2 → add caregiver flows + empty state
3. US3 → enforce blocking overlay and retry
4. Final polish and docs
