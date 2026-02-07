# Implementation Plan: Inventory Tracking

**Branch**: `006-inventory-tracking` | **Date**: 2026-02-06 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/006-inventory-tracking/spec.md`

## Summary

Deliver caregiver-only inventory management with per-medication quantities and thresholds, automatic adjustments tied to TAKEN create/delete (idempotent), and realtime low/out banners without cron or push. Changes span API endpoints + data model, inventory adjustment/event logging, and iOS UI with full-screen blocking overlays.

## Technical Context

**Language/Version**: TypeScript (Node.js >=20), Swift 6.2.1 (SwiftUI, iOS 26 SDK)  
**Primary Dependencies**: Next.js App Router, Prisma, Supabase Realtime; SwiftUI, XCTest  
**Storage**: PostgreSQL via Prisma  
**Testing**: Vitest (API unit/integration/contract), XCTest (iOS unit + UI smoke)  
**Target Platform**: Web API + iOS app  
**Project Type**: Mobile + API  
**Performance Goals**: Inventory updates and banner alerts visible within 5 seconds while foregrounded  
**Constraints**: No cron/daemon workers; caregiver-only UI; conceal non-owned patients; full-screen "更新中" overlay during network calls  
**Scale/Scope**: MVP limited to caregiver mode and per-medication inventory tracking

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: Pass (spec is the source of truth).
- Traceability: Pass (plan ties steps to FR/NFR and tests).
- Test strategy: Pass (contract/integration/UI smoke; no external calls in CI).
- Security & privacy: Pass (deny-by-default, concealment).
- Performance guardrails: Pass (explicit update + banner timing targets).
- UX/accessibility: Pass (blocking overlay, reuse shared patterns).
- Documentation: Pass (contracts, quickstart, data model updated).

## Project Structure

### Documentation (this feature)

```text
specs/006-inventory-tracking/
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
│   └── patients/
│       └── [patientId]/
│           ├── inventory/
│           └── medications/
│               └── [medicationId]/
│                   └── inventory/
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
│   ├── Caregiver/
│   ├── MedicationList/
│   └── Inventory/
├── Networking/
├── Shared/
│   └── Banner/
└── Tests/
    ├── Caregiver/
    └── Inventory/
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). Inventory APIs live under caregiver patient routes; iOS UI under a new Inventory feature module.

## Complexity Tracking

No constitution violations.

## Phase 0: Outline & Research

### Research Tasks

- Confirm best practice for idempotent inventory adjustments tied to TAKEN records.
- Confirm Supabase Realtime event emission patterns for low/out transitions with RLS.
- Confirm UX copy and banner timing for caregiver alerts within existing GlobalBannerPresenter.

### Output

- `research.md` with decisions, rationales, and alternatives.

## Phase 1: Design & Contracts

### Data Model

- Define medication inventory fields, adjustment log, and inventory alert event entities.
- Capture validation rules for thresholds, clamp-to-zero, and alert state transitions.

### API Contracts

- Document inventory list, update, and adjustment endpoints under caregiver patient routes.
- Document inventory alert event payload for realtime consumption.

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

### Phase 1: Tests / Contracts

1) Contract tests for inventory endpoints
   - **Done**: Request/response shapes, auth, and concealment behaviors validated.
   - **Files**: `api/tests/contract/inventory*.contract.test.ts`
   - **Tests**: `cd api && npm test`

2) Integration tests for TAKEN-driven adjustments
   - **Done**: Create decrements once, delete increments back, clamp-to-zero enforced.
   - **Files**: `api/tests/integration/inventory-adjustment.test.ts`
   - **Tests**: `cd api && npm test`

3) iOS UI smoke tests for Inventory tab and overlay
   - **Done**: Empty state, list/detail, and blocking overlay verified.
   - **Files**: `ios/MedicationApp/Tests/Inventory/*`, `ios/MedicationApp/Tests/HistoryOverlayTests.swift`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 2: DB / Migrations

4) Add inventory fields and event/adjustment tables
   - **Done**: Medication inventory fields, adjustment log, and alert event tables added with safe defaults.
   - **Files**: `api/prisma/schema.prisma`, `api/prisma/migrations/*`
   - **Tests**: `cd api && npm test`

### Phase 3: API

5) Caregiver inventory endpoints + validation
   - **Done**: List, update, and adjust endpoints enforce caregiver auth and conceal non-owned patients.
   - **Files**: `api/app/api/patients/[patientId]/inventory/route.ts`, `api/app/api/patients/[patientId]/medications/[medicationId]/inventory/route.ts`, `api/src/validators/*`
   - **Tests**: `cd api && npm test`

6) TAKEN create/delete inventory updates + alert emission
   - **Done**: Adjustments occur only on new TAKEN and delete, with alert events only on state transitions.
   - **Files**: `api/src/services/doseRecordService.ts`, `api/src/services/medicationService.ts`, `api/src/repositories/*`
   - **Tests**: `cd api && npm test`

### Phase 4: iOS UI

7) Inventory tab UI (caregiver-only)
   - **Done**: Tab added, empty state when no patient, list/detail with enable, quantity, threshold, and actions.
   - **Files**: `ios/MedicationApp/Features/Inventory/*`, `ios/MedicationApp/Features/Caregiver/*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

8) Full-screen blocking overlay integration
   - **Done**: All inventory API calls are wrapped with the blocking "更新中" overlay.
   - **Files**: `ios/MedicationApp/Shared/Views/FullScreenContainer.swift`, `ios/MedicationApp/Features/Inventory/*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 5: Realtime

9) Caregiver realtime inventory banner
   - **Done**: Inventory low/out events are subscribed in caregiver mode and surfaced via GlobalBannerPresenter.
   - **Files**: `ios/MedicationApp/Features/Caregiver/*`, `ios/MedicationApp/Shared/Banner/*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 6: Docs

10) Update quickstart and contracts
   - **Done**: Quickstart documents endpoints, constraints, and test commands; contracts reflect inventory endpoints and events.
   - **Files**: `specs/006-inventory-tracking/quickstart.md`, `specs/006-inventory-tracking/contracts/openapi.yaml`
   - **Tests**: N/A (doc update)
