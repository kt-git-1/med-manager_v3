# Implementation Plan: Billing Foundation (Premium Unlock)

**Branch**: `008-billing-foundation` | **Date**: 2026-02-10 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/008-billing-foundation/spec.md`

## Summary

Add a billing foundation for the family/caregiver mode using StoreKit2 Non-Consumable Premium Unlock. The plan covers a new `CaregiverEntitlement` data model, two backend endpoints (`/iap/claim` and `/me/entitlements`), iOS `EntitlementStore` with lifecycle auto-refresh, a centralized `FeatureGate` map, a Paywall view with purchase/restore/error/retry, and Settings integration. Patient mode remains free with zero billing entry points. All billing network operations use the existing full-screen "更新中" overlay. Tests-first sequencing is enforced.

## Technical Context

**Language/Version**: TypeScript (Node.js >=20, Next.js 16, Prisma 7.3), Swift 6.2 (SwiftUI, iOS 26 SDK, StoreKit2)
**Primary Dependencies**: Next.js App Router, Prisma, Supabase Auth (JWT); SwiftUI, StoreKit2, XCTest
**Storage**: PostgreSQL via Prisma (`api/prisma/schema.prisma`)
**Testing**: Vitest (API unit/integration), XCTest (iOS unit + UI smoke)
**Target Platform**: Web API (Vercel) + iOS app
**Project Type**: Mobile + API
**Performance Goals**: Entitlement refresh < 3s p95; status presentation updates in same session
**Constraints**: Full-screen "更新中" overlay during billing flows; patient mode always free; no external calls in CI tests; Prisma v7.3 init differs from prior versions
**Scale/Scope**: MVP: 1 Non-Consumable product, 1 entitlement tier (premium), 5 feature gates

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: Pass (spec is the source of truth).
- Traceability: Pass (plan ties each task to FR/NFR and tests).
- Test strategy: Pass (tests-first; Vitest mocks for JWS; StoreKit testing config for iOS; no external calls in CI).
- Security & privacy: Pass (caregiver auth required; conceal unauthorized as 404; deny-by-default; no PII in logs).
- Performance guardrails: Pass (explicit timing targets in spec NFR-001).
- UX/accessibility: Pass (reuse SchedulingRefreshOverlay; localized strings; VoiceOver labels).
- Documentation: Pass (contracts, quickstart, data model updated in same branch).

## Project Structure

### Documentation (this feature)

```text
specs/008-billing-foundation/
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
│   ├── iap/
│   │   └── claim/
│   │       └── route.ts              # POST /api/iap/claim
│   └── me/
│       └── entitlements/
│           └── route.ts              # GET /api/me/entitlements
├── src/
│   ├── services/
│   │   └── entitlementService.ts     # claim + read logic
│   ├── repositories/
│   │   └── entitlementRepo.ts        # Prisma CRUD for CaregiverEntitlement
│   └── validators/
│       └── iapValidator.ts           # claim payload validation
├── prisma/
│   ├── schema.prisma                 # + CaregiverEntitlement model + EntitlementStatus enum
│   └── migrations/
│       └── 2026MMDD_caregiver_entitlements/
└── tests/
    ├── integration/
    │   └── entitlement-claim.test.ts
    └── unit/
        └── iapValidator.test.ts

ios/MedicationApp/
├── Features/
│   └── Billing/
│       ├── EntitlementStore.swift
│       ├── FeatureGate.swift
│       ├── PaywallView.swift
│       └── PaywallViewModel.swift
├── Networking/
│   └── DTOs/
│       └── EntitlementDTO.swift
├── Shared/
│   └── Views/
│       └── (reuse SchedulingRefreshOverlay)
├── Resources/
│   └── Localizable.strings           # + billing keys
└── Tests/
    └── Billing/
        ├── EntitlementStoreTests.swift
        ├── FeatureGateTests.swift
        ├── PaywallUITests.swift
        └── PatientNoBillingUITests.swift
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). Billing APIs live under `/api/iap/` and `/api/me/`; iOS adds a new `Features/Billing/` module.

## Complexity Tracking

No constitution violations.

## Phase 0: Outline & Research

### Research Tasks

- Confirm StoreKit2 `Transaction.currentEntitlements` is suitable for local premium state without server round-trip.
- Determine JWS verification scope for MVP (structure + payload vs full Apple root cert chain).
- Confirm single `CaregiverEntitlement` table with `originalTransactionId` unique key prevents duplicate claims.
- Confirm `EntitlementStatus` enum with `ACTIVE`/`REVOKED` covers MVP revocation model.
- Confirm pure Swift enum for FeatureGate is sufficient (no server round-trip for gate checks).
- Confirm reuse of existing `SchedulingRefreshOverlay` for billing blocking overlay.

### Output

- `research.md` with decisions, rationales, and alternatives.

## Phase 1: Design & Contracts

### Data Model

- Define `CaregiverEntitlement` entity with fields, indexes, and constraints.
- Define `EntitlementStatus` enum (`ACTIVE`, `REVOKED`).
- Define iOS `EntitlementState` enum (`unknown`, `free`, `premium`).
- Define `FeatureGate` enum with tier requirements.

### API Contracts

- `POST /api/iap/claim` -- caregiver submits signed transaction for entitlement upsert.
- `GET /api/me/entitlements` -- caregiver reads server-recognized premium status and records.

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

