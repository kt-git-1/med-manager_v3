# Implementation Plan: Dose Recording (003)

**Branch**: `003-dose-recording` | **Date**: 2026-02-03 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/003-dose-recording/spec.md`

## Summary

Add dose recording for scheduled doses across API and iOS: create idempotent taken records, derive missed/pending status, support caregiver create/delete, and schedule patient reminders with an auto-refreshing Today UI.

## Technical Context

**Language/Version**: TypeScript 5.9 (Node >=20) for API; Swift 6.2 for iOS  
**Primary Dependencies**: Next.js 16 App Router API routes, Prisma 7.3, Vitest, Playwright; SwiftUI, XCTest, UserNotifications  
**Storage**: PostgreSQL via Prisma  
**Testing**: Vitest (unit/integration/contract), Playwright (e2e), XCTest (iOS tests)  
**Target Platform**: Node 20 server, iOS 26+  
**Project Type**: Mobile + API  
**Performance Goals**: Today list refresh within 5 seconds; reminder delivery within 5 minutes  
**Constraints**: Idempotent dose recording, no patient undo, deny-by-default auth, no external calls in CI, no PII/tokens in logs  
**Scale/Scope**: MVP scale up to 10k patients and 100k scheduled doses/day

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: spec is the single source of truth for behavior. **Pass**
- Traceability: every change maps to spec + acceptance criteria + tests (or documented exception). **Pass**
- Test strategy: deterministic tests, no external calls in CI, regression tests for fixes. **Pass**
- Security & privacy: least privilege, deny-by-default, PII never in logs. **Pass**
- Performance guardrails: define or reference budgets/targets for web-api and iOS. **Pass**
- UX/accessibility: shared patterns, accessibility, error UX, and i18n readiness. **Pass**
- Documentation: ADRs for key decisions; module run/test docs updated with changes. **Pass**

Post-Phase 1 re-check: **Pass** (research, data model, contracts, and quickstart aligned with spec).

## Project Structure

### Documentation (this feature)

```text
specs/003-dose-recording/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
api/
├── app/
│   └── api/
│       ├── patient/
│       ├── patients/
│       └── schedule/
├── src/
│   ├── auth/
│   ├── repositories/
│   ├── services/
│   └── validators/
└── tests/
    ├── contract/
    ├── integration/
    ├── e2e/
    └── unit/

ios/MedicationApp/
├── Features/
├── Networking/
├── Services/
├── Shared/
└── Tests/
```

**Structure Decision**: Mobile + API. API changes live in `api/app/api` + `api/src` and iOS changes live in `ios/MedicationApp` feature modules and shared services.

## Complexity Tracking

No Constitution violations noted.
