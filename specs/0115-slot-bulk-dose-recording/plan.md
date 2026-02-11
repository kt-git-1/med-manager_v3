# Implementation Plan: Slot Bulk Dose Recording (0115)

**Branch**: `0115-slot-bulk-dose-recording` | **Date**: 2026-02-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/0115-slot-bulk-dose-recording/spec.md`

## Summary

Replace per-medication dose recording in the patient Today tab with slot-based (morning/noon/evening/bedtime) bulk recording. Each slot card displays medication details (name+dosage, "1回X錠") and a summary ("合計Y錠（N種類）") with a single large "この時間のお薬を飲んだ" button. A new backend endpoint `POST /api/patient/dose-records/slot` handles atomic bulk upserts. All slot time references use per-patient `scheduledAt` values from generated scheduled doses, never hardcoded defaults.

## Technical Context

**Language/Version**: TypeScript 5.9 (Node >=20) for API; Swift 6.2 for iOS
**Primary Dependencies**: Next.js 16 App Router, Prisma 7.3, Vitest; SwiftUI, XCTest
**Storage**: PostgreSQL via Prisma (DoseRecord table, new nullable `recordingGroupId` column)
**Testing**: Vitest (integration), XCTest (unit + UI smoke)
**Target Platform**: Node 20 server, iOS 26+
**Project Type**: Mobile + API
**Performance Goals**: Bulk record API response < 2s for 10 medications; end-to-end < 3s
**Constraints**: Idempotent, transactional, patient-only auth, per-patient slot times, full-screen overlay

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: spec is the single source of truth for behavior. **Pass**
- Traceability: every change maps to spec + acceptance criteria + tests. **Pass**
- Test strategy: tests-first in Phase 1; Vitest for backend; XCTest for iOS; no external calls in CI. **Pass**
- Security & privacy: patient auth via `requirePatient`; deny-by-default; no PII in logs. **Pass**
- Performance guardrails: `$transaction` batch upsert for bounded slot size (<=10 meds); no N+1. **Pass**
- UX/accessibility: reuses full-screen overlay pattern; VoiceOver labels on slot cards; localized strings. **Pass**
- Documentation: research, data model, contracts, quickstart updated in same branch. **Pass**

Post-Phase 1 re-check: **Pass** (research, data model, contracts, and quickstart aligned with spec).

## Project Structure

### Documentation (this feature)

```text
specs/0115-slot-bulk-dose-recording/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── openapi.yaml     # Phase 1 output — slot bulk record endpoint
├── checklists/
│   └── requirements.md  # Spec quality checklist (existing)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
api/
├── prisma/
│   └── schema.prisma                                    # MODIFY: add recordingGroupId to DoseRecord
├── app/api/patient/dose-records/slot/
│   └── route.ts                                         # NEW: POST bulk record endpoint
├── src/
│   ├── services/
│   │   └── slotBulkRecordService.ts                     # NEW: bulk record orchestration
│   └── validators/
│       └── slotBulkRecord.ts                            # NEW: slot bulk request validation
└── tests/integration/
    └── slot-bulk-record.test.ts                         # NEW: integration tests

ios/MedicationApp/
├── Features/Today/
│   ├── PatientTodayView.swift                           # MODIFY: slot card layout
│   └── PatientTodayViewModel.swift                      # MODIFY: bulk record methods + slot state
├── Networking/
│   ├── APIClient.swift                                  # MODIFY: add bulk record API method
│   └── DTOs/
│       └── SlotBulkRecordDTO.swift                      # NEW: request/response DTOs
├── Resources/
│   └── Localizable.strings                              # MODIFY: add slot card strings
└── Tests/
    └── SlotBulkRecord/
        ├── SlotCardRenderingTests.swift                 # NEW: unit tests
        └── SlotBulkRecordUITests.swift                  # NEW: UI smoke tests
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). New endpoint lives under the existing patient route namespace. iOS changes modify the existing Today feature module and add a new test group.

## Complexity Tracking

No Constitution violations noted.

## Phase 0: Outline & Research

### Research Tasks

