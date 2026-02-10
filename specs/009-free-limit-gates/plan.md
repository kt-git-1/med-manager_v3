# Implementation Plan: Free Limit Gates

**Branch**: `009-free-limit-gates` | **Date**: 2026-02-10 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-free-limit-gates/spec.md`

## Summary

Using the billing foundation from 008, introduce the first real free-vs-premium gate: free caregivers can register at most 1 patient; premium caregivers register unlimited patients. The gate is enforced on both the backend (`POST /api/patients` returns 403 `PATIENT_LIMIT_EXCEEDED`) and the iOS UI (pre-flight check blocks the "add patient" action and presents the paywall). Existing caregivers with >1 patients retain read access (grandfather rule). Patient mode is unchanged.

## Technical Context

**Language/Version**: TypeScript (Node.js >=20, Next.js 16, Prisma 7.3), Swift 6.2 (SwiftUI, iOS 26 SDK)  
**Primary Dependencies**: Next.js App Router, Prisma, Supabase Auth (JWT); SwiftUI, StoreKit2, XCTest  
**Storage**: PostgreSQL via Prisma (`api/prisma/schema.prisma`) — no new tables  
**Testing**: Vitest (API integration/contract), XCTest (iOS unit + UI smoke)  
**Target Platform**: Web API (Vercel) + iOS app  
**Project Type**: Mobile + API  
**Performance Goals**: Gate check < 200ms p95 (single count query + entitlement lookup inside existing transaction)  
**Constraints**: Full-screen "更新中" overlay during async ops; patient mode always free; no external calls in CI  
**Scale/Scope**: MVP: gate on 1 operation (patient creation), 1 limit constant (FREE_PATIENT_LIMIT = 1)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Spec-Driven Development**: Pass (spec at `specs/009-free-limit-gates/spec.md` is source of truth).
- **Traceability**: Pass (every task maps to FR/NFR + acceptance scenarios + tests).
- **Test strategy**: Pass (tests-first; Vitest for backend; XCTest for iOS; no external calls in CI).
- **Security & privacy**: Pass (server-side enforcement mandatory; caregiver auth required; conceal unauthorized as 404; deny-by-default; no PII in logs).
- **Performance guardrails**: Pass (gate check is a single count query inside existing transaction).
- **UX/accessibility**: Pass (reuses SchedulingRefreshOverlay; localized strings; VoiceOver labels maintained).
- **Documentation**: Pass (quickstart, data model, contracts updated in same branch).

## Project Structure

### Documentation (this feature)

```text
specs/009-free-limit-gates/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── openapi.yaml     # Phase 1 output — PATIENT_LIMIT_EXCEEDED on POST /api/patients
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
api/
├── app/api/
│   └── patients/
│       └── route.ts                          # MODIFY: catch PatientLimitError, return 403 body
├── src/
│   ├── services/
│   │   ├── linkingService.ts                 # MODIFY: add limit check inside transaction
│   │   └── patientLimitConstants.ts          # NEW: FREE_PATIENT_LIMIT = 1
│   └── errors/
│       └── patientLimitError.ts              # NEW: PatientLimitError class
└── tests/
    ├── integration/
    │   └── patient-limit.test.ts             # NEW: limit enforcement tests
    └── contract/
        └── patients.contract.test.ts         # MODIFY: add PATIENT_LIMIT_EXCEEDED contract case

ios/MedicationApp/
├── Features/
│   ├── Billing/
│   │   ├── FeatureGate.swift                 # NO CHANGE (multiplePatients already defined)
│   │   ├── EntitlementStore.swift            # NO CHANGE
│   │   └── PaywallView.swift                 # NO CHANGE (reused as-is)
│   └── PatientManagement/
│       └── PatientManagementView.swift       # MODIFY: gate "add patient" button action
├── Networking/
│   ├── APIError.swift                        # MODIFY: add .patientLimitExceeded case
│   └── APIClient.swift                       # MODIFY: parse PATIENT_LIMIT_EXCEEDED before generic 403
├── Resources/
│   └── Localizable.strings                   # MODIFY: add limit-gate localization keys
└── Tests/
    ├── Billing/
    │   ├── FeatureGateTests.swift            # MODIFY: add canAddPatient gate tests
    │   └── PatientNoBillingUITests.swift     # MODIFY: add patient-mode no-paywall assertion
    └── PatientManagement/
        └── PatientLimitGateTests.swift       # NEW: unit tests for gate decision logic
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). This feature adds no new modules — it modifies the existing patient creation path and wires the existing billing components (FeatureGate, PaywallView, EntitlementStore) into the patient management flow.

## Complexity Tracking

No constitution violations. No new abstractions or layers introduced.

## Phase 0: Outline & Research

### Research Tasks

All unknowns resolved from 008 foundation and codebase inspection:

- **Gate point**: Confirmed as `POST /api/patients` (`createPatientForCaregiver()`). Linking code issuance and code exchange do NOT create new caregiver-patient links.
- **Count query**: `CaregiverPatientLink.count` with `status: "ACTIVE"` (excludes revoked links per FR-012).
- **Race condition**: Prisma `$transaction` with the count + conditional insert inside a single interactive transaction handles concurrent requests atomically.
- **Entitlement check inside transaction**: Use `tx.caregiverEntitlement.findFirst({ where: { caregiverId, status: "ACTIVE" } })` within the same transaction for atomicity.
- **iOS error differentiation**: Parse 403 response body for `"code": "PATIENT_LIMIT_EXCEEDED"` before the generic 403 handler (which calls `handleAuthFailure` — incorrect for limit errors).
- **Paywall reuse**: Existing `PaywallView` description already mentions multiple-patient registration. No new paywall variant needed for MVP.