1) Backend integration tests for claim + entitlements
   - **Files**: `api/tests/integration/entitlement-claim.test.ts`
   - **Covers**: valid claim upserts entitlement; duplicate claim same originalTransactionId idempotent; invalid productId rejected; unauthenticated rejected; GET /me/entitlements returns premium true/false
   - **Tests**: `cd api && npm test`

2) Backend unit test for iapValidator
   - **Files**: `api/tests/unit/iapValidator.test.ts`
   - **Covers**: missing fields, invalid productId, valid payload passes
   - **Tests**: `cd api && npm test`

3) iOS unit tests for EntitlementStore + FeatureGate
   - **Files**: `ios/MedicationApp/Tests/Billing/EntitlementStoreTests.swift`, `ios/MedicationApp/Tests/Billing/FeatureGateTests.swift`
   - **Covers**: state transitions (unknown -> premium, unknown -> free); FeatureGate returns correct access for premium vs free vs pro-only
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

4) iOS UI smoke tests
   - **Files**: `ios/MedicationApp/Tests/Billing/PaywallUITests.swift`, `ios/MedicationApp/Tests/Billing/PatientNoBillingUITests.swift`
   - **Covers**: Settings shows upgrade/restore in caregiver mode; Paywall displays; overlay blocks during refresh; Patient mode has zero billing entry points
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 2: Backend (Schema + Endpoints)

5) Prisma schema + migration
   - **Files**: `api/prisma/schema.prisma`, `api/prisma/migrations/*`
   - **Action**: Add `EntitlementStatus` enum and `CaregiverEntitlement` model; generate migration
   - **Tests**: `cd api && npm test`

6) Repository + Service + Validator
   - **Files**: `api/src/repositories/entitlementRepo.ts`, `api/src/services/entitlementService.ts`, `api/src/validators/iapValidator.ts`
   - **Action**: upsert by originalTransactionId, findByCaregiverId, claim logic, validation
   - **Tests**: `cd api && npm test`

7) API routes
   - **Files**: `api/app/api/iap/claim/route.ts`, `api/app/api/me/entitlements/route.ts`
   - **Action**: POST /api/iap/claim with requireCaregiver -> validate -> service.claim; GET /api/me/entitlements with requireCaregiver -> service.getEntitlements
   - **Tests**: `cd api && npm test`

8) Verify integration tests pass
   - **Tests**: `cd api && npm test`

### Phase 3: iOS (StoreKit2 + UI + Gate)

9) EntitlementStore
   - **Files**: `ios/MedicationApp/Features/Billing/EntitlementStore.swift`
   - **Action**: @Observable @MainActor class; states unknown/free/premium; iterate Transaction.currentEntitlements; call POST /api/iap/claim on purchase/restore success
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

10) FeatureGate
    - **Files**: `ios/MedicationApp/Features/Billing/FeatureGate.swift`
    - **Action**: Pure enum with requiredTier property; static isUnlocked function; gate definitions from spec FR-010/FR-011
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

11) PaywallView + PaywallViewModel
    - **Files**: `ios/MedicationApp/Features/Billing/PaywallView.swift`, `ios/MedicationApp/Features/Billing/PaywallViewModel.swift`
    - **Action**: Product.products(for:), displayPrice, purchase, AppStore.sync restore, error/retry, SchedulingRefreshOverlay
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

12) Settings integration (caregiver)
    - **Files**: `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift`
    - **Action**: Add Premium section with upgrade CTA, restore button, status display; visible only in caregiver mode
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

13) Patient mode guarantee
    - **Files**: `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift`
    - **Action**: Verify zero billing entry points in patient mode (enforced by UI tests in task 4)
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

14) Localizable.strings
    - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
    - **Action**: Add billing keys for premium section, paywall, errors, accessibility
    - **Tests**: N/A (string update)

15) Lifecycle auto-refresh
    - **Files**: `ios/MedicationApp/App/RootView.swift` or app entry, `ios/MedicationApp/Shared/SessionStore.swift`
    - **Action**: Hook EntitlementStore.refresh() on app launch, foreground (scenePhase .active), post caregiver login
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

16) Verify iOS tests pass
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 4: Docs / App Review Readiness

17) quickstart.md
    - **Files**: `specs/008-billing-foundation/quickstart.md`
    - **Action**: Sandbox purchase/restore test procedure; App Review notes

18) contracts/openapi.yaml
    - **Files**: `specs/008-billing-foundation/contracts/openapi.yaml`
    - **Action**: Full OpenAPI 3.0.3 spec for POST /iap/claim and GET /me/entitlements

19) data-model.md
    - **Files**: `specs/008-billing-foundation/data-model.md`
    - **Action**: CaregiverEntitlement entity, FeatureGate definitions, validation rules

20) Agent context update
    - **Action**: Run `.specify/scripts/bash/update-agent-context.sh cursor-agent`

## Acceptance Criteria

- Caregiver mode: Paywall purchase works; premium state activates; restore also returns premium.
- Patient mode: zero billing entry points across all screens.
- Premium check available both locally (iOS EntitlementStore) and server-side (GET /me/entitlements).
- All billing network operations show full-screen "更新中" overlay blocking interaction.
- All tests pass: iOS unit + UI smoke, backend integration.

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`
