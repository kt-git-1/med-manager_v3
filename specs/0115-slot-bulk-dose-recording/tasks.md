# Tasks: Slot Bulk Dose Recording

**Input**: Design documents from `/specs/0115-slot-bulk-dose-recording/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/openapi.yaml

**Tests**: Tests are REQUIRED (spec Testing Requirements mandate backend integration, iOS unit, and iOS UI smoke tests). Phase 1 is tests-first.

**Organization**: Tasks are grouped into 4 phases (Tests-first, Backend, iOS, Docs) with user story labels for traceability.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 = Patient bulk-records slot doses, US2 = Slot card display with details/summary, US3 = Confirmation dialog, US4 = MISSED doses recordable, US5 = Caregiver sees bulk results

## Path Conventions

- **Backend**: `api/` (Next.js App Router + Prisma)
- **iOS**: `ios/MedicationApp/` (SwiftUI)

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`

---

## Phase 1: Tests (Required First)

**Purpose**: Write all tests before implementation. Tests MUST fail initially and pass after their corresponding implementation phase completes.

### Backend Integration Tests

- [x] T001 [P] [US1] Create test fixtures, mock setup, and PENDING bulk -> TAKEN test case in `api/tests/integration/slot-bulk-record.test.ts`
  - **Why**: Integration tests need to mock repos, services, and auth to test the bulk record service and route. The PENDING -> TAKEN case is the core happy path (FR-007, FR-014, SC-003)
  - **Files**: `api/tests/integration/slot-bulk-record.test.ts`
  - **Done**: (1) Mock `patientSessionVerifier` for patient sessions, (2) Mock `doseRecordRepo` (upsertDoseRecord with in-memory store, getDoseRecordByKey, listDoseRecordsByPatientRange), (3) Mock `patientRepo` (getPatientRecordById returns patient with displayName), (4) Mock `medicationRepo` (getMedicationRecordForPatient returns medication with doseCountPerIntake), (5) Mock `doseRecordEventRepo` (createDoseRecordEvent returns event), (6) Mock `pushNotificationService` (notifyCaregiversOfDoseRecord no-op), (7) Mock `medicationService` (applyInventoryDeltaForDoseRecord no-op), (8) Mock `scheduleService` (generateScheduleForPatientWithStatus returns 3 PENDING morning doses), (9) Mock `scheduleResponse` (resolveSlot maps morning doses to "morning"), (10) Helper `patientHeaders()` for auth, (11) Test case: 3 PENDING morning doses -> POST /api/patient/dose-records/slot -> 200 with updatedCount=3, remainingCount=0
  - **Test**: `cd api && npm test`

- [x] T002 [P] [US4] Create integration test cases for MISSED->TAKEN, idempotent, auth, and validation in `api/tests/integration/slot-bulk-record.test.ts`
  - **Why**: Validates MISSED dose recording (withinTime=false), idempotency, auth enforcement, and input validation (FR-007, FR-008, NFR-002, NFR-003, SC-004)
  - **Files**: `api/tests/integration/slot-bulk-record.test.ts`
  - **Done**: Test cases for: (1) 2 MISSED morning doses -> bulk record -> updatedCount=2, all records have withinTime=false (takenAt > scheduledAt + 60m), (2) idempotent: call bulk record twice for same slot -> first returns updatedCount=3, second returns updatedCount=0, (3) missing auth header -> 401, (4) caregiver token -> 403, (5) missing date field -> 422 validation error, (6) missing slot field -> 422 validation error, (7) invalid slot value "lunch" -> 422 validation error, (8) invalid date format "2026/02/11" -> 422 validation error
  - **Test**: `cd api && npm test`

- [x] T003 [P] [US1] Create integration test cases for totalPills/medCount/slotTime correctness in `api/tests/integration/slot-bulk-record.test.ts`
  - **Why**: Validates response summary fields match the slot's medication data, and custom slot times are respected (FR-004, FR-013, SC-005)
  - **Files**: `api/tests/integration/slot-bulk-record.test.ts`
  - **Done**: Test cases for: (1) slot with 3 medications (doseCountPerIntake: 2, 1, 3) -> totalPills=6, medCount=3, (2) slotTime derived from first dose's scheduledAt (e.g., scheduledAt at 07:30 Tokyo -> slotTime="07:30"), (3) custom slot times via query params (morningTime=07:00) -> resolveSlot uses custom time for slot assignment, (4) slotSummary contains correct status for all 4 slots after recording morning, (5) recordingGroupId is a valid UUID string in response, (6) mixed slot: 2 PENDING + 1 TAKEN -> updatedCount=2, remainingCount=0, totalPills and medCount include all 3 medications
  - **Test**: `cd api && npm test`

