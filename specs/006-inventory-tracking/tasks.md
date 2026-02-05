# Tasks: Inventory Tracking

**Input**: Design documents from `/specs/006-inventory-tracking/`  
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md  
**Tests**: Required by spec (contract, integration, iOS UI smoke)

## Phase 1: Tests (contract/integration/UI)

- [ ] T001 [P] [US1] Add contract test for `GET /api/patients/{patientId}/inventory` (Why: validate list shape + auth + conceal; Files: `api/tests/contract/inventory-list.contract.test.ts`; AC: 200/401/404 cases asserted; Test: `cd api && npm test`)
- [ ] T002 [P] [US1] Add contract test for `PATCH /api/patients/{patientId}/medications/{medicationId}/inventory` (Why: validate update request/response + auth + conceal; Files: `api/tests/contract/inventory-update.contract.test.ts`; AC: 200/401/404/422 cases asserted; Test: `cd api && npm test`)
- [ ] T003 [P] [US1] Add contract test for `POST /api/patients/{patientId}/medications/{medicationId}/inventory/adjust` (Why: validate adjust request/response + auth + conceal; Files: `api/tests/contract/inventory-adjust.contract.test.ts`; AC: 200/401/404/422 cases asserted; Test: `cd api && npm test`)
- [ ] T004 [P] [US2] Add integration test for TAKEN create decrement (idempotent) (Why: prevent double-decrement; Files: `api/tests/integration/inventory-taken-create.test.ts`; AC: only new TAKEN decrements; Test: `cd api && npm test`)
- [ ] T005 [P] [US2] Add integration test for TAKEN delete increment (Why: restore inventory on undo; Files: `api/tests/integration/inventory-taken-delete.test.ts`; AC: delete increments by dose_count_per_intake; Test: `cd api && npm test`)
- [ ] T006 [P] [US3] Add integration test for low/out transitions and alert gating (Why: prevent spam; Files: `api/tests/integration/inventory-alerts.test.ts`; AC: events only on state transitions; Test: `cd api && npm test`)
- [ ] T007 [P] [US1] Add auth/404 conceal integration test for non-owned patient inventory access (Why: security requirement; Files: `api/tests/integration/inventory-auth.test.ts`; AC: concealment enforced; Test: `cd api && npm test`)
- [ ] T008 [P] [US1] Add iOS UI smoke test for Inventory tab empty state + CTA (Why: validate UX; Files: `ios/MedicationApp/Tests/Inventory/InventoryEmptyStateUITests.swift`; AC: CTA routes to Linking tab; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T009 [P] [US1] Add iOS UI smoke test for Inventory list/detail view (Why: validate caregiver flow; Files: `ios/MedicationApp/Tests/Inventory/InventoryListDetailUITests.swift`; AC: list shows quantity + low/out badge; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T010 [P] [US1] Add iOS UI smoke test for blocking overlay on fetch (Why: enforce UX requirement; Files: `ios/MedicationApp/Tests/Inventory/InventoryOverlayUITests.swift`; AC: overlay blocks tap/scroll and shows retry; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T011 [P] [US3] Add iOS UI smoke test for realtime inventory banner (Why: regression for caregiver banner; Files: `ios/MedicationApp/Tests/Caregiver/InventoryBannerUITests.swift`; AC: banner shown ~3s on event; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

---

## Phase 2: DB migrations / schema

- [ ] T012 [US1] Add inventory fields to Medication model (Why: store per-medication inventory; Files: `api/prisma/schema.prisma`; AC: new fields with defaults defined; Test: `cd api && npm test`)
- [ ] T013 [US2] Add MedicationInventoryAdjustment model (Why: audit inventory changes; Files: `api/prisma/schema.prisma`; AC: adjustment entity + relations defined; Test: `cd api && npm test`)
- [ ] T014 [US3] Add InventoryAlertEvent model (Why: realtime low/out events; Files: `api/prisma/schema.prisma`; AC: event entity + relations defined; Test: `cd api && npm test`)
- [ ] T015 [US1] Create migration for inventory schema changes (Why: apply DB updates; Files: `api/prisma/migrations/*`; AC: migration applies cleanly; Test: `cd api && npm test`)
- [ ] T016 [US3] Add RLS policy for inventory alert events (Why: caregivers only see linked patients; Files: `api/prisma/migrations/*`; AC: policy restricts reads; Test: `cd api && npm test`)

---

## Phase 3: API implementation

- [ ] T017 [US1] Add inventory validators for update/adjust payloads (Why: enforce integer-only rules; Files: `api/src/validators/inventory.ts`; AC: validations for enabled/quantity/threshold; Test: `cd api && npm test`)
- [ ] T018 [US1] Add inventory repository methods (Why: encapsulate DB access; Files: `api/src/repositories/medicationRepo.ts`, `api/src/repositories/inventoryAdjustmentRepo.ts`; AC: CRUD helpers for inventory + adjustments; Test: `cd api && npm test`)
- [ ] T019 [US3] Add inventory alert event repository (Why: persist alert events; Files: `api/src/repositories/inventoryAlertEventRepo.ts`; AC: insert event on state transition; Test: `cd api && npm test`)
- [ ] T020 [US1] Implement caregiver inventory list endpoint (Why: list medications with inventory status; Files: `api/app/api/patients/[patientId]/inventory/route.ts`; AC: returns list with low/out computed; Test: `cd api && npm test`)
- [ ] T021 [US1] Implement inventory update endpoint (Why: enable/disable + set quantity/threshold; Files: `api/app/api/patients/[patientId]/medications/[medicationId]/inventory/route.ts`; AC: updates values, clamps >=0; Test: `cd api && npm test`)
- [ ] T022 [US1] Implement inventory adjust endpoint (Why: refill/correction/set actions; Files: `api/app/api/patients/[patientId]/medications/[medicationId]/inventory/adjust/route.ts`; AC: applies delta or absolute; Test: `cd api && npm test`)
- [ ] T023 [US2] Update dose record service for TAKEN create/deletion adjustments (Why: auto inventory updates; Files: `api/src/services/doseRecordService.ts`; AC: decrement only on new record, increment on delete; Test: `cd api && npm test`)
- [ ] T024 [US2] Add adjustment logging on auto updates (Why: audit trail; Files: `api/src/services/medicationService.ts`, `api/src/repositories/inventoryAdjustmentRepo.ts`; AC: log with reason TAKEN_CREATE/TAKEN_DELETE; Test: `cd api && npm test`)
- [ ] T025 [US3] Emit alert events on low/out state transitions (Why: realtime banners; Files: `api/src/services/medicationService.ts`, `api/src/repositories/inventoryAlertEventRepo.ts`; AC: only transition emits; Test: `cd api && npm test`)

---

## Phase 4: iOS Caregiver Inventory tab UI

- [ ] T026 [US1] Add Inventory tab to caregiver tab order (Why: required navigation order; Files: `ios/MedicationApp/Features/Caregiver/*`, `ios/MedicationApp/RootView.swift`; AC: tabs show 「薬」「履歴」「在庫管理」「連携/患者」; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T027 [US1] Implement Inventory empty state + CTA to Linking tab (Why: UX requirement when no patient; Files: `ios/MedicationApp/Features/Inventory/InventoryEmptyStateView.swift`; AC: CTA switches to Linking tab; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T028 [US1] Implement Inventory list view with low/out badges (Why: caregiver overview; Files: `ios/MedicationApp/Features/Inventory/InventoryListView.swift`; AC: shows quantity and LOW/OUT badges; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T029 [US1] Implement Inventory detail editor (Why: edit enabled/quantity/threshold; Files: `ios/MedicationApp/Features/Inventory/InventoryDetailView.swift`; AC: enable toggle + quantity/threshold inputs + adjust actions; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T030 [US1] Add Inventory API client methods (Why: fetch/update/adjust; Files: `ios/MedicationApp/Networking/InventoryAPI.swift`; AC: GET/PATCH/POST wrappers; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T031 [US1] Wrap inventory network calls with blocking overlay (Why: enforce "更新中"; Files: `ios/MedicationApp/Shared/Views/FullScreenContainer.swift`, `ios/MedicationApp/Features/Inventory/*`; AC: overlay blocks interactions and supports retry; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

---

## Phase 5: Realtime alert events + caregiver banner integration

- [ ] T032 [US3] Add realtime subscription for inventory alerts (Why: foreground caregiver banner; Files: `ios/MedicationApp/Features/Caregiver/CaregiverEventSubscriber.swift`; AC: subscribes to inventory event type; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T033 [US3] Add banner presentation for inventory alerts (Why: user-visible low/out alerts; Files: `ios/MedicationApp/Shared/Banner/GlobalBannerPresenter.swift`; AC: shows newest banner ~3s, queue length 1; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T034 [US3] Add server-side alert payload mapping (Why: consistent banner text; Files: `api/src/services/medicationService.ts`; AC: payload includes patient/medication names and remaining; Test: `cd api && npm test`)

---

## Phase 6: Docs / quickstart updates

- [ ] T035 [P] Update quickstart with endpoints, constraints, and test commands (Why: documentation requirement; Files: `specs/006-inventory-tracking/quickstart.md`; AC: matches final behavior; Test: N/A)
- [ ] T036 [P] Update contracts to reflect final API schemas (Why: contract accuracy; Files: `specs/006-inventory-tracking/contracts/openapi.yaml`; AC: schemas align with implementation; Test: N/A)
- [ ] T037 [P] Update data-model notes with final fields and rules (Why: traceability; Files: `specs/006-inventory-tracking/data-model.md`; AC: matches DB schema + rules; Test: N/A)

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 (Tests) can start immediately.
- Phase 2 (DB) should complete before Phase 3 API.
- Phase 3 (API) should complete before Phase 4 iOS and Phase 5 Realtime.
- Phase 6 (Docs) can run after all behavior is implemented.

### User Story Dependencies

- **US1 (P1)**: Foundation for UI + API inventory management.
- **US2 (P2)**: Depends on US1 inventory fields + services.
- **US3 (P3)**: Depends on US1 inventory data + US2 update flow.

---

## Parallel Execution Examples

### US1

- T001, T002, T003 can run in parallel (contract tests).
- T028, T029, T030 can run in parallel (different iOS files).

### US2

- T004 and T005 can run in parallel (integration tests).

### US3

- T006 and T011 can run in parallel (API integration + UI test).

---

## Implementation Strategy

### MVP First (US1)

1. Complete Phase 1 tests relevant to US1.
2. Complete Phase 2 DB changes for inventory fields.
3. Complete Phase 3 inventory endpoints.
4. Complete Phase 4 Inventory tab UI and overlay.
5. Validate US1 independently via UI smoke + API tests.

### Incremental Delivery

1. Add US2 TAKEN adjustment logic + integration tests.
2. Add US3 alert events + caregiver banner integration.
3. Update docs and re-run test suites.
