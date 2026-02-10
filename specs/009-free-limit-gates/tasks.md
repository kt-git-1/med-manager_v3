# Tasks: Free Limit Gates

**Input**: Design documents from `/specs/009-free-limit-gates/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/openapi.yaml

**Tests**: Tests are REQUIRED (spec Testing Requirements mandate unit, UI smoke, and integration tests). Phase 1 is tests-first.

**Organization**: Tasks are grouped into 4 phases (Tests-first, Backend, iOS, Docs) with user story labels for traceability.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 = Free caregiver limit (1 patient) + paywall trigger, US2 = Premium caregiver unlimited patients, US3 = Backend enforcement + security, US4 = Docs / QA / release readiness

## Path Conventions

- **Backend**: `api/` (Next.js App Router + Prisma)
- **iOS**: `ios/MedicationApp/` (SwiftUI + StoreKit2)

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`

---

## Phase 1: Tests (Required First)

**Purpose**: Write all tests before implementation. Tests MUST fail initially and pass after their corresponding implementation phase completes.

### Backend Tests

- [x] T001 [P] [US3] Add entitlement fixture helper and multi-patient seeding utility to `api/tests/_db/testDb.ts`
  - **Why**: Integration tests need to seed premium entitlements and grandfather multi-patient fixtures for limit enforcement scenarios (FR-001, FR-010, FR-012)
  - **Files**: `api/tests/_db/testDb.ts`
  - **Done**: (1) `createEntitlementFixture({ caregiverId, ... })` creates an ACTIVE CaregiverEntitlement record, (2) `createMultiPatientFixture(caregiverId, count)` creates N patients with ACTIVE CaregiverPatientLinks for grandfather testing, (3) both use existing Prisma client
  - **Test**: `cd api && npm test`

- [x] T002 [P] [US3] Create integration tests for patient limit enforcement in `api/tests/integration/patient-limit.test.ts`
  - **Why**: Validates server-side gate for free vs premium caregivers, grandfather rule, race condition safety, and unauthorized rejection (FR-001, FR-002, FR-003, FR-010, FR-012, NFR-001)
  - **Files**: `api/tests/integration/patient-limit.test.ts`
  - **Done**: Test cases for: (1) free caregiver + 0 patients → create succeeds (201), (2) free caregiver + 1 ACTIVE patient → create rejected (403, code=PATIENT_LIMIT_EXCEEDED, limit=1, current=1), (3) premium caregiver + 1 patient → create succeeds (201), (4) free caregiver + 3 pre-existing patients (grandfather fixture) → list returns all 3 (200); create rejected (403, current=3), (5) two concurrent creates from free caregiver with 0 patients → exactly 1 succeeds + 1 fails, (6) unauthenticated request → 401, (7) free caregiver with 1 REVOKED patient + 0 ACTIVE → create succeeds (201, revoked links not counted)
  - **Test**: `cd api && npm test`

- [x] T003 [P] [US3] Add PATIENT_LIMIT_EXCEEDED contract test cases to `api/tests/contract/patients.contract.test.ts`
  - **Why**: Validates the stable error response shape that iOS relies on for paywall triggering (FR-003)
  - **Files**: `api/tests/contract/patients.contract.test.ts`
  - **Done**: Test cases for: (1) response status is 403, (2) response body has `code: "PATIENT_LIMIT_EXCEEDED"`, (3) response body has `limit` as integer, (4) response body has `current` as integer, (5) response body has `message` as string
  - **Test**: `cd api && npm test`

### iOS Unit Tests

- [x] T004 [P] [US1] Create unit tests for canAddPatient gate decision logic in `ios/MedicationApp/Tests/Billing/FeatureGateTests.swift`
  - **Why**: Validates the gate helper that drives the "add patient" button behavior for all entitlement states and patient counts (FR-001, FR-002, FR-005, FR-006, FR-007, FR-011)
  - **Files**: `ios/MedicationApp/Tests/Billing/FeatureGateTests.swift`
  - **Done**: Test cases for: (1) free + count=0 → allowed, (2) free + count=1 → blocked (paywall trigger), (3) free + count=3 → blocked (grandfather: viewing allowed but add blocked), (4) premium + count=0 → allowed, (5) premium + count=5 → allowed regardless of count, (6) unknown + count=0 → needs refresh (not allowed until state is resolved)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### iOS UI Smoke Tests