### iOS Unit Tests

- [x] T004 [P] [US2] Create unit tests for slot card rendering logic in `ios/MedicationApp/Tests/SlotBulkRecord/SlotCardRenderingTests.swift`
  - **Why**: Validates slot card display formatting, summary calculation, and button state derivation (FR-002, FR-003, FR-004, FR-005, SC-001)
  - **Files**: `ios/MedicationApp/Tests/SlotBulkRecord/SlotCardRenderingTests.swift`
  - **Done**: Test cases for: (1) medication row displays "アムロジピン 5mg" format (name + dosageText), (2) medication row displays "1回2錠" format (doseCountPerIntake=2), (3) medication row with doseCountPerIntake=1 displays "1回1錠", (4) summary calculation: 3 medications with doseCountPerIntake [2, 1, 3] -> "合計6錠（3種類）", (5) summary with single medication: doseCountPerIntake=1 -> "合計1錠（1種類）", (6) button disabled when remainingCount=0 (all doses TAKEN), (7) button enabled when remainingCount > 0, (8) slot time label derived from scheduledAt 2026-02-11T07:30:00+09:00 -> "07:30", (9) header status badge: all PENDING -> "pending" aggregate, (10) header status badge: all TAKEN -> "taken" aggregate, (11) header status badge: mix of TAKEN and MISSED -> "missed" aggregate (worst-case), (12) remaining count = count of PENDING + MISSED doses in slot
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### iOS UI Smoke Tests

- [x] T005 [P] [US1] Create UI smoke tests for patient bulk record flow in `ios/MedicationApp/Tests/SlotBulkRecord/SlotBulkRecordUITests.swift`
  - **Why**: Validates the end-to-end patient bulk recording flow including confirmation, overlay, and status update (FR-006, FR-007, FR-009, FR-015, SC-001, SC-002)
  - **Files**: `ios/MedicationApp/Tests/SlotBulkRecord/SlotBulkRecordUITests.swift`
  - **Done**: Test cases for: (1) patient Today tab shows slot cards with "この時間のお薬を飲んだ" button (accessibilityIdentifier "SlotBulkRecordButton"), (2) tapping button shows confirmation dialog with slot label and summary text, (3) tapping "記録する" shows "更新中" overlay (accessibilityIdentifier "SchedulingRefreshOverlay"), (4) after success overlay dismisses and slot card shows TAKEN status, (5) overlay blocks tap interactions while visible, (6) MISSED slot still shows enabled "この時間のお薬を飲んだ" button, (7) tapping "キャンセル" in confirmation dismisses dialog without recording, (8) slot card header shows per-patient slot time (not hardcoded default)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: All test files exist and define expected behavior. Tests fail because backend bulk record endpoint and iOS slot card UI do not exist yet.

---

## Phase 2: Backend — Bulk Record Endpoint (US1 + US4)

**Purpose**: Add recordingGroupId migration, validator, service, and route. After this phase, backend tests (T001-T003) must pass.

### Migration & Validator

- [x] T006 [P] [US1] Add recordingGroupId column to DoseRecord in `api/prisma/schema.prisma`
  - **Why**: Nullable UUID column groups dose records from the same bulk operation for downstream features like push notifications and PDF export (FR-012, data-model.md)
  - **Files**: `api/prisma/schema.prisma`
  - **Done**: (1) Add `recordingGroupId String?` field to DoseRecord model after `recordedById`, (2) No new indexes (groupId is for display only, not query filtering), (3) Run `npx prisma migrate dev --name slot_bulk_recording_group_id` to generate and apply migration, (4) Verify existing DoseRecord queries still work (nullable field, backward compatible)
  - **Test**: `cd api && npm test`

