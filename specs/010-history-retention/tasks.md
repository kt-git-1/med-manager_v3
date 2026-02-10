# Tasks: History Retention Limit

**Input**: Design documents from `/specs/010-history-retention/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/openapi.yaml

**Tests**: Tests are REQUIRED (spec Testing Requirements mandate integration, unit, and UI smoke tests). Phase 1 is tests-first.

**Organization**: Tasks are grouped into 4 phases (Tests-first, Backend, iOS, Docs) with user story labels for traceability.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 = Free retention limit (30 days) for caregiver + patient, US2 = Premium unlimited (caregiver premium implies patient unlimited), US3 = Backend enforcement + stable error contract, US4 = iOS lock UX + overlay + paywall routing, US5 = Docs / contracts / QA

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

- [x] T001 [P] [US3] Create test fixtures and mock setup for history retention integration tests in `api/tests/integration/history-retention.test.ts`
  - **Why**: Integration tests need to mock auth verifiers, Prisma queries (entitlement lookup, patient link lookup), and history route imports to test retention enforcement across all 4 endpoints (FR-001, FR-002, FR-003, FR-006, FR-007)
  - **Files**: `api/tests/integration/history-retention.test.ts`
  - **Done**: (1) Mock `verifySupabaseJwt` for caregiver sessions and `patientSessionVerifier` for patient sessions, (2) Mock `prisma.caregiverEntitlement.findFirst` for premium/free determination, (3) Mock `prisma.caregiverPatientLink.findFirst` for patient→caregiver resolution, (4) Mock schedule/dose data fetchers used by the history routes to return minimal valid data, (5) Helper functions `caregiverHeaders()` and `patientHeaders()` for auth, (6) Helper `setFreeUser()` / `setPremiumUser()` / `setPatientLinkedToPremium()` / `setPatientLinkedToFree()` for fixture switching
  - **Test**: `cd api && npm test`

- [x] T002 [P] [US3] Create integration test cases for retention enforcement across caregiver and patient sessions in `api/tests/integration/history-retention.test.ts`
  - **Why**: Validates server-side gate for free vs premium, both session types, month/day endpoints, straddling months, and within-range requests (FR-001 through FR-007, NFR-001, SC-003)
  - **Files**: `api/tests/integration/history-retention.test.ts`
  - **Done**: Test cases for: (1) free caregiver — month entirely before cutoff → 403 HISTORY_RETENTION_LIMIT with correct `cutoffDate` (YYYY-MM-DD) and `retentionDays: 30`, (2) free caregiver — day before cutoff → 403 HISTORY_RETENTION_LIMIT, (3) free caregiver — straddling month (cutoff falls within month) → 403 HISTORY_RETENTION_LIMIT, (4) free caregiver — day within range [cutoffDate, todayTokyo] → 200 with data, (5) premium caregiver — month before cutoff → 200 with data, (6) premium caregiver — day before cutoff → 200 with data, (7) free patient session linked to free caregiver — day before cutoff → 403 HISTORY_RETENTION_LIMIT, (8) free patient session linked to premium caregiver — day before cutoff → 200 with data (premium inherited), (9) unauthenticated request → 401 (existing behaviour preserved)
  - **Test**: `cd api && npm test`

- [x] T003 [P] [US3] Create contract test for HISTORY_RETENTION_LIMIT response shape in `api/tests/contract/history-retention.contract.test.ts`
  - **Why**: Validates the stable error response shape that iOS relies on for lock UI triggering (FR-003, contracts/openapi.yaml HistoryRetentionLimitResponse)
  - **Files**: `api/tests/contract/history-retention.contract.test.ts`
  - **Done**: Test cases for: (1) response status is 403, (2) response body has `code: "HISTORY_RETENTION_LIMIT"`, (3) response body has `cutoffDate` as string matching YYYY-MM-DD format, (4) response body has `retentionDays` as integer equal to 30, (5) response body has `message` as string
  - **Test**: `cd api && npm test`

### iOS Unit Tests

- [x] T004 [P] [US1] Create unit tests for cutoff date calculation, error parsing, and banner text in `ios/MedicationApp/Tests/History/HistoryRetentionTests.swift`
  - **Why**: Validates client-side cutoff computation (Asia/Tokyo), error code mapping to lock state, and banner text selection for free vs premium (FR-001, FR-008, spec iOS Unit Tests)
  - **Files**: `ios/MedicationApp/Tests/History/HistoryRetentionTests.swift`
  - **Done**: Test cases for: (1) `FeatureGate.historyCutoffDate()` returns todayTokyo - 29 days in YYYY-MM-DD format, (2) cutoff calculation at midnight JST boundary resolves correctly, (3) `APIError.historyRetentionLimit` is parsed from JSON `{ code: "HISTORY_RETENTION_LIMIT", cutoffDate: "...", retentionDays: 30 }`, (4) retention error is NOT treated as a generic 403 auth failure, (5) free entitlement state → banner text contains cutoffDate, (6) premium entitlement state → banner text is "全期間表示中", (7) `FeatureGate.retentionDaysFree` equals 30
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### iOS UI Smoke Tests

- [x] T005 [P] [US4] Create UI smoke test for caregiver mode lock UI with paywall buttons in `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift`
  - **Why**: Validates that a free caregiver navigating to a month/day before cutoff sees the lock overlay with upgrade, restore, and close buttons, and that "アップグレード" opens the paywall (FR-009, SC-001)
  - **Files**: `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift`
  - **Done**: Test cases for: (1) free caregiver navigates to month before cutoff → lock overlay with accessibilityIdentifier "HistoryRetentionLockView" appears, (2) lock overlay contains button with text "アップグレード", (3) lock overlay contains button with text "購入を復元", (4) lock overlay contains button with text "閉じる", (5) tapping "アップグレード" presents PaywallView sheet
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T006 [P] [US4] Create UI smoke test for patient mode lock UI with NO purchase buttons in `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift`
  - **Why**: Validates that patient mode lock screen has zero billing/paywall UI and shows informational messaging only (FR-010, SC-004)
  - **Files**: `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift`
  - **Done**: Test cases for: (1) free patient navigates to month before cutoff → lock overlay appears, (2) lock overlay does NOT contain "アップグレード" button, (3) lock overlay does NOT contain "購入を復元" button, (4) lock overlay contains informational text about caregiver premium, (5) optional "更新" button is present for refresh
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T007 [P] [US2] Create UI smoke test for premium user viewing all history without lock in `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift`
  - **Why**: Validates that premium users (caregiver or patient linked to premium caregiver) can freely navigate to old history without encountering lock UI (FR-008 premium banner, SC-002)
  - **Files**: `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift`
  - **Done**: Test cases for: (1) premium caregiver navigates to month before cutoff → data loads, no "HistoryRetentionLockView" present, (2) banner shows "全期間表示中" text
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T008 [P] [US4] Create UI smoke test for "更新中" overlay blocking interaction during history fetch in `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift`
  - **Why**: Validates that the full-screen overlay blocks user interaction during history fetch operations (FR-012, NFR-002)
  - **Files**: `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift`
  - **Done**: Test cases for: (1) during history month load, "SchedulingRefreshOverlay" accessibilityIdentifier is present, (2) overlay is not interactable (taps do not pass through)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: All test files exist and define expected behavior. Tests fail because backend retention service and iOS lock UI do not exist yet.

---

## Phase 2: Backend — Enforce History Retention (US1 + US3)

**Purpose**: Add HistoryRetentionError, retention constant, retention service, and wire the check into the 4 existing history routes. After this phase, backend tests (T001-T003) must pass.

### Constants & Error Class

- [x] T009 [P] [US3] Create RETENTION_DAYS_FREE constant in `api/src/services/historyRetentionConstants.ts`
  - **Why**: Single source of truth for the free-plan history retention window; easily adjustable for future plan tiers (FR-001, research decision 1)
  - **Files**: `api/src/services/historyRetentionConstants.ts`
  - **Done**: (1) Exports `RETENTION_DAYS_FREE = 30` as a named constant, (2) file is importable from historyRetentionService.ts
  - **Test**: `cd api && npm test`

- [x] T010 [P] [US3] Create HistoryRetentionError class in `api/src/errors/historyRetentionError.ts`
  - **Why**: Domain error carrying cutoffDate and retentionDays for stable 403 response (FR-003, data-model.md HistoryRetentionError). Follows PatientLimitError pattern from `api/src/errors/patientLimitError.ts`.
  - **Files**: `api/src/errors/historyRetentionError.ts`
  - **Done**: (1) `HistoryRetentionError` extends `Error`, (2) has properties: `cutoffDate: string` (YYYY-MM-DD), `retentionDays: number`, `statusCode: 403`, (3) constructor accepts `(cutoffDate: string, retentionDays: number)`, (4) message defaults to `"履歴の閲覧は直近30日間に制限されています。"`
  - **Test**: `cd api && npm test`

### Retention Service

- [x] T011 [US3] Implement history retention service in `api/src/services/historyRetentionService.ts`
  - **Why**: Centralised retention check logic for all 4 history endpoints — computes cutoff date, resolves premium status for both caregiver and patient sessions, and throws HistoryRetentionError for blocked requests (FR-001 through FR-007, research decisions 1-3, 7)
  - **Files**: `api/src/services/historyRetentionService.ts`
  - **Done**: Exported functions: (1) `getTodayTokyo(): string` — returns current date in YYYY-MM-DD format using Asia/Tokyo timezone, (2) `getCutoffDate(): string` — returns todayTokyo - 29 days in YYYY-MM-DD, (3) `isPremiumForCaregiver(caregiverId: string): Promise<boolean>` — queries `prisma.caregiverEntitlement.findFirst({ where: { caregiverId, status: "ACTIVE" } })`, (4) `isPremiumForPatient(patientId: string): Promise<boolean>` — queries `prisma.caregiverPatientLink.findFirst({ where: { patientId, status: "ACTIVE" } })` then checks caregiver entitlement, (5) `checkRetentionForDay(dateStr: string, sessionType: "caregiver" | "patient", sessionId: string): Promise<void>` — if `dateStr < cutoffDate` and not premium, throws `HistoryRetentionError`, (6) `checkRetentionForMonth(year: number, month: number, sessionType: "caregiver" | "patient", sessionId: string): Promise<void>` — computes firstDayOfMonth; if `firstDayOfMonth < cutoffDate` and not premium, throws `HistoryRetentionError`. All queries are read-only (no transaction needed).
  - **Test**: `cd api && npm test`

### Route Wiring

- [x] T012 [US1] Wire retention check into patient history month route in `api/app/api/patient/history/month/route.ts`
  - **Why**: Enforces the 30-day retention gate on patient month history requests (FR-002, FR-004, NFR-001)
  - **Files**: `api/app/api/patient/history/month/route.ts`
  - **Done**: (1) Import `checkRetentionForMonth` from `historyRetentionService` and `HistoryRetentionError` from `historyRetentionError`, (2) After `requirePatient(authHeader)` and validation, before data fetch: call `await checkRetentionForMonth(year, month, "patient", session.patientId)`, (3) In catch block, BEFORE `errorResponse(error)`: if `error instanceof HistoryRetentionError` → return `new Response(JSON.stringify({ code: "HISTORY_RETENTION_LIMIT", message: error.message, cutoffDate: error.cutoffDate, retentionDays: error.retentionDays }), { status: 403, headers: { "content-type": "application/json" } })`
  - **Test**: `cd api && npm test`

- [x] T013 [P] [US1] Wire retention check into patient history day route in `api/app/api/patient/history/day/route.ts`
  - **Why**: Enforces the 30-day retention gate on patient day history requests (FR-002, FR-005, NFR-001)
  - **Files**: `api/app/api/patient/history/day/route.ts`
  - **Done**: Same pattern as T012 but using `checkRetentionForDay(dateParam, "patient", session.patientId)` after auth/validation
  - **Test**: `cd api && npm test`

- [x] T014 [US1] Wire retention check into caregiver history month and day routes in `api/app/api/patients/[patientId]/history/month/route.ts` and `api/app/api/patients/[patientId]/history/day/route.ts`
  - **Why**: Enforces the 30-day retention gate on caregiver history requests for both month and day endpoints (FR-002, FR-004, FR-005, NFR-001)
  - **Files**: `api/app/api/patients/[patientId]/history/month/route.ts`, `api/app/api/patients/[patientId]/history/day/route.ts`
  - **Done**: (1) Month route: after `requireCaregiver` + `assertCaregiverPatientScope` + validation, call `await checkRetentionForMonth(year, month, "caregiver", session.caregiverUserId)`. Same HistoryRetentionError catch as T012. (2) Day route: after auth + validation, call `await checkRetentionForDay(dateParam, "caregiver", session.caregiverUserId)`. Same catch pattern.
  - **Test**: `cd api && npm test`

### Verification

- [x] T015 [US3] Verify all backend tests pass (T001-T003) against implemented code
  - **Why**: Confirms backend retention enforcement satisfies test expectations (SC-003, SC-005)
  - **Files**: `api/tests/integration/history-retention.test.ts`, `api/tests/contract/history-retention.contract.test.ts`
  - **Done**: `cd api && npm test` exits 0 with all history-retention integration and contract tests passing
  - **Test**: `cd api && npm test`

**Checkpoint**: Backend gate complete. Free users requesting history before cutoffDate receive 403 HISTORY_RETENTION_LIMIT. Premium users (including patients linked to premium caregivers) proceed. Integration and contract tests pass.

---

## Phase 3: iOS — Lock UI + Banner + Paywall Integration (US1 + US2 + US4)

**Purpose**: Wire the retention error into the iOS networking layer, add retention state to HistoryViewModel, create the lock overlay view (caregiver paywall variant + patient info-only variant), add the retention banner, and add localization strings. After this phase, iOS tests (T004-T008) must pass.

### Networking Layer

- [x] T016 [P] [US4] Add `historyRetentionLimit(cutoffDate:retentionDays:)` case to APIError enum in `ios/MedicationApp/Networking/APIError.swift`
  - **Why**: Distinct error case so the UI can differentiate a retention rejection from a generic auth failure and show the lock overlay instead of logging out (research decision 6)
  - **Files**: `ios/MedicationApp/Networking/APIError.swift`
  - **Done**: (1) New case `historyRetentionLimit(cutoffDate: String, retentionDays: Int)` added to `APIError` enum, (2) `errorDescription` returns localized retention limit message using `NSLocalizedString("history.retention.lock.caregiver.body", ...)`, (3) enum remains `Error`-conforming and `LocalizedError`-conforming
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T017 [US4] Parse HISTORY_RETENTION_LIMIT in APIClient.mapErrorIfNeeded() before generic 403 handler in `ios/MedicationApp/Networking/APIClient.swift`
  - **Why**: The generic 403 handler calls `handleAuthFailure` which clears the session — incorrect for retention errors. Must intercept HISTORY_RETENTION_LIMIT first (research decision 6). Follows `parsePatientLimitExceeded` pattern at line 450.
  - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
  - **Done**: (1) Add private method `parseHistoryRetentionLimit(from data: Data) -> APIError?` that decodes `{ code: "HISTORY_RETENTION_LIMIT", cutoffDate: String, retentionDays: Int }` and returns `.historyRetentionLimit(cutoffDate:retentionDays:)` or nil, (2) In `mapErrorIfNeeded()`, inside the `case 403:` block, AFTER `parsePatientLimitExceeded` check and BEFORE `handleAuthFailure`: call `parseHistoryRetentionLimit(from: data)` — if non-nil, throw the returned error, (3) If not a retention error, fall through to existing 403 handling
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### ViewModel

- [x] T018 [US1] Add retention state properties to HistoryViewModel and handle `.historyRetentionLimit` in catch blocks in `ios/MedicationApp/Features/History/HistoryViewModel.swift`
  - **Why**: The ViewModel must expose retention lock state so the view can show the lock overlay or banner, and must distinguish retention errors from generic fetch failures (FR-008, SC-001)
  - **Files**: `ios/MedicationApp/Features/History/HistoryViewModel.swift`
  - **Done**: (1) Add `@Published var retentionLocked = false`, `@Published var retentionCutoffDate: String?`, `@Published var retentionDays: Int?`, (2) In `loadMonth()` catch block: if error is `APIError.historyRetentionLimit(let cutoffDate, let retentionDays)` → set `retentionLocked = true`, `retentionCutoffDate = cutoffDate`, `self.retentionDays = retentionDays`; do NOT set `monthErrorMessage` (lock UI handles this), (3) Same handling in `loadDay()` catch block with the day-specific retention state, (4) On successful month/day loads: reset `retentionLocked = false`, `retentionCutoffDate = nil`, `retentionDays = nil`
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Lock View

- [x] T019 [P] [US4] Create HistoryRetentionLockView with caregiver and patient variants in `ios/MedicationApp/Features/History/HistoryRetentionLockView.swift`
  - **Why**: Lock overlay shown when free user navigates to history before cutoff. Caregiver variant has paywall buttons (FR-009); patient variant has no billing UI (FR-010). Follows PaywallView patterns from `ios/MedicationApp/Features/Billing/PaywallView.swift`.
  - **Files**: `ios/MedicationApp/Features/History/HistoryRetentionLockView.swift`
  - **Done**: (1) `HistoryRetentionLockView` takes `mode: AppMode`, `cutoffDate: String`, `entitlementStore: EntitlementStore`, `onDismiss: () -> Void`, `onRefresh: (() -> Void)?`, (2) Caregiver variant (mode == .caregiver): title "プレミアムで全期間の履歴を閲覧", body "30日より前の履歴はプレミアムで閲覧できます", buttons "アップグレード" (presents PaywallView via .sheet), "購入を復元" (calls entitlementStore.restore()), "閉じる" (calls onDismiss), (3) Patient variant (mode == .patient): title "履歴の閲覧制限", body "30日より前の履歴はプレミアムで閲覧できます。家族がプレミアムの場合は自動で表示されます。", optional "更新" button (calls onRefresh), NO "アップグレード" or "購入を復元" buttons, (4) accessibilityIdentifier "HistoryRetentionLockView" on root container, (5) All text uses NSLocalizedString with keys from Localizable.strings
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### FeatureGate Extension

- [x] T020 [P] [US1] Add retentionDaysFree constant and historyCutoffDate() helper to FeatureGate in `ios/MedicationApp/Features/Billing/FeatureGate.swift`
  - **Why**: Client-side cutoff calculation for the retention banner (FR-008). Must match server-side calculation using Asia/Tokyo timezone (research decision 1).
  - **Files**: `ios/MedicationApp/Features/Billing/FeatureGate.swift`
  - **Done**: (1) `static let retentionDaysFree = 30`, (2) `static func historyCutoffDate() -> String` that computes todayTokyo - 29 days using `Calendar` with `TimeZone(identifier: "Asia/Tokyo")` and returns formatted YYYY-MM-DD string, (3) Uses `AppConstants.defaultTimeZone` for consistency with existing history code
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### View Wiring

- [x] T021 [US4] Add retention banner and lock overlay wiring to HistoryMonthView in `ios/MedicationApp/Features/History/HistoryMonthView.swift`
  - **Why**: Displays the retention status banner (free: range info, premium: "全期間表示中") and shows the lock overlay when ViewModel reports retention locked (FR-008, FR-009, FR-010, FR-012)
  - **Files**: `ios/MedicationApp/Features/History/HistoryMonthView.swift`
  - **Done**: (1) Accept `EntitlementStore` (injected or via init), (2) Add banner view below the month navigation header: if `entitlementStore.isPremium` → "全期間表示中"; else → "無料：直近30日まで（{FeatureGate.historyCutoffDate()}〜今日）", (3) When `viewModel.retentionLocked == true`, show `HistoryRetentionLockView` as an overlay/sheet with `mode: sessionStore.mode ?? .caregiver`, passing `onDismiss` that navigates back to current month and `onRefresh` that re-triggers the month load, (4) Existing "更新中" overlay (SchedulingRefreshOverlay) continues to block interaction during loads via `viewModel.isUpdating`
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Localization

- [x] T022 [P] [US4] Add localization strings for retention banner and lock UI to `ios/MedicationApp/Resources/Localizable.strings`
  - **Why**: Keeps all user-facing copy localized and not hardcoded (constitution III, spec UX Copy)
  - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
  - **Done**: Keys added: (1) `"history.retention.banner.free" = "無料：直近30日まで（%@〜今日）";`, (2) `"history.retention.banner.premium" = "全期間表示中";`, (3) `"history.retention.lock.caregiver.title" = "プレミアムで全期間の履歴を閲覧";`, (4) `"history.retention.lock.caregiver.body" = "30日より前の履歴はプレミアムで閲覧できます";`, (5) `"history.retention.lock.patient.title" = "履歴の閲覧制限";`, (6) `"history.retention.lock.patient.body" = "30日より前の履歴はプレミアムで閲覧できます。家族がプレミアムの場合は自動で表示されます。";`, (7) `"history.retention.lock.upgrade" = "アップグレード";`, (8) `"history.retention.lock.restore" = "購入を復元";`, (9) `"history.retention.lock.close" = "閉じる";`, (10) `"history.retention.lock.refresh" = "更新";`
  - **Test**: Build succeeds; strings referenced by HistoryRetentionLockView and HistoryMonthView

### Patient Mode Audit

- [x] T023 [US4] Verify patient mode lock UI has zero billing elements — audit against `ios/MedicationApp/Tests/Billing/PatientNoBillingUITests.swift`
  - **Why**: Patient mode must never show paywall/upgrade UI; confirms no billing code leaked into patient lock variant (FR-010, SC-004)
  - **Files**: `ios/MedicationApp/Tests/Billing/PatientNoBillingUITests.swift`, `ios/MedicationApp/Features/History/HistoryRetentionLockView.swift`
  - **Done**: (1) HistoryRetentionLockView in patient mode contains no "アップグレード" or "購入を復元" buttons, (2) No PaywallView sheet is presented from patient mode lock, (3) PatientNoBillingUITests pass with extended retention lock assertions
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Verification

- [x] T024 [US4] Verify all iOS tests pass (T004-T008) against implemented code
  - **Why**: Confirms iOS retention lock UI, banner, error parsing, and overlay behavior satisfy test expectations (SC-001, SC-002, SC-004, SC-005)
  - **Files**: `ios/MedicationApp/Tests/History/`
  - **Done**: `xcodebuild test` exits 0 with all history-retention tests passing: HistoryRetentionTests (cutoff calc, error parsing, banner), HistoryRetentionUITests (caregiver lock + paywall, patient lock no billing, premium no lock, overlay)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: Full iOS and backend implementations complete. All tests (T001-T008) pass. Free users are blocked from viewing history older than 30 days. Premium users unrestricted. Patient mode has zero billing UI. Caregiver mode shows paywall on lock.

---

## Phase 4: Docs / QA Readiness (US5)

**Purpose**: Verify and finalize design documentation against implemented behavior. Docs were pre-generated during planning; this phase confirms accuracy post-implementation.

- [x] T025 [P] [US5] Verify and finalize quickstart.md, contracts/openapi.yaml, and data-model.md against implementation in `specs/010-history-retention/`
  - **Why**: Design documents must accurately describe the implemented retention behavior, error contract, and premium resolution path (NFR-004)
  - **Files**: `specs/010-history-retention/quickstart.md`, `specs/010-history-retention/contracts/openapi.yaml`, `specs/010-history-retention/data-model.md`
  - **Done**: (1) quickstart.md sandbox test steps verified against implemented gate (free/premium, caregiver/patient, linked premium propagation), (2) openapi.yaml HISTORY_RETENTION_LIMIT response schema matches implemented HistoryRetentionError serialization (`{ code, message, cutoffDate, retentionDays }`), (3) data-model.md premium resolution paths (caregiver direct, patient via link) match implemented service functions, (4) File locations table matches actual paths
  - **Test**: `cd api && npm test` (contract tests T003 validate response shape)

- [x] T026 [P] [US5] Run full test suite validation — both backend and iOS
  - **Why**: Final green-light confirmation that all behavioral contracts hold across both platforms (SC-005)
  - **Files**: `api/tests/`, `ios/MedicationApp/Tests/`
  - **Done**: (1) `cd api && npm test` exits 0 — all history-retention + existing tests pass, (2) `xcodebuild test` exits 0 — all history-retention + existing tests pass, (3) No regressions in 004 history tests, 008 billing tests, or 009 limit tests
  - **Test**: Both commands exit 0

**Checkpoint**: All documentation finalized and verified against implementation. Full test suites green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Tests)**: No dependencies — start immediately
- **Phase 2 (Backend)**: Depends on T001 (fixture setup) being available; implementation makes T002-T003 pass
- **Phase 3 (iOS)**: Depends on Phase 2 completion (backend gate must exist for error response testing); implementation makes T004-T008 pass
- **Phase 4 (Docs)**: Depends on Phase 2 + Phase 3 completion (docs verified against implementation)

### User Story Dependencies

- **US1 (Free retention limit)**: Requires US3 backend enforcement; core gate wiring across backend + iOS
- **US2 (Premium unlimited)**: Verified by tests; no separate implementation — premium path is the absence of blocking
- **US3 (Backend enforcement)**: Independent — can be built and tested without iOS
- **US4 (iOS lock UX)**: Requires US3 backend for server error responses; builds on US1 retention state
- **US5 (Docs)**: Depends on US1 + US3 + US4 being complete

### Within Each Phase

- Tasks marked [P] can run in parallel (different files, no shared state)
- Unmarked tasks depend on prior tasks in same phase completing

### Parallel Opportunities

```text
# Phase 1: All test files can be written in parallel
T001 | T002 | T003 | T004 | T005 | T006 | T007 | T008