- [x] T005 [P] [US1] Create UI smoke tests for free caregiver add-patient blocked and paywall trigger in `ios/MedicationApp/Tests/Billing/PaywallUITests.swift`
  - **Why**: Validates that a free caregiver with 1 patient who taps "add patient" sees the paywall instead of the create form, and the overlay blocks interaction during gate checks (FR-002, FR-005, NFR-002, SC-001)
  - **Files**: `ios/MedicationApp/Tests/Billing/PaywallUITests.swift`
  - **Done**: Test cases for: (1) caregiver mode with 1 patient: tapping "caregiver.patients.add" button opens PaywallView sheet (not PatientCreateView), (2) overlay with accessibilityIdentifier "SchedulingRefreshOverlay" appears during entitlement refresh if state is unknown
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T006 [P] [US2] Create UI smoke tests for premium caregiver add-patient unblocked in `ios/MedicationApp/Tests/Billing/PaywallUITests.swift`
  - **Why**: Validates that a premium caregiver can add patients without paywall interruption (FR-008, SC-002)
  - **Files**: `ios/MedicationApp/Tests/Billing/PaywallUITests.swift`
  - **Done**: Test cases for: (1) premium caregiver with 1 patient: tapping "caregiver.patients.add" opens PatientCreateView (not PaywallView), (2) no paywall accessibility identifiers present during the add flow
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T007 [P] [US1] Add patient-mode no-paywall-on-add assertion to `ios/MedicationApp/Tests/Billing/PatientNoBillingUITests.swift`
  - **Why**: Validates that patient mode never exposes paywall or upgrade UI even in contexts where gate checks occur in caregiver mode (FR-009, SC-005)
  - **Files**: `ios/MedicationApp/Tests/Billing/PatientNoBillingUITests.swift`
  - **Done**: Existing test file extended with: (1) patient mode screens contain no paywall accessibility identifiers ("billing.paywall.*"), (2) no "caregiver.patients.add" button visible in patient mode (patient mode has no add-patient flow)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: All test files exist and define expected behavior. Tests fail because backend gate and iOS gate logic do not exist yet.

---

## Phase 2: Backend — Enforce Patient Limit (US3)

**Purpose**: Add PatientLimitError, limit constant, and gate check inside the existing `createPatientForCaregiver()` transaction. After this phase, backend tests (T001-T003) must pass.

### Constants & Error Class

- [x] T008 [P] [US3] Create FREE_PATIENT_LIMIT constant in `api/src/services/patientLimitConstants.ts`
  - **Why**: Single source of truth for the free-plan patient limit; easily adjustable for future plan tiers (FR-011)
  - **Files**: `api/src/services/patientLimitConstants.ts`
  - **Done**: (1) Exports `FREE_PATIENT_LIMIT = 1` as a named constant, (2) file is importable from linkingService.ts
  - **Test**: `cd api && npm test`

- [x] T009 [P] [US3] Create PatientLimitError class in `api/src/errors/patientLimitError.ts`
  - **Why**: Domain error carrying limit and current count for stable 403 response (FR-003, data-model.md PatientLimitError)
  - **Files**: `api/src/errors/patientLimitError.ts`
  - **Done**: (1) `PatientLimitError` extends `Error`, (2) has properties: `limit: number`, `current: number`, `statusCode: 403`, (3) constructor accepts `(limit: number, current: number)`, (4) message defaults to `"Patient limit reached. Upgrade to premium for unlimited patients."`
  - **Test**: `cd api && npm test`

### Service Layer Gate