- [x] T007 [P] [US1] Create slot bulk record validator in `api/src/validators/slotBulkRecord.ts`
  - **Why**: Centralised input validation for date and slot fields, reusing existing `parseSlotTimesFromParams` for custom slot time query params (FR-007, contracts/openapi.yaml SlotBulkRecordRequest)
  - **Files**: `api/src/validators/slotBulkRecord.ts`
  - **Done**: (1) Export `validateSlotBulkRecordRequest(body: { date?: string; slot?: string }): { errors: string[]; date?: string; slot?: HistorySlot }`, (2) Validate `date` is present and matches YYYY-MM-DD format, (3) Validate `slot` is present and one of "morning", "noon", "evening", "bedtime", (4) Return errors array (empty on success) and parsed values, (5) Import `HistorySlot` type from `scheduleResponse.ts`, (6) Reuse `parseSlotTimesFromParams` from `scheduleResponse.ts` for slot time query param validation (called by route, not validator)
  - **Test**: `cd api && npm test`

### Service

- [x] T008 [US1] Implement slot bulk record service in `api/src/services/slotBulkRecordService.ts`
  - **Why**: Core orchestration: schedule lookup, slot filtering, transactional upsert, side effects, and summary computation. Reuses existing services from 003/004/005 (FR-007, FR-008, FR-012, FR-014, NFR-001, NFR-002, NFR-003, research decisions 1-4)
  - **Files**: `api/src/services/slotBulkRecordService.ts`
  - **Done**: (1) Export `bulkRecordSlot({ patientId, date, slot, customSlotTimes }): Promise<SlotBulkRecordResult>`, (2) Parse date string to day range using `getDayRange` with Asia/Tokyo timezone, (3) Call `getScheduleWithStatus(patientId, from, to, "Asia/Tokyo")` for today's doses with status, (4) Filter doses to target slot using `resolveSlot(dose.scheduledAt, "Asia/Tokyo", customSlotTimes)`, (5) Compute `totalPills` (sum of `doseCountPerIntake` across all slot doses) and `medCount` (count of slot doses), (6) Partition into `recordable` (effectiveStatus pending or missed) and already-taken, (7) If recordable is empty: return `{ updatedCount: 0, remainingCount: 0, totalPills, medCount, slotTime, slotSummary, recordingGroupId: null }`, (8) Generate `recordingGroupId = crypto.randomUUID()`, (9) Import `prisma` and execute `prisma.$transaction(recordable.map(dose => prisma.doseRecord.upsert({ where: { patientId_medicationId_scheduledAt: ... }, create: { patientId, medicationId, scheduledAt, takenAt: now, recordedByType: "PATIENT", recordedById: null, recordingGroupId }, update: {} })))`, (10) For each newly created record: fetch patient and medication, compute `withinTime = takenAt <= scheduledAt + DOSE_MISSED_WINDOW_MS`, call `createDoseRecordEvent`, fire-and-forget `notifyCaregiversOfDoseRecord`, call `applyInventoryDeltaForDoseRecord`, (11) Recompute `remainingCount` from updated schedule, (12) Build `slotSummary` via `buildSlotSummary` on updated doses, (13) Derive `slotTime` from first slot dose's scheduledAt (HH:mm in Tokyo) or custom slot time param, (14) Return `{ updatedCount, remainingCount, totalPills, medCount, slotTime, slotSummary, recordingGroupId }`
  - **Test**: `cd api && npm test`

### Route

- [x] T009 [US1] Implement POST route handler in `api/app/api/patient/dose-records/slot/route.ts`
  - **Why**: The new endpoint that serves slot bulk recording for patients. Follows the exact auth + validate + service + response pattern from `api/app/api/patient/dose-records/route.ts` (FR-007, FR-011, contracts/openapi.yaml)
  - **Files**: `api/app/api/patient/dose-records/slot/route.ts`
  - **Done**: (1) `export const runtime = "nodejs"`, (2) POST handler: extract `authorization` header + parse JSON body + extract searchParams, (3) Auth: `requirePatient(authHeader)` -> get `session.patientId`, (4) Validate body: `validateSlotBulkRecordRequest({ date: body.date, slot: body.slot })` -> return 422 on errors, (5) Parse slot times: `parseSlotTimesFromParams(new URL(request.url).searchParams)` -> return 422 on errors, (6) Call `bulkRecordSlot({ patientId: session.patientId, date: validated.date, slot: validated.slot, customSlotTimes: slotTimes })`, (7) Log: `logDoseRecordOperation("bulk_create", "patient")`, (8) Return 200 JSON with `{ updatedCount, remainingCount, totalPills, medCount, slotTime, slotSummary, recordingGroupId }`, (9) Catch errors -> `errorResponse(error)`
  - **Test**: `cd api && npm test`

