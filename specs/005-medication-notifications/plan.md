# Implementation Plan: Medication Notifications

**Branch**: `005-medication-notifications` | **Date**: 2026-02-04 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/005-medication-notifications/spec.md`

## Summary

Deliver patient local reminders for pending medication slots with Settings controls, notification tap routing to Today + highlight, and caregiver in-app banners via realtime events. Use existing history month/day data for a rolling 7-day schedule, local notification identifiers for cancel/reschedule, and a server-side event emission on dose TAKEN with RLS-scoped realtime delivery.

## Technical Context

**Language/Version**: TypeScript (Node.js >=20), Swift 6.2.1 (SwiftUI, iOS 26 SDK)  
**Primary Dependencies**: Next.js App Router, Prisma, Supabase Realtime; SwiftUI, UserNotifications, XCTest  
**Storage**: PostgreSQL via Prisma  
**Testing**: Vitest (API unit/integration/contract), XCTest (iOS unit + UI smoke)  
**Target Platform**: Web API + iOS app  
**Project Type**: Mobile + API  
**Performance Goals**: Scheduling refresh <= 10s; caregiver banner appears <= 5s while foregrounded  
**Constraints**: No server cron; device-local scheduling only; full-screen "更新中" overlay on refresh; Asia/Tokyo slot boundaries  
**Scale/Scope**: MVP, single patient per session, 7-day rolling window

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: Pass (spec and clarifications are source of truth).
- Traceability: Pass (plan ties changes to FR/NFR and tests).
- Test strategy: Pass (unit/contract/UI smoke included; no external calls in CI).
- Security & privacy: Pass (RLS for events; concealment rules; no PII in logs).
- Performance guardrails: Pass (explicit refresh + banner timing targets).
- UX/accessibility: Pass (blocking overlay, permission guidance, clear retry/error UX).
- Documentation: Pass (contracts, quickstart, and plan updates included).

## Project Structure

### Documentation (this feature)

```text
specs/005-medication-notifications/
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
│   ├── patient/history/
│   └── patient/dose-records/
├── src/
│   ├── services/
│   ├── repositories/
│   └── validators/
└── tests/
    ├── contract/
    ├── integration/
    └── unit/

ios/MedicationApp/
├── Features/
│   ├── Settings/
│   ├── Today/
│   └── Notifications/
├── Networking/
├── Shared/
└── Tests/
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). Feature code will be organized under iOS feature modules for Settings/Today/Notifications and API service + repository layers for dose event emission.

## Complexity Tracking

No constitution violations.

## Phase 0: Outline & Research

### Research Tasks

- Confirm best practices for iOS local notification scheduling with stable identifiers and rescheduling on app lifecycle.
- Confirm recommended UX for notification permission denied states and in-app reminder banners.
- Confirm Supabase Realtime + RLS pattern for event emission from dose records.

### Output

- `research.md` with decisions, rationales, and alternatives.

## Phase 1: Design & Contracts

### Data Model

- Define notification preferences, reminder plan entries, and dose record event payloads.
- Capture relationships between dose record, scheduled slot, and caregiver visibility.

### API Contracts

- Document existing history month/day endpoints used for scheduling refresh.
- Document dose-record event payload (realtime channel) and insertion behavior.

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

### Phase 1: Tests/Contracts

1) Contract tests: history month/day endpoints used for scheduling refresh
   - **Done**: Request/response shapes validated; auth and concealment tested.
   - **Files**: `api/tests/contract/history-*.contract.test.ts`
   - **Tests**: `npm test` (from `api/`)

2) iOS unit tests: notification plan, ids, cancellation, routing
   - **Done**: Plan covers 7-day window, month crossover, ids stable; routing selects Today + highlight.
   - **Files**: `ios/MedicationApp/Tests/Notifications/*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

3) iOS UI smoke tests: Settings permission UX, overlay, notification tap, caregiver banner
   - **Done**: Denied/enabled flows verified; overlay blocks input; tap routes + highlight; banner appears on any screen.
   - **Files**: `ios/MedicationApp/Tests/Notifications/*`
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 2: Backend event emission + RLS

4) Add dose record event persistence
   - **Done**: Event row created when TAKEN is recorded; includes patientId, scheduledAt, takenAt, withinTime.
   - **Files**: `api/src/services/doseRecordService.ts`, `api/src/repositories/doseRecordRepo.ts`, `api/prisma/schema.prisma`, migration
   - **Tests**: `npm test`

5) Realtime + RLS policy for caregiver events
   - **Done**: RLS ensures caregiver can only read events for linked patients.
   - **Files**: `api/prisma/migrations/*`, `api/tests/integration/*event*.test.ts`
   - **Tests**: `npm test`

### Phase 3: iOS notification settings + scheduling engine

6) Notification permission + Settings toggles
   - **Done**: Master OFF by default; slot toggles ON; denied state disables toggles and shows guidance.
   - **Files**: `ios/MedicationApp/Features/Settings/*`
   - **Tests**: `xcodebuild ... test`

7) NotificationScheduler (plan + schedule/cancel)
   - **Done**: Builds 7-day plan from slotSummary, schedules primary + optional secondary, cancels secondary after TAKEN when no pending remain.
   - **Files**: `ios/MedicationApp/Features/Notifications/*`, `ios/MedicationApp/Networking/*`
   - **Tests**: `xcodebuild ... test`

8) Foreground refresh overlay
   - **Done**: Any scheduling refresh shows blocking "更新中" overlay; on failure shows error + retry.
   - **Files**: `ios/MedicationApp/Shared/Views/FullScreenContainer.swift`, scheduling UI integration
   - **Tests**: `xcodebuild ... test`

### Phase 4: Deep link navigation + highlight effect

9) Notification tap routing to Today slot
   - **Done**: Tap opens Today tab, scrolls to slot, triggers highlight or "already recorded" message if not pending.
   - **Files**: `ios/MedicationApp/Features/Today/*`, `ios/MedicationApp/Features/Notifications/*`
   - **Tests**: `xcodebuild ... test`

10) In-app banner when reminder fires while open
   - **Done**: In-app banner appears; if on Today, highlight slot for a few seconds.
   - **Files**: `ios/MedicationApp/Shared/*`, `ios/MedicationApp/Features/Notifications/*`
   - **Tests**: `xcodebuild ... test`

### Phase 5: Caregiver realtime subscription + banner

11) Realtime subscription and banner queue
   - **Done**: Foreground caregiver subscribes to events, filters withinTime, queues banners sequentially.
   - **Files**: `ios/MedicationApp/Features/Caregiver/*`, `ios/MedicationApp/Shared/Banner/*`
   - **Tests**: `xcodebuild ... test`

### Phase 6: Docs / quickstart updates

12) Update quickstart and contracts
   - **Done**: Quickstart lists scheduling refresh flow, event emission, and test commands; contracts reflect used endpoints.
   - **Files**: `specs/005-medication-notifications/quickstart.md`, `specs/005-medication-notifications/contracts/openapi.yaml`
   - **Tests**: N/A (doc update)