All unknowns resolved from codebase inspection and dependency spec review:

- **Bulk record atomicity**: Prisma `$transaction` with batch `upsert`. Side effects fire after transaction. Rationale: atomic + idempotent + keeps transaction short. Alternative rejected: sequential `createDoseRecordIdempotent` (not atomic).

- **Slot time source of truth**: `scheduledAt` from generated scheduled doses. iOS passes custom slot times as query params via existing `parseSlotTimesFromParams`. Rationale: regimen times already encode patient schedule. Alternative rejected: server-side slot time storage (over-engineered).

- **Side effects**: Loop through newly created records after transaction; fire `createDoseRecordEvent` + `notifyCaregiversOfDoseRecord` + `applyInventoryDeltaForDoseRecord` per record. Rationale: reuses existing pipeline from `doseRecordService.ts`. Alternative rejected: single combined notification (out of scope).

- **recordingGroupId**: Nullable `String?` column on DoseRecord. UUID generated per bulk operation. Rationale: enables downstream grouped push/PDF. Alternative rejected: junction table (over-engineered).

- **Endpoint path**: `POST /api/patient/dose-records/slot`. Rationale: follows existing `/api/patient/` namespace convention. Alternative rejected: `/api/dose-records/slot` (breaks convention).

- **Response shape**: `{ updatedCount, remainingCount, totalPills, medCount, slotTime, slotSummary, recordingGroupId }`. Rationale: provides all data client needs for UI update without separate refresh. Alternative rejected: returning full dose records (verbose).

### Output

- `research.md` with all decisions, rationales, and alternatives consolidated.

## Phase 1: Design & Contracts

**Prerequisites**: `research.md` complete

### Data Model

**DoseRecord — modified** (in `api/prisma/schema.prisma`):
- Add: `recordingGroupId String?` (UUID, nullable)
- No new indexes needed (groupId is queried only for display, not filtering)

No new tables. All other entities unchanged. Full details in `data-model.md`.

### API Contracts

One new endpoint:

**`POST /api/patient/dose-records/slot`**

Request body:
```json
{
  "date": "2026-02-11",
  "slot": "morning"
}
```

Query parameters (optional, existing `parseSlotTimesFromParams` pattern):
- `morningTime=07:30`, `noonTime=12:00`, `eveningTime=19:00`, `bedtimeTime=22:00`

Auth: `Authorization: Bearer <patient-session-token>` (via `requirePatient`)