### Verification

- [x] T010 [US1] Verify all backend tests pass (T001-T003) against implemented code
  - **Why**: Confirms bulk record endpoint validation, auth enforcement, idempotency, atomicity, and response correctness satisfy test expectations (SC-003, SC-004, SC-005, SC-007)
  - **Files**: `api/tests/integration/slot-bulk-record.test.ts`
  - **Done**: `cd api && npm test` exits 0 with all slot-bulk-record integration tests passing
  - **Test**: `cd api && npm test`

**Checkpoint**: Backend bulk record endpoint complete. PENDING and MISSED doses bulk-recorded as TAKEN. Idempotent. Transactional. Custom slot times respected. Auth enforced. Integration tests pass.

---

## Phase 3: iOS — Slot Card UI + Bulk Recording (US1 + US2 + US3 + US4 + US5)

**Purpose**: Add slot bulk record DTO, API client method, ViewModel bulk record logic, SlotCard UI, confirmation dialog, and localization. After this phase, iOS tests (T004-T005) must pass.

### Networking Layer

- [x] T011 [P] [US1] Create SlotBulkRecordDTO structs in `ios/MedicationApp/Networking/DTOs/SlotBulkRecordDTO.swift`
  - **Why**: Encodable request and Decodable response DTOs matching the bulk record API contract (contracts/openapi.yaml)
  - **Files**: `ios/MedicationApp/Networking/DTOs/SlotBulkRecordDTO.swift`
  - **Done**: (1) `SlotBulkRecordRequestDTO: Encodable` with `date: String`, `slot: String`, (2) `SlotBulkRecordResponseDTO: Decodable` with `updatedCount: Int`, `remainingCount: Int`, `totalPills: Double`, `medCount: Int`, `slotTime: String`, `slotSummary: [String: String]`, `recordingGroupId: String?`, (3) `SlotSummaryDTO: Decodable` as alternative typed structure if preferred for `slotSummary` with `morning`, `noon`, `evening`, `bedtime` as `String`
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T012 [US1] Add bulkRecordSlot method to APIClient in `ios/MedicationApp/Networking/APIClient.swift`
  - **Why**: New API client method for the slot bulk record endpoint, following the pattern of `createPatientDoseRecord` (lines 241-257). Passes custom slot times as query parameters from NotificationPreferencesStore.
  - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
  - **Done**: (1) New method `func bulkRecordSlot(date: String, slot: String, slotTimes: [URLQueryItem]) async throws -> SlotBulkRecordResponseDTO`, (2) Build URL: `api/patient/dose-records/slot` with slot time query items appended, (3) POST request with JSON body `SlotBulkRecordRequestDTO(date:slot:)`, (4) Use patient session token auth (same as `createPatientDoseRecord`), (5) Decode response as `SlotBulkRecordResponseDTO`, (6) Error handling follows existing pattern with `mapErrorIfNeeded`
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### ViewModel