# Phase 2: Constant and error class in parallel, then sequential service/routes
(T009 | T010) -> T011 -> (T012 | T013) -> T014 -> T015

# Phase 3: APIError, lock view, FeatureGate, and localization in parallel, then sequential wiring
(T016 | T019 | T020 | T022) -> T017 -> T018 -> T021 -> T023 -> T024

# Phase 4: All docs in parallel
T025 | T026
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2)

1. Write all tests (Phase 1) — establish behavioral contract
2. Implement backend retention service + route wiring (Phase 2) — backend tests pass
3. **STOP and VALIDATE**: Run `cd api && npm test` — all green

### Full Feature (Phase 3 + Phase 4)

4. Implement iOS error parsing, ViewModel state, lock view, banner, localization (Phase 3) — iOS tests pass
5. **STOP and VALIDATE**: Run full iOS test suite — all green
6. Finalize docs (Phase 4)
7. **FINAL VALIDATION**: Both test suites green, quickstart walkthrough passes

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 26 |
| Phase 1 (Tests) | 8 |
| Phase 2 (Backend) | 7 |
| Phase 3 (iOS) | 9 |
| Phase 4 (Docs) | 2 |
| Parallel opportunities | 8 (Phase 1) + 2 (Phase 2) + 4 (Phase 3) + 2 (Phase 4) |
| US1 tasks | 7 |
| US2 tasks | 1 |
| US3 tasks | 8 |
| US4 tasks | 10 |
| US5 tasks | 2 |
| Non-goals excluded | Data deletion/archiving, escalation push, Pro plan, partial month data return, non-history screen changes |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Tests MUST fail before implementation and pass after
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
- Gate applies to 4 existing history endpoints from feature 004: patient month/day + caregiver month/day
- Premium resolution for patient sessions: `CaregiverPatientLink` (unique patientId, 1:1) → `caregiverId` → `CaregiverEntitlement` (status=ACTIVE)
- Existing `FeatureGate.extendedHistory`, `PaywallView`, `EntitlementStore`, and `SchedulingRefreshOverlay` from 008 are reused
- MVP straddling rule: month is locked if `firstDayOfMonth < cutoffDate` — no partial month data
- `cutoffDate = todayTokyo - 29 days` (inclusive, exactly 30 viewable days)
- All dates computed in Asia/Tokyo timezone (server and client)