- [x] T010 [US3] Add limit check inside `createPatientForCaregiver()` transaction in `api/src/services/linkingService.ts`
  - **Why**: Core enforcement — counts ACTIVE links and checks entitlement inside the existing Prisma $transaction for atomicity and race-condition safety (FR-001, FR-002, FR-010, FR-012, research decisions 1-3)
  - **Files**: `api/src/services/linkingService.ts`
  - **Done**: Inside the existing `prisma.$transaction()` in `createPatientForCaregiver()`, BEFORE creating the patient: (1) count ACTIVE links: `tx.caregiverPatientLink.count({ where: { caregiverId, status: "ACTIVE" } })`, (2) if count >= `FREE_PATIENT_LIMIT`, check entitlement: `tx.caregiverEntitlement.findFirst({ where: { caregiverId, status: "ACTIVE" } })`, (3) if no active entitlement found, throw `new PatientLimitError(FREE_PATIENT_LIMIT, count)`, (4) imports from `patientLimitConstants.ts` and `patientLimitError.ts`
  - **Test**: `cd api && npm test`

### Route Handler

- [x] T011 [US3] Handle PatientLimitError in POST /api/patients route in `api/app/api/patients/route.ts`
  - **Why**: Transforms the domain error into a stable HTTP 403 response with the contract shape iOS relies on (FR-003, NFR-001, contracts/openapi.yaml)
  - **Files**: `api/app/api/patients/route.ts`
  - **Done**: In the POST handler catch block, BEFORE `errorResponse(error)`: (1) `if (error instanceof PatientLimitError)` → return `new Response(JSON.stringify({ code: "PATIENT_LIMIT_EXCEEDED", message: error.message, limit: error.limit, current: error.current }), { status: 403, headers: { "content-type": "application/json" } })`, (2) import `PatientLimitError` from `../../../src/errors/patientLimitError`
  - **Test**: `cd api && npm test`

### Verification

- [x] T012 [US3] Verify all backend tests pass (T001-T003) against implemented code
  - **Why**: Confirms backend limit enforcement satisfies test expectations (SC-003, SC-006)
  - **Files**: `api/tests/integration/patient-limit.test.ts`, `api/tests/contract/patients.contract.test.ts`
  - **Done**: `cd api && npm test` exits 0 with all patient-limit and contract tests passing
  - **Test**: `cd api && npm test`

**Checkpoint**: Backend gate complete. Free caregivers with >= 1 patient are blocked at POST /api/patients with 403 PATIENT_LIMIT_EXCEEDED. Premium caregivers proceed. Grandfather viewing unaffected. Integration and contract tests pass.

---

## Phase 3: iOS — Gate Wiring + Paywall Integration (US1 + US2)

**Purpose**: Wire the existing billing infrastructure (FeatureGate, EntitlementStore, PaywallView) into the patient creation flow. After this phase, iOS tests (T004-T007) must pass.

### Networking Layer

- [x] T013 [P] [US1] Add `patientLimitExceeded(limit:current:)` case to APIError enum in `ios/MedicationApp/Networking/APIError.swift`
  - **Why**: Distinct error case so the UI can differentiate a limit rejection from a generic auth failure and show the paywall instead of logging out (FR-004, research decision 5)
  - **Files**: `ios/MedicationApp/Networking/APIError.swift`
  - **Done**: (1) New case `patientLimitExceeded(limit: Int, current: Int)` added to `APIError` enum, (2) `errorDescription` returns localized limit message, (3) enum remains `Error`-conforming and `LocalizedError`-conforming
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T014 [US1] Parse PATIENT_LIMIT_EXCEEDED in APIClient.mapErrorIfNeeded() before generic 403 handler in `ios/MedicationApp/Networking/APIClient.swift`
  - **Why**: The generic 403 handler calls `handleAuthFailure` which clears the session — incorrect for limit errors. Must intercept PATIENT_LIMIT_EXCEEDED first (FR-004, research decision 5)
  - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
  - **Done**: In `mapErrorIfNeeded()`, BEFORE the `case 403:` branch: (1) parse response body for JSON with `"code": "PATIENT_LIMIT_EXCEEDED"`, (2) if found, extract `limit` and `current` integers, (3) throw `APIError.patientLimitExceeded(limit: limit, current: current)` WITHOUT calling `handleAuthFailure`, (4) if not a limit error, fall through to existing 403 handling
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Gate Wiring