### Output

- `research.md` with all decisions, rationales, and alternatives consolidated.

## Phase 1: Design & Contracts

### Data Model

No new entities. Gate logic uses existing entities from features 002 and 008:
- `CaregiverEntitlement` — premium status
- `CaregiverPatientLink` — active link count
- `Patient` — the resource being gated

New artifacts:
- `PatientLimitError` class (backend domain error, not a database entity)
- `FREE_PATIENT_LIMIT` constant (= 1)

### API Contracts

Existing `POST /api/patients` endpoint gains a new 403 response variant:

```json
{
  "code": "PATIENT_LIMIT_EXCEEDED",
  "message": "Patient limit reached. Upgrade to premium for unlimited patients.",
  "limit": 1,
  "current": 1
}
```

No new endpoints.

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
- Security & privacy: Pass (server-side enforcement; no PII in logs; RLS unchanged)
- Performance guardrails: Pass (single count + lookup in existing transaction)
- UX/accessibility: Pass (reuses overlay; localized; VoiceOver labels)
- Documentation: Pass (quickstart, data model, contracts updated)

## Phase 2: Implementation Plan (Tasks)

### Phase 1: Tests / Contracts (test-first)

1) Backend integration tests for patient limit
   - **Files**: `api/tests/integration/patient-limit.test.ts`
   - **Covers**: free + 0 patients → 201; free + 1 patient → 403 PATIENT_LIMIT_EXCEEDED; premium + 1 → 201; grandfather (free + 3) → list OK, create 403; race condition → exactly 1 succeeds
   - **Tests**: `cd api && npm test`

2) Backend contract test for PATIENT_LIMIT_EXCEEDED response shape
   - **Files**: `api/tests/contract/patients.contract.test.ts`
   - **Covers**: Response body `{ code, message, limit, current }` with status 403
   - **Tests**: `cd api && npm test`

3) iOS unit tests for gate decision logic
   - **Files**: `ios/MedicationApp/Tests/Billing/FeatureGateTests.swift` or `ios/MedicationApp/Tests/PatientManagement/PatientLimitGateTests.swift`
   - **Covers**: canAddPatient: free+0 → allowed; free+1 → blocked; free+3 → blocked; premium+N → allowed; unknown+N → needs refresh
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

4) iOS UI smoke tests
   - **Files**: `ios/MedicationApp/Tests/Billing/PatientNoBillingUITests.swift`
   - **Covers**: free caregiver + 1 patient → paywall on add; premium → no paywall; patient mode → no billing UI
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 2: Backend (enforce limit)

5) PatientLimitError class and constants
   - **Files**: `api/src/errors/patientLimitError.ts`, `api/src/services/patientLimitConstants.ts`
   - **Action**: `PatientLimitError` extends `Error` with `limit`, `current`, `statusCode: 403`. Constant `FREE_PATIENT_LIMIT = 1`.
   - **Tests**: `cd api && npm test`

6) Add limit check to `createPatientForCaregiver()`
   - **Files**: `api/src/services/linkingService.ts`
   - **Action**: Inside `$transaction`, before creating patient: count active links, check entitlement if count >= limit, throw PatientLimitError if not premium.
   - **Tests**: `cd api && npm test`

7) Handle PatientLimitError in route
   - **Files**: `api/app/api/patients/route.ts`
   - **Action**: Catch `PatientLimitError` before `errorResponse()`, return 403 with stable `{ code, message, limit, current }` body.
   - **Tests**: `cd api && npm test`

8) Verify all backend tests pass
   - **Tests**: `cd api && npm test`

### Phase 3: iOS (gate wiring)

9) Add `patientLimitExceeded` to APIError
    - **Files**: `ios/MedicationApp/Networking/APIError.swift`
    - **Action**: Add case `patientLimitExceeded(limit: Int, current: Int)`.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

10) Parse PATIENT_LIMIT_EXCEEDED in APIClient
    - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
    - **Action**: In `mapErrorIfNeeded`, before the `case 403:` handler, check if body contains `"code": "PATIENT_LIMIT_EXCEEDED"`. If so, throw `.patientLimitExceeded(limit:current:)` without calling `handleAuthFailure`.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

11) Wire gate in PatientManagementView "add patient" button
    - **Files**: `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift`
    - **Action**: Replace button action with gate logic: if unknown → refresh; if free + count >= 1 → showPaywall; else → showCreate. Add fallback: if createPatient returns .patientLimitExceeded, show paywall.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

12) Add localization keys for limit gate
    - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
    - **Action**: Add `billing.gate.patientLimit.title` and `billing.gate.patientLimit.body` keys.

13) Verify all iOS tests pass
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 4: Docs / Finalization

14) Generate spec artifacts
    - **Files**: `specs/009-free-limit-gates/research.md`, `data-model.md`, `quickstart.md`, `contracts/openapi.yaml`
    - **Action**: Write all design artifacts based on decisions documented in this plan.

15) Agent context update
    - **Action**: Run `.specify/scripts/bash/update-agent-context.sh cursor-agent`

## Acceptance Criteria

- Free caregiver can have at most 1 patient; adding 2nd triggers paywall and the add action is blocked.
- Premium caregiver can add 2nd+ patients without restriction.
- Backend enforces the limit (client cannot bypass via direct API calls).
- Existing accounts with >1 patients can still view/manage them (grandfather), but additional add is gated.
- Patient mode remains free and shows no billing UI.
- Overlay blocks interactions during entitlement refresh / gating / add operations.
- All required tests pass (iOS unit + UI smoke, backend integration).

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`
