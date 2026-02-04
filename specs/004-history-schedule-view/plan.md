# Implementation Plan: History Schedule View

**Branch**: `004-history-schedule-view` | **Date**: 2026-02-03 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/004-history-schedule-view/spec.md`

## Summary

Add a History tab for patients and caregivers that shows a month calendar (last 3 months through month-end future days) and a day detail view with derived status dots and a blocking “更新中” overlay. Implement new history month/day endpoints (patient + caregiver), reuse existing schedule logic with a shared status derivation function, and add SwiftUI screens with a persistent legend, empty state, and retry UX.

## Technical Context

**Language/Version**: TypeScript (Node.js >=20), Swift (SwiftUI, iOS 26+ assumed by simulator target)  
**Primary Dependencies**: Next.js App Router, Prisma, Vitest, Playwright; SwiftUI, XCTest  
**Storage**: PostgreSQL via Prisma  
**Testing**: Vitest (API unit/integration/contract), Playwright e2e, XCTest (iOS)  
**Target Platform**: Web API + iOS app  
**Project Type**: Mobile + API  
**Performance Goals**: History month load p95 <= 3s; overlay appears <= 1s  
**Constraints**: Full-screen blocking overlay on all history fetches; Asia/Tokyo month boundaries  
**Scale/Scope**: MVP, single patient per session, 3-month history window

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: Pass (spec and clarifications are source of truth).
- Traceability: Pass (plan ties changes to FR/NFR and tests).
- Test strategy: Pass (contract/integration/iOS smoke included; no external calls in CI).
- Security & privacy: Pass (authz enforced, 404 conceal, no PII in logs).
- Performance guardrails: Pass (explicit latency target).
- UX/accessibility: Pass (blocking overlay, legend, empty state, error copy, VoiceOver labels).
- Documentation: Pass (contracts, quickstart, and plan updates included).

## Project Structure

### Documentation (this feature)

```text
specs/004-history-schedule-view/
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
│   ├── patient/
│   │   └── history/
│   │       ├── day/route.ts
│   │       └── month/route.ts
│   └── patients/[patientId]/
│       └── history/
│           ├── day/route.ts
│           └── month/route.ts
├── src/
│   ├── services/
│   │   ├── scheduleService.ts
│   │   └── scheduleResponse.ts
│   ├── repositories/
│   │   ├── doseRecordRepo.ts
│   │   └── regimenRepo.ts
│   └── validators/
│       └── schedule.ts
└── tests/
    ├── contract/
    ├── integration/
    └── unit/

ios/MedicationApp/
├── Features/
│   └── History/
├── Networking/
├── Shared/
└── Tests/
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). Feature-specific code will live under `api/app/api/.../history` and `ios/MedicationApp/Features/History`.

## Complexity Tracking

No constitution violations.

## Phase 0: Outline & Research

### Research Tasks

- Confirm best pattern to derive effectiveStatus from schedule + taken records and reuse existing schedule service.
- Confirm month/day response shapes and slot aggregation rule for status dots.
- Confirm UX handling for blocking overlay, error copy, and empty state copy.

### Output

- `research.md` with decisions, rationales, and alternatives.

## Phase 1: Design & Contracts

### Data Model

- Derive view models for day detail and month summary using scheduled doses + dose records.
- Capture slot summary aggregation and ordering rules.

### API Contracts

- Add patient and caregiver history endpoints with contract schemas.
- Ensure auth and concealment behaviors are reflected in responses.

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

### Tests (contract → integration → iOS smoke)

1) Contract tests: history month/day endpoints (patient + caregiver)
   - **Done**: Request/response shapes validated, auth and 404 concealment tested.
   - **Files**: `api/tests/contract/history-*.contract.test.ts`
   - **Tests**: `npm test` (from `api/`)

2) Integration tests: slot summary aggregation and status derivation
   - **Done**: MISSED > PENDING > TAKEN aggregation validated; day ordering by slot then name; 401/404 cases covered.
   - **Files**: `api/tests/integration/history-*.test.ts`
   - **Tests**: `npm test` (from `api/`)

3) iOS smoke tests: History tab, overlay, and empty state
   - **Done**: Calendar renders, day detail loads, overlay blocks input, retry works, empty state CTA routes to Link/Patients.
   - **Files**: `ios/MedicationApp/Tests/History*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### API

4) Shared schedule/status service
   - **Done**: `getScheduleWithStatus(patientId, from, to, tz)` returns scheduled doses with effectiveStatus; includes 60-min missed rule.
   - **Files**: `api/src/services/scheduleService.ts`, `api/src/services/scheduleResponse.ts`
   - **Tests**: `npm test`

5) Month history endpoints (patient + caregiver)
   - **Done**: Month range honors Asia/Tokyo boundaries, returns per-day slot summaries, hides slots with no doses.
   - **Files**: `api/app/api/patient/history/month/route.ts`, `api/app/api/patients/[patientId]/history/month/route.ts`
   - **Tests**: `npm test`

6) Day history endpoints (patient + caregiver)
   - **Done**: Day detail returns doses ordered by slot then medication name; effectiveStatus derived; auth enforced.
   - **Files**: `api/app/api/patient/history/day/route.ts`, `api/app/api/patients/[patientId]/history/day/route.ts`
   - **Tests**: `npm test`

### iOS

7) History tab navigation and empty state
   - **Done**: Add History tab for patient and caregiver; caregiver with no selected patient shows empty state and CTA.
   - **Files**: `ios/MedicationApp/Features/History/*`, tab configuration files
   - **Tests**: `xcodebuild ... test`

8) Calendar month view with slot dots and legend
   - **Done**: Month view renders dots per slot summary, legend always visible, last 3 months navigation, future days shown.
   - **Files**: `ios/MedicationApp/Features/History/*`
   - **Tests**: `xcodebuild ... test`

9) Day detail view and ordering
   - **Done**: Day detail list ordered by slot then medication name; shows status badge; handles pending/missed/taken.
   - **Files**: `ios/MedicationApp/Features/History/*`
   - **Tests**: `xcodebuild ... test`

10) Full-screen updating overlay + error/retry UX
   - **Done**: Overlay blocks interactions on all history fetches; error message and retry flow implemented.
   - **Files**: `ios/MedicationApp/Shared/Views/FullScreenContainer.swift`, `ios/MedicationApp/Features/History/*`
   - **Tests**: `xcodebuild ... test`

### Docs

11) Update quickstart and contracts
   - **Done**: `quickstart.md` includes new endpoints and test commands; OpenAPI contract published.
   - **Files**: `specs/004-history-schedule-view/quickstart.md`, `specs/004-history-schedule-view/contracts/openapi.yaml`
   - **Tests**: N/A (doc update)