- [x] T013 [US1] Add bulk record state and methods to PatientTodayViewModel in `ios/MedicationApp/Features/Today/PatientTodayViewModel.swift`
  - **Why**: Encapsulates slot-level confirmation state, summary computation, and the bulk record API call with overlay and toast feedback (FR-006, FR-007, FR-009, FR-015, FR-016)
  - **Files**: `ios/MedicationApp/Features/Today/PatientTodayViewModel.swift`
  - **Done**: (1) Add `SlotSummary` struct with `totalPills: Double`, `medCount: Int`, `remainingCount: Int`, `slotTime: String`, `aggregateStatus: DoseStatusDTO`, (2) Add `@Published var confirmSlot: NotificationSlot?` for slot-level confirmation, (3) Add computed property `slotSummaries: [NotificationSlot: SlotSummary]` that groups current `items` by slot using `NotificationSlot.from(date:slotTimes:)`, computes totalPills (sum of doseCountPerIntake), medCount (count of doses), remainingCount (PENDING + MISSED count), slotTime (HH:mm from first dose's scheduledAt), aggregateStatus (worst-case: missed > pending > taken > none), (4) Add `func confirmBulkRecord(for slot: NotificationSlot)` that sets `confirmSlot = slot`, (5) Add `func executeBulkRecord() async` that: reads `confirmSlot`, sets `confirmSlot = nil` + `isUpdating = true`, formats today's date as YYYY-MM-DD (Asia/Tokyo), gets slot time query items from `notificationPreferencesStore.slotTimeQueryItems()`, calls `apiClient.bulkRecordSlot(date:slot:slotTimes:)`, on success calls `refreshTodayData()` and sets `toastMessage`, on failure sets `errorMessage`, always sets `isUpdating = false`
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Views

- [x] T014 [US2] Create SlotCardView and restructure PatientTodayView for slot-based layout in `ios/MedicationApp/Features/Today/PatientTodayView.swift`
  - **Why**: Replaces per-medication recording buttons with slot-level cards showing medication details, summary, and a single large bulk record button (FR-001, FR-002, FR-003, FR-004, FR-005, SC-001)
  - **Files**: `ios/MedicationApp/Features/Today/PatientTodayView.swift`
  - **Done**: (1) Create `SlotCardView` subview accepting `slot: NotificationSlot`, `doses: [ScheduleDoseDTO]`, `summary: SlotSummary`, `onRecord: () -> Void`, (2) Header: HStack with slot color indicator, slot label (朝/昼/夜/眠前 from existing localization), slot time text (summary.slotTime, e.g. "07:30"), status badge (summary.aggregateStatus), remaining count badge (summary.remainingCount), (3) Body: ForEach doses showing medication row — each row has: `dose.medicationSnapshot.name` + " " + `dose.medicationSnapshot.dosageText` (e.g., "アムロジピン 5mg") on one line, "1回\(dose.medicationSnapshot.doseCountPerIntake)錠" on the same or next line, (4) Summary line at bottom of body: "合計\(summary.totalPills)錠（\(summary.medCount)種類）" using localized format string, (5) Primary button: large "この時間のお薬を飲んだ" button — disabled when `summary.remainingCount == 0`, calls `onRecord()`, accessibilityIdentifier "SlotBulkRecordButton", (6) VoiceOver: slot card group label includes slot name and medication count, (7) Restructure `PatientTodayListView` to iterate `slotSections` as `SlotCardView` instances instead of individual `PatientTodayRow` with per-dose record buttons, (8) Preserve existing slot ordering: morning, noon, evening, bedtime, (9) Hide slot card if slot has zero scheduled medications
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T015 [US3] Wire confirmation dialog for slot-level bulk record in `ios/MedicationApp/Features/Today/PatientTodayView.swift`
  - **Why**: Mandatory confirmation before irreversible bulk recording, showing slot summary for informed consent (FR-006, FR-010, SC-001)
  - **Files**: `ios/MedicationApp/Features/Today/PatientTodayView.swift`
  - **Done**: (1) Add `.alert` or `.confirmationDialog` modifier bound to `viewModel.confirmSlot`, (2) Title: "{slotLabel}のお薬を記録" using localized format (e.g., "朝のお薬を記録"), (3) Message: "{slotLabel}（{slotTime}）のお薬（{medCount}種類 / 合計{totalPills}錠）を記録しますか？" using localized format with values from `slotSummaries[slot]`, (4) Primary button: "記録する" -> calls `Task { await viewModel.executeBulkRecord() }`, (5) Cancel button: "キャンセル" -> dismisses (sets `confirmSlot = nil`), (6) Existing `isUpdating` overlay (SchedulingRefreshOverlay / FullScreenContainer) blocks interaction during API call — no additional overlay needed
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Localization

- [x] T016 [P] [US2] Add slot card localization strings to `ios/MedicationApp/Resources/Localizable.strings`
  - **Why**: All user-facing copy must be localized per constitution III and NFR-005
  - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
  - **Done**: Keys added: (1) `"patient.today.slot.bulk.button" = "この時間のお薬を飲んだ";`, (2) `"patient.today.slot.bulk.confirm.title" = "%@のお薬を記録";`, (3) `"patient.today.slot.bulk.confirm.message" = "%@（%@）のお薬（%@種類 / 合計%@錠）を記録しますか？";`, (4) `"patient.today.slot.bulk.confirm.record" = "記録する";`, (5) `"patient.today.slot.bulk.confirm.cancel" = "キャンセル";`, (6) `"patient.today.slot.bulk.summary" = "合計%@錠（%@種類）";`, (7) `"patient.today.slot.bulk.perDose" = "1回%@錠";`, (8) `"patient.today.slot.bulk.success" = "記録しました";`, (9) `"patient.today.slot.bulk.remaining" = "残り%d件";`
  - **Test**: Build succeeds; strings referenced by SlotCardView and confirmation dialog

### Compatibility Verification

- [x] T017 [US5] Verify caregiver views reflect bulk-recorded results — audit existing refresh paths
  - **Why**: Caregiver today/history/calendar views must show TAKEN status for doses bulk-recorded by the patient without any code changes (FR-011, SC-006)
  - **Files**: `ios/MedicationApp/Features/Today/CaregiverTodayView.swift`, `ios/MedicationApp/Features/History/CaregiverHistoryView.swift`
  - **Done**: (1) Confirm caregiver Today view reads from same schedule + dose record data (via `getScheduleWithStatus`) — bulk-recorded DoseRecords with recordedByType=PATIENT are included, (2) Confirm caregiver History view uses same `resolveSlot` + `buildSlotSummary` pipeline — slot summary reflects TAKEN status from bulk records, (3) Confirm caregiver per-dose record/cancel workflow is unchanged (uses separate `POST /api/patients/{patientId}/dose-records` and `DELETE` endpoints, not affected by new `/slot` endpoint), (4) No code changes needed — existing caregiver views are compatible by design
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Verification

- [x] T018 [US1] Verify all iOS tests pass (T004-T005) against implemented code
  - **Why**: Confirms slot card rendering, summary calculation, bulk record flow, overlay, and confirmation satisfy test expectations (SC-001, SC-002, SC-005, SC-007)
  - **Files**: `ios/MedicationApp/Tests/SlotBulkRecord/`
  - **Done**: `xcodebuild test` exits 0 with all SlotBulkRecord tests passing: SlotCardRenderingTests, SlotBulkRecordUITests
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: Full iOS and backend implementations complete. All tests (T001-T005) pass. Patient can bulk-record slot doses with confirmation. Slot cards show medication details and summary. MISSED doses remain recordable. Caregiver views reflect bulk-recorded results. Overlay blocks interaction during sync.

---

## Phase 4: Docs / QA Readiness

**Purpose**: Verify design documentation against implemented behavior and run full test suites.

- [x] T019 [P] Verify and finalize quickstart.md, contracts/openapi.yaml, and data-model.md against implementation in `specs/0115-slot-bulk-dose-recording/`
  - **Why**: Design documents must accurately describe the implemented bulk record endpoint, response model, and migration (constitution VI)
  - **Files**: `specs/0115-slot-bulk-dose-recording/quickstart.md`, `specs/0115-slot-bulk-dose-recording/contracts/openapi.yaml`, `specs/0115-slot-bulk-dose-recording/data-model.md`
  - **Done**: (1) quickstart.md test steps verified against implemented flows (bulk record, MISSED, idempotent, caregiver), (2) openapi.yaml request/response schemas match implemented route (`POST /api/patient/dose-records/slot`), error codes 401/403/422 match, (3) data-model.md `recordingGroupId` field description matches schema migration, SlotCard entity matches iOS implementation, withinTime derivation matches service logic
  - **Test**: `cd api && npm test` (integration tests validate response shape)

- [x] T020 [P] Run full test suite validation — both backend and iOS
  - **Why**: Final confirmation that all behavioral contracts hold and no regressions in existing features (SC-007)
  - **Files**: `api/tests/`, `ios/MedicationApp/Tests/`
  - **Done**: (1) `cd api && npm test` exits 0 — all slot-bulk-record + existing tests pass, (2) `xcodebuild test` exits 0 — all SlotBulkRecord + existing tests pass, (3) No regressions in 003 dose-recording tests, 004 history tests, 005 notification tests
  - **Test**: Both commands exit 0

**Checkpoint**: All documentation finalized and verified against implementation. Full test suites green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Tests)**: No dependencies — start immediately
- **Phase 2 (Backend)**: Depends on T001 fixtures being available; implementation makes T001-T003 pass
- **Phase 3 (iOS)**: Depends on Phase 2 completion (backend endpoint must exist for integration); implementation makes T004-T005 pass
- **Phase 4 (Docs)**: Depends on Phase 2 + Phase 3 completion (docs verified against implementation)