- [x] T015 [US1] Wire pre-flight gate and server fallback on "add patient" button in `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift`
  - **Why**: Core UX gate — prevents the create form from opening when blocked, shows paywall in one tap, and falls back to server error if the pre-flight check is bypassed (FR-002, FR-005, FR-006, FR-007, NFR-002, SC-001)
  - **Files**: `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift`
  - **Done**: (1) Replace the "add patient" button action (currently `showingCreate = true` at ~line 144) with gate logic: if `entitlementStore.state == .unknown` → `await entitlementStore.refresh()` (overlay shown automatically via isRefreshing); if `!entitlementStore.isPremium && viewModel.patients.count >= 1` → `showingPaywall = true` and return; else → `showingCreate = true`, (2) In `PatientManagementViewModel.createPatient()`, add fallback: if the API call throws `APIError.patientLimitExceeded` → surface it so the view can set `showingPaywall = true` instead of showing a generic error, (3) Grandfather rule: free caregivers with >1 patients see all in the list (no change to list logic) but the add button is gated, (4) overlay blocks interaction during entitlement refresh via existing SchedulingRefreshOverlay binding to entitlementStore.isRefreshing
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Localization

- [x] T016 [P] [US1] Add limit-gate localization keys to `ios/MedicationApp/Resources/Localizable.strings`
  - **Why**: Gate-specific copy for future use; keeps strings localized and not hardcoded (NFR-002, constitution III, spec UX Copy)
  - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
  - **Done**: Keys added: (1) `"billing.gate.patientLimit.title" = "プレミアムで複数患者を登録";`, (2) `"billing.gate.patientLimit.body" = "無料プランでは登録できる患者は1人までです。プレミアムで無制限に登録できます。";`
  - **Test**: Build succeeds; strings available for future paywall context customization

### Patient Mode Audit

- [x] T017 [US1] Verify patient mode has no billing UI — confirmation audit against `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift`
  - **Why**: Patient mode must never show paywall/upgrade UI; this is a confirmation that no gate code leaked into patient views (FR-009, SC-005)
  - **Files**: `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift`
  - **Done**: (1) PatientReadOnlyView contains no references to EntitlementStore, PaywallView, FeatureGate, or billing localization keys, (2) No "caregiver.patients.add" button exists in patient mode, (3) T007 (PatientNoBillingUITests) passes
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Verification

- [x] T018 [US1] Verify all iOS tests pass (T004-T007) against implemented code
  - **Why**: Confirms iOS gate wiring satisfies test expectations (SC-001, SC-002, SC-005, SC-006)
  - **Files**: `ios/MedicationApp/Tests/Billing/`
  - **Done**: `xcodebuild test` exits 0 with all gate-related tests passing: FeatureGateTests (canAddPatient), PaywallUITests (free blocked, premium unblocked), PatientNoBillingUITests (no billing in patient mode)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: Full iOS and backend gate implementations complete. All tests (T001-T007) pass. Free caregivers blocked at 1 patient. Premium caregivers unrestricted. Grandfather viewing preserved. Patient mode clean.

---

## Phase 4: Docs / QA Readiness (US4)

**Purpose**: Verify and finalize design documentation against implemented behavior. Docs were pre-generated during planning; this phase confirms accuracy post-implementation.

- [x] T019 [P] [US4] Verify and finalize quickstart.md with post-implementation sandbox test steps in `specs/009-free-limit-gates/quickstart.md`
  - **Why**: Quickstart must accurately describe the implemented gate behavior, sandbox test procedures, and grandfather rule for developers and testers (NFR-004)
  - **Files**: `specs/009-free-limit-gates/quickstart.md`
  - **Done**: (1) Free limit sandbox steps verified against implemented gate, (2) Premium bypass steps verified, (3) Grandfather rule steps verified, (4) Error contract table matches implemented response shape, (5) File locations table matches actual paths
  - **Test**: Manual walkthrough of quickstart steps

