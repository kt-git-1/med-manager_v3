# Implementation Plan: History Retention Limit

**Branch**: `010-history-retention` | **Date**: 2026-02-10 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/010-history-retention/spec.md`

## Summary

Using the billing foundation from 008 and the history views from 004, restrict free users (both patient and caregiver modes) to viewing the most recent 30 days of medication history. Premium users can view all history. The restriction is enforced server-side on the 4 existing history endpoints (`/api/patient/history/month`, `/api/patient/history/day`, `/api/patients/{patientId}/history/month`, `/api/patients/{patientId}/history/day`), returning a stable `HISTORY_RETENTION_LIMIT` error. The iOS app displays a retention banner and a lock overlay — with paywall navigation in caregiver mode and informational messaging (no billing UI) in patient mode. No database changes; this is a view restriction only.

## Technical Context

**Language/Version**: TypeScript (Node.js >=20, Next.js 16, Prisma 7.3), Swift 6.2 (SwiftUI, iOS 26 SDK)  
**Primary Dependencies**: Next.js App Router, Prisma, Supabase Auth (JWT); SwiftUI, StoreKit2, XCTest  
**Storage**: PostgreSQL via Prisma (`api/prisma/schema.prisma`) — no new tables  
**Testing**: Vitest (API integration/contract), XCTest (iOS unit + UI smoke)  
**Target Platform**: Web API (Vercel) + iOS app  
**Project Type**: Mobile + API  
**Performance Goals**: Retention check < 200ms p95 (date comparison + single entitlement lookup, no transaction required)  
**Constraints**: Full-screen "更新中" overlay during async ops; patient mode has zero billing UI; Asia/Tokyo timezone for all cutoff calculations  
**Scale/Scope**: MVP: retention gate on 4 history endpoints, 1 constant (RETENTION_DAYS_FREE = 30), cutoff computed per-request

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Spec-Driven Development**: Pass (spec at `specs/010-history-retention/spec.md` is source of truth).
- **Traceability**: Pass (every task maps to FR/NFR + acceptance scenarios + tests).
- **Test strategy**: Pass (tests-first; Vitest for backend; XCTest for iOS; no external calls in CI).
- **Security & privacy**: Pass (server-side enforcement mandatory; existing auth/concealment policies unchanged; no PII in logs).
- **Performance guardrails**: Pass (retention check is a date comparison + single entitlement lookup — no transaction needed for read-only queries).
- **UX/accessibility**: Pass (reuses SchedulingRefreshOverlay; localized strings; VoiceOver labels; distinct lock UIs for caregiver vs patient).
- **Documentation**: Pass (quickstart, data model, contracts updated in same branch).

## Project Structure

### Documentation (this feature)

```text
specs/010-history-retention/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── openapi.yaml     # Phase 1 output — HISTORY_RETENTION_LIMIT on 4 history endpoints
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
api/
├── app/api/
│   ├── patient/history/
│   │   ├── month/route.ts                        # MODIFY: add retention check after auth
│   │   └── day/route.ts                          # MODIFY: add retention check after auth
│   └── patients/[patientId]/history/
│       ├── month/route.ts                        # MODIFY: add retention check after auth
│       └── day/route.ts                          # MODIFY: add retention check after auth
├── src/
│   ├── services/
│   │   ├── historyRetentionService.ts            # NEW: checkRetention + isPremium resolution
│   │   └── historyRetentionConstants.ts          # NEW: RETENTION_DAYS_FREE = 30
│   └── errors/
│       └── historyRetentionError.ts              # NEW: HistoryRetentionError class
└── tests/
    ├── integration/
    │   └── history-retention.test.ts             # NEW: retention enforcement tests
    └── contract/
        └── history-retention.contract.test.ts    # NEW: HISTORY_RETENTION_LIMIT contract case

ios/MedicationApp/
├── Features/
│   ├── History/
│   │   ├── HistoryViewModel.swift                # MODIFY: retention error state
│   │   ├── HistoryMonthView.swift                # MODIFY: banner + lock UI trigger
│   │   └── HistoryRetentionLockView.swift        # NEW: lock overlay (caregiver/patient variants)
│   └── Billing/
│       └── FeatureGate.swift                     # MODIFY: add retentionDaysFree constant + cutoffDate helper
├── Networking/
│   ├── APIError.swift                            # MODIFY: add .historyRetentionLimit case
│   └── APIClient.swift                           # MODIFY: parse HISTORY_RETENTION_LIMIT before generic 403
├── Resources/
│   └── Localizable.strings                       # MODIFY: add retention localization keys
└── Tests/
    ├── History/
    │   ├── HistoryRetentionTests.swift           # NEW: cutoff calc + lock logic unit tests
    │   └── HistoryRetentionUITests.swift         # NEW: UI smoke tests
    └── Billing/
        └── PatientNoBillingUITests.swift         # MODIFY: verify no paywall in patient lock UI
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). This feature adds a new `historyRetentionService` module and a new `HistoryRetentionLockView` on iOS. It modifies the 4 existing history routes and the existing `HistoryViewModel` / `HistoryMonthView` to handle the retention gate.