Success response (200):
```json
{
  "updatedCount": 3,
  "remainingCount": 0,
  "totalPills": 5,
  "medCount": 3,
  "slotTime": "07:30",
  "slotSummary": {
    "morning": "taken",
    "noon": "pending",
    "evening": "none",
    "bedtime": "none"
  },
  "recordingGroupId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

Error responses: 401 Unauthorized, 403 Forbidden, 422 Validation error

Full OpenAPI specification in `contracts/openapi.yaml`.

### Key Implementation: slotBulkRecordService.ts

Service orchestration flow:
1. Validate inputs (date format, slot enum)
2. Generate day range from date + Asia/Tokyo timezone
3. Call `getScheduleWithStatus` for the patient's day
4. Filter doses to target slot using `resolveSlot` with custom slot times
5. Partition into recordable (PENDING/MISSED) and already-taken
6. If recordable is empty, return summary with `updatedCount: 0`
7. Generate `recordingGroupId` UUID
8. Execute `prisma.$transaction([...upserts])` for all recordable doses
9. Determine which records were newly created vs already existed
10. Fire side effects for new records: events, push, inventory
11. Recompute slot summary via `buildSlotSummary`
12. Derive `slotTime` from the first dose's `scheduledAt` in the slot
13. Return response

### Key Implementation: iOS SlotCard

The existing `PatientTodayListView` groups doses by slots via `slotSections`. Changes:
- **SlotCardView** (new): header (slot name + time + badge + count), body (medication rows with name+dosage+"1回X錠"), summary ("合計Y錠（N種類）"), large "この時間のお薬を飲んだ" button
- **ViewModel**: `confirmSlot: NotificationSlot?` state, `bulkRecordSlot(_:)` method, computed `slotSummaries`
- **Confirmation dialog**: "{slotLabel}（{slotTime}）のお薬（N種類 / 合計Y錠）を記録しますか？"
- **Overlay**: reuses existing `isUpdating` / `SchedulingRefreshOverlay` pattern

### Agent Context Update

- Run `.specify/scripts/bash/update-agent-context.sh cursor-agent`

### Output

- `data-model.md`
- `contracts/openapi.yaml`
- `quickstart.md`
- Updated agent context

### Constitution Check (Post-Design)

- Spec-Driven Development: Pass
- Traceability: Pass (every task maps to FR + test)
- Test strategy: Pass (tests written first; no external calls)
- Security & privacy: Pass (patient auth via `requirePatient`; deny-by-default; no PII in logs)
- Performance guardrails: Pass (bounded slot size; `$transaction` batch; no N+1)
- UX/accessibility: Pass (reuses overlay; localized strings; VoiceOver labels)
- Documentation: Pass (research, data model, contracts, quickstart updated)

## Phase 2: Implementation Plan (Tasks)

### Phase 1: Tests / Contracts (test-first)

1) Backend integration tests for slot bulk record
   - **Files**: `api/tests/integration/slot-bulk-record.test.ts`
   - **Covers**: PENDING bulk → TAKEN (correct updatedCount/remainingCount); MISSED → TAKEN (withinTime=false); idempotent double-call; totalPills/medCount correctness; slotTime reflects custom times; auth required (401/403); validation errors (422)
   - **Mock pattern**: follows `dose-recording-patient.test.ts` mocking repos + services
   - **Tests**: `cd api && npm test`

2) iOS unit tests for slot card rendering
   - **Files**: `ios/MedicationApp/Tests/SlotBulkRecord/SlotCardRenderingTests.swift`
   - **Covers**: medication row format "薬名+用量" and "1回X錠"; summary "合計Y錠（N種類）" calculation; button disabled when remaining=0; slot time label derived from scheduledAt; header status badge derivation
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

3) iOS UI smoke tests for bulk record flow
   - **Files**: `ios/MedicationApp/Tests/SlotBulkRecord/SlotBulkRecordUITests.swift`
   - **Covers**: patient bulk record flow (tap → confirm → overlay → success → TAKEN status); overlay blocks interaction; MISSED slot can still be recorded; per-patient slot time in header
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 2: Backend

4) Prisma migration — add recordingGroupId to DoseRecord
   - **Files**: `api/prisma/schema.prisma`
   - **Action**: Add `recordingGroupId String?` field to DoseRecord model. Run `npx prisma migrate dev --name slot_bulk_recording_group_id`.

5) Slot bulk record validator
   - **Files**: `api/src/validators/slotBulkRecord.ts`
   - **Action**: `validateSlotBulkRecordRequest(body)` validates `date` (YYYY-MM-DD format) and `slot` (one of morning/noon/evening/bedtime). Returns `{ errors, date, slot }`. Reuses `parseSlotTimesFromParams` from `scheduleResponse.ts` for custom slot time query param validation.

6) Slot bulk record service
   - **Files**: `api/src/services/slotBulkRecordService.ts`
   - **Action**: `bulkRecordSlot({ patientId, date, slot, customSlotTimes })` implements the 13-step orchestration flow documented above. Reuses: `getScheduleWithStatus`, `resolveSlot`, `buildSlotSummary` from `scheduleService.ts` / `scheduleResponse.ts`; `createDoseRecordEvent` from `doseRecordEventRepo.ts`; `notifyCaregiversOfDoseRecord` from `pushNotificationService.ts`; `applyInventoryDeltaForDoseRecord` from `medicationService.ts`; `getPatientRecordById` from `patientRepo.ts`; `getMedicationRecordForPatient` from `medicationRepo.ts`.

7) API route handler
   - **Files**: `api/app/api/patient/dose-records/slot/route.ts`
   - **Action**: `POST` handler follows the existing pattern from `api/app/api/patient/dose-records/route.ts`: `requirePatient` → parse body + query params → validate → call `bulkRecordSlot` → return JSON response. Catch errors → `errorResponse()`. Add `logDoseRecordOperation("bulk_create", "patient")`.

8) Verify all backend tests pass
   - **Tests**: `cd api && npm test`

### Phase 3: iOS

9) SlotBulkRecordDTO
   - **Files**: `ios/MedicationApp/Networking/DTOs/SlotBulkRecordDTO.swift`
   - **Action**: `SlotBulkRecordRequestDTO` (Encodable): `date: String`, `slot: String`. `SlotBulkRecordResponseDTO` (Decodable): `updatedCount: Int`, `remainingCount: Int`, `totalPills: Double`, `medCount: Int`, `slotTime: String`, `slotSummary: [String: String]`, `recordingGroupId: String?`.

10) APIClient — add bulkRecordSlot method
    - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
    - **Action**: New method `bulkRecordSlot(date:slot:slotTimes:) async throws -> SlotBulkRecordResponseDTO`. POST to `/api/patient/dose-records/slot` with slot time query items from `NotificationPreferencesStore.slotTimeQueryItems()`. Uses patient session token auth. ISO8601 date encoding.

11) PatientTodayViewModel — bulk record state and methods
    - **Files**: `ios/MedicationApp/Features/Today/PatientTodayViewModel.swift`
    - **Action**: Add `confirmSlot: NotificationSlot?` published property. Add `SlotSummary` struct with `totalPills`, `medCount`, `remainingCount`, `slotTime`, `aggregateStatus`. Add computed `slotSummaries: [NotificationSlot: SlotSummary]` from current `items`. Add `confirmBulkRecord(for:)` (sets `confirmSlot`). Add `executeBulkRecord()` (sets `isUpdating=true`, calls API with today's date + slot, calls `refreshTodayData()` on success, shows toast, handles errors).

12) PatientTodayView — SlotCard UI
    - **Files**: `ios/MedicationApp/Features/Today/PatientTodayView.swift`
    - **Action**: Create `SlotCardView` subview within the Today view file. Replace/restructure existing slot sections with slot cards. Header: slot label + time (from `slotSummary.slotTime` or first dose's `scheduledAt`) + status badge + remaining count. Body: medication rows with `name + dosageText` and "1回{doseCountPerIntake}錠". Summary line: "合計{totalPills}錠（{medCount}種類）". Button: "この時間のお薬を飲んだ" (disabled when `remainingCount == 0`). Confirmation alert/sheet triggered by `confirmSlot`, shows slot-level summary text. Overlay: reuses existing `isUpdating` state and `SchedulingRefreshOverlay` / `FullScreenContainer` pattern.

13) Localizable.strings
    - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
    - **Action**: Add localization keys for: slot card button ("この時間のお薬を飲んだ"), confirmation title ("{slot}のお薬を記録"), confirmation message ("{slot}（{time}）のお薬（{N}種類 / 合計{Y}錠）を記録しますか？"), confirmation buttons ("記録する" / "キャンセル"), summary format ("合計%@錠（%@種類）"), per-dose format ("1回%@錠"), success toast.

14) Verify all iOS tests pass
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 4: Docs / Finalization

15) Generate spec artifacts
    - **Files**: `specs/0115-slot-bulk-dose-recording/research.md`, `data-model.md`, `quickstart.md`, `contracts/openapi.yaml`
    - **Action**: Already written as part of /speckit.plan execution.

16) Agent context update
    - **Action**: Run `.specify/scripts/bash/update-agent-context.sh cursor-agent`

## Acceptance Criteria

- Patient can record a whole slot with one large button + confirmation
- Slot card body shows: 薬名+用量, 1回X錠, 合計Y錠（N種類）
- Slot time display and MISSED/withinTime calculations follow per-patient slot times (scheduledAt), not fixed constants
- No patient undo; caregiver behavior unchanged
- Full-screen overlay blocks interaction during sync
- All tests pass (backend integration + iOS unit/UI smoke)

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`