- [x] T020 [P] [US4] Verify contracts/openapi.yaml matches implemented error response in `specs/009-free-limit-gates/contracts/openapi.yaml`
  - **Why**: OpenAPI spec must reflect the actual PATIENT_LIMIT_EXCEEDED 403 response shape (NFR-004)
  - **Files**: `specs/009-free-limit-gates/contracts/openapi.yaml`
  - **Done**: (1) POST /patients 403 response schema matches `{ code, message, limit, current }`, (2) PatientLimitExceededResponse component matches implemented PatientLimitError serialization, (3) 201/401/422 responses unchanged from existing behavior
  - **Test**: `cd api && npm test` (contract tests T003 validate response shape)

**Checkpoint**: All documentation finalized and verified against implementation.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Tests)**: No dependencies — start immediately
- **Phase 2 (Backend)**: Depends on T001 (fixture helpers) being available; implementation makes T002-T003 pass
- **Phase 3 (iOS)**: Depends on Phase 2 completion (backend gate must exist for server fallback testing); implementation makes T004-T007 pass
- **Phase 4 (Docs)**: Depends on Phase 2 + Phase 3 completion (docs verified against implementation)

### User Story Dependencies

- **US1 (Free limit + paywall)**: Requires US3 backend enforcement for server fallback; core iOS gate wiring
- **US2 (Premium unlimited)**: Verified by tests in Phase 1; no separate implementation — premium path is the absence of blocking
- **US3 (Backend enforcement)**: Independent — can be built and tested without iOS
- **US4 (Docs)**: Depends on US1 + US3 being complete

### Within Each Phase

- Tasks marked [P] can run in parallel (different files, no shared state)
- Unmarked tasks depend on prior tasks in same phase completing

### Parallel Opportunities

```text
# Phase 1: All test files can be written in parallel
T001 | T002 | T003 | T004 | T005 | T006 | T007

# Phase 2: Constant and error class in parallel, then sequential service/route
(T008 | T009) -> T010 -> T011 -> T012

# Phase 3: APIError and localization in parallel, then sequential wiring
(T013 | T016) -> T014 -> T015 -> T017 -> T018

# Phase 4: All docs in parallel
T019 | T020
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2)

1. Write all tests (Phase 1) — establish behavioral contract
2. Implement backend gate (Phase 2) — backend tests pass
3. **STOP and VALIDATE**: Run `cd api && npm test` — all green

### Full Feature (Phase 3 + Phase 4)

4. Implement iOS gate wiring (Phase 3) — iOS tests pass
5. **STOP and VALIDATE**: Run full iOS test suite — all green
6. Finalize docs (Phase 4)
7. **FINAL VALIDATION**: Both test suites green, quickstart walkthrough passes

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 20 |
| Phase 1 (Tests) | 7 |
| Phase 2 (Backend) | 5 |
| Phase 3 (iOS) | 6 |
| Phase 4 (Docs) | 2 |
| Parallel opportunities | 7 (Phase 1) + 2 (Phase 2) + 2 (Phase 3) + 2 (Phase 4) |
| US1 tasks | 9 |
| US2 tasks | 1 |
| US3 tasks | 8 |
| US4 tasks | 2 |
| Non-goals excluded | Other premium features, escalation push, Pro plan, patient-mode changes |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Tests MUST fail before implementation and pass after
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
- Gate point is `POST /api/patients` (`createPatientForCaregiver()` in `api/src/services/linkingService.ts`) — the only operation that creates CaregiverPatientLinks
- Existing `FeatureGate.multiplePatients`, `PaywallView`, `EntitlementStore`, and `SchedulingRefreshOverlay` from 008 are reused without modification
- Only ACTIVE links count toward the limit (revoked links excluded per FR-012)