### User Story Dependencies

- **US1 (Bulk record flow)**: Core flow across backend + iOS; requires US2 (slot card) and US3 (confirmation) for full experience
- **US2 (Slot card display)**: Independent UI work; can be built in parallel with backend (uses mock data until API ready)
- **US3 (Confirmation dialog)**: Depends on US2 (slot card) for trigger; depends on US1 ViewModel for execution
- **US4 (MISSED recording)**: Implicit in US1 backend logic (MISSED treated as recordable); tested explicitly in T002
- **US5 (Caregiver compat)**: Independent verification — no code changes needed; existing views are compatible

### Within Each Phase

- Tasks marked [P] can run in parallel (different files, no shared state)
- Unmarked tasks depend on prior tasks in same phase completing

### Parallel Opportunities

```text
# Phase 1: All test files can be written in parallel
T001 | T002 | T003 | T004 | T005

# Phase 2: Migration and validator in parallel, then sequential service/route
(T006 | T007) -> T008 -> T009 -> T010

# Phase 3: DTO and localization in parallel, then sequential networking/view/wiring
(T011 | T016) -> T012 -> T013 -> T014 -> T015 -> T017 -> T018

# Phase 4: All docs in parallel
T019 | T020
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2)

1. Write all tests (Phase 1) — establish behavioral contract
2. Implement backend bulk record endpoint (Phase 2) — backend tests pass
3. **STOP and VALIDATE**: Run `cd api && npm test` — all green

### Full Feature (Phase 3 + Phase 4)

4. Implement iOS DTO, API client, ViewModel, SlotCard, confirmation, localization (Phase 3) — iOS tests pass
5. **STOP and VALIDATE**: Run full iOS test suite — all green
6. Finalize docs (Phase 4)
7. **FINAL VALIDATION**: Both test suites green, quickstart walkthrough passes

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 20 |
| Phase 1 (Tests) | 5 |
| Phase 2 (Backend) | 5 |
| Phase 3 (iOS) | 8 |
| Phase 4 (Docs) | 2 |
| Parallel opportunities | 5 (Phase 1) + 2 (Phase 2) + 2 (Phase 3) + 2 (Phase 4) |
| US1 tasks | 13 |
| US2 tasks | 3 |
| US3 tasks | 1 |
| US4 tasks | 1 |
| US5 tasks | 1 |
| Untagged tasks | 2 |
| Non-goals excluded | Slot time settings UI, patient undo, caregiver bulk recording, caregiver UX redesign |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Tests MUST fail before implementation and pass after
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
- Backend reuses: `scheduleService.getScheduleWithStatus`, `scheduleResponse.resolveSlot` + `buildSlotSummary` + `parseSlotTimesFromParams`, `doseRecordEventRepo.createDoseRecordEvent`, `pushNotificationService.notifyCaregiversOfDoseRecord`, `medicationService.applyInventoryDeltaForDoseRecord`
- iOS reuses: `NotificationSlot.from(date:slotTimes:)`, `NotificationPreferencesStore.slotTimeQueryItems()`, `SchedulingRefreshOverlay` / `FullScreenContainer`, existing `isUpdating` overlay pattern
- All dates computed in Asia/Tokyo timezone (server and client)
- Slot time source of truth: `scheduledAt` from generated scheduled doses (not hardcoded defaults)
- `recordingGroupId` is nullable — null for single-record flows, UUID for bulk
- Caregiver views are compatible by design — no code changes needed for US5