## Complexity Tracking

No constitution violations. No new abstractions or layers introduced beyond the retention service.

## Phase 0: Outline & Research

### Research Tasks

All unknowns resolved from 008/009 foundation and codebase inspection:

- **Cutoff date calculation**: Server-side in Asia/Tokyo using `Intl.DateTimeFormat` (already used in schedule services via `getLocalDateKey`). `cutoffDate = todayTokyo - 29 days` (inclusive, so exactly 30 days of viewable history). Constant: `RETENTION_DAYS_FREE = 30`.
- **Premium resolution for caregiver sessions**: Query `CaregiverEntitlement` for `caregiverId = session.caregiverUserId` with `status: "ACTIVE"`. Same pattern as `linkingService.ts` lines 53-55.
- **Premium resolution for patient sessions**: Query `CaregiverPatientLink` with `patientId = session.patientId` and `status: "ACTIVE"` (unique constraint, 1:1) to find `caregiverId`, then query `CaregiverEntitlement` for that caregiver. If the linked caregiver has an ACTIVE entitlement, the patient inherits premium.
- **Month straddling rule**: MVP locks the entire month if `cutoffDate` falls within it. A month is accessible only when `firstDayOfMonth >= cutoffDate`. This avoids partial-month data complexity.
- **Error class pattern**: New `HistoryRetentionError` extending `Error` with `statusCode: 403`, `cutoffDate: string`, `retentionDays: number`. Caught in each route handler before `errorResponse()`, same pattern as `PatientLimitError` in `api/app/api/patients/route.ts`.
- **iOS error differentiation**: Parse 403 response body for `"code": "HISTORY_RETENTION_LIMIT"` before the generic 403 handler (which calls `handleAuthFailure` — incorrect for retention errors). Same approach as `parsePatientLimitExceeded` in `APIClient.swift`.
- **No transaction needed**: Retention checks are read-only (date comparison + entitlement lookup). No need for Prisma `$transaction`.

### Output

- `research.md` with all decisions, rationales, and alternatives consolidated.

## Phase 1: Design & Contracts

### Data Model

No new entities. Retention logic uses existing entities from features 002, 004, and 008:
- `CaregiverEntitlement` — premium status lookup
- `CaregiverPatientLink` — patient-to-caregiver resolution (1:1, unique on `patientId`)

New artifacts:
- `HistoryRetentionError` class (backend domain error)
- `RETENTION_DAYS_FREE` constant (= 30)

### API Contracts

The 4 existing history endpoints gain a new 403 response variant:

```json
{
  "code": "HISTORY_RETENTION_LIMIT",
  "message": "履歴の閲覧は直近30日間に制限されています。",
  "cutoffDate": "2026-01-12",
  "retentionDays": 30
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
- Security & privacy: Pass (server-side enforcement; no PII in logs; existing auth/RLS unchanged)
- Performance guardrails: Pass (date comparison + single entitlement lookup)
- UX/accessibility: Pass (reuses overlay; localized; VoiceOver labels; distinct caregiver/patient lock UIs)
- Documentation: Pass (quickstart, data model, contracts updated)

## Phase 2: Implementation Plan (Tasks)

### Phase 1: Tests / Contracts (test-first)

1) Backend integration tests for history retention
   - **Files**: `api/tests/integration/history-retention.test.ts`
   - **Covers**: free caregiver month/day before cutoff → 403 HISTORY_RETENTION_LIMIT; free caregiver within range → 200; premium caregiver any date → 200; straddling month → 403; free patient linked to free caregiver → 403; free patient linked to premium caregiver → 200
   - **Tests**: `cd api && npm test`

2) Backend contract test for HISTORY_RETENTION_LIMIT response shape
   - **Files**: `api/tests/contract/history-retention.contract.test.ts`
   - **Covers**: Response body `{ code, message, cutoffDate, retentionDays }` with status 403; cutoffDate format is YYYY-MM-DD; retentionDays is 30
   - **Tests**: `cd api && npm test`

3) iOS unit tests for retention logic
   - **Files**: `ios/MedicationApp/Tests/History/HistoryRetentionTests.swift`
   - **Covers**: cutoffDate calculation (Tokyo TZ, inclusive, midnight boundary); error code parsing maps to `.historyRetentionLimit`; banner text selection (free vs premium); patient-mode lock has no paywall buttons
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

4) iOS UI smoke tests
   - **Files**: `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift`
   - **Covers**: caregiver past navigation → lock → paywall; patient past navigation → lock (no purchase UI); premium → no lock; overlay blocks interaction
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 2: Backend (enforce retention)

5) HistoryRetentionError class and constants
   - **Files**: `api/src/errors/historyRetentionError.ts`, `api/src/services/historyRetentionConstants.ts`
   - **Action**: `HistoryRetentionError` extends `Error` with `cutoffDate: string`, `retentionDays: number`, `statusCode: 403`. Constant `RETENTION_DAYS_FREE = 30`.
   - **Tests**: `cd api && npm test`

6) History retention service
   - **Files**: `api/src/services/historyRetentionService.ts`
   - **Action**: Implement `getTodayTokyo()`, `getCutoffDate()`, `isPremiumForCaregiver(caregiverId)`, `isPremiumForPatient(patientId)`, `checkRetentionForDay(dateStr, sessionType, sessionId)`, `checkRetentionForMonth(year, month, sessionType, sessionId)`. Uses Prisma directly (read-only, no transaction).
   - **Tests**: `cd api && npm test`

7) Wire retention check into 4 history routes
   - **Files**: `api/app/api/patient/history/month/route.ts`, `api/app/api/patient/history/day/route.ts`, `api/app/api/patients/[patientId]/history/month/route.ts`, `api/app/api/patients/[patientId]/history/day/route.ts`
   - **Action**: After auth/validation, before data fetch: call `checkRetentionForMonth/Day(sessionType, sessionId)`. Catch `HistoryRetentionError` before `errorResponse()` and return custom 403 JSON body with `{ code, message, cutoffDate, retentionDays }`.
   - **Tests**: `cd api && npm test`

8) Verify all backend tests pass
   - **Tests**: `cd api && npm test`

### Phase 3: iOS (UX wiring)

9) Add `historyRetentionLimit` to APIError + parsing
    - **Files**: `ios/MedicationApp/Networking/APIError.swift`, `ios/MedicationApp/Networking/APIClient.swift`
    - **Action**: Add case `.historyRetentionLimit(cutoffDate: String, retentionDays: Int)`. Add `parseHistoryRetentionLimit(from:)` method. Insert check in `mapErrorIfNeeded` case 403 block, before `handleAuthFailure`.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

10) Update HistoryViewModel with retention state
    - **Files**: `ios/MedicationApp/Features/History/HistoryViewModel.swift`
    - **Action**: Add `@Published var retentionLocked = false`, `retentionCutoffDate: String?`, `retentionDays: Int?`. In `loadMonth`/`loadDay` catch blocks: detect `.historyRetentionLimit` → set retention state. Clear on successful loads.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

11) Create HistoryRetentionLockView
    - **Files**: `ios/MedicationApp/Features/History/HistoryRetentionLockView.swift`
    - **Action**: Caregiver variant: title + body + "アップグレード" / "購入を復元" / "閉じる". Patient variant: title + body (different text) + optional "更新" + no purchase buttons. Wire to PaywallView via `.sheet` in caregiver mode.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

12) Add retention banner + lock wiring to HistoryMonthView
    - **Files**: `ios/MedicationApp/Features/History/HistoryMonthView.swift`, `ios/MedicationApp/Features/Billing/FeatureGate.swift`
    - **Action**: Add banner below header (free: "無料：直近30日まで（{cutoffDate}〜今日）", premium: "全期間表示中"). Add `retentionDaysFree = 30` and `historyCutoffDate()` to FeatureGate. Show `HistoryRetentionLockView` when `viewModel.retentionLocked == true`.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

13) Add localization strings
    - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
    - **Action**: Add keys for retention banner (free/premium), lock UI (caregiver title/body, patient title/body), and buttons (upgrade/restore/close/refresh).

14) Verify all iOS tests pass
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 4: Docs / Finalization

15) Generate spec artifacts
    - **Files**: `specs/010-history-retention/research.md`, `data-model.md`, `quickstart.md`, `contracts/openapi.yaml`
    - **Action**: Write all design artifacts based on decisions documented in this plan.

16) Agent context update
    - **Action**: Run `.specify/scripts/bash/update-agent-context.sh cursor-agent`

## Acceptance Criteria

- Free users (patient/caregiver) cannot view history older than 30 days; attempting to do so shows a lock screen.
- Premium users (caregiver who purchased, or patient linked to premium caregiver) can view all history without restriction.
- Backend enforces the limit — client modifications cannot bypass it.
- Patient-mode lock screens contain zero billing, paywall, or upgrade UI.
- Caregiver-mode lock screens provide paywall navigation (upgrade/restore/close).
- Retention banner is visible on the history tab (free: range info, premium: "全期間表示中").
- Overlay blocks interactions during history fetch and entitlement refresh.
- All required tests pass (iOS unit + UI smoke, backend integration + contract).

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`
