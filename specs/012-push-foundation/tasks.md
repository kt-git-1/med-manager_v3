# Tasks: Push Notification Foundation

**Input**: Design documents from `/specs/012-push-foundation/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/openapi.yaml, quickstart.md

**Tests**: Tests are REQUIRED (spec Testing Requirements mandate backend integration, iOS unit, and iOS UI smoke tests). Phase 1 is tests-first.

**Organization**: Tasks are grouped into 4 phases (Tests-first, Backend, iOS, Docs) with user story labels for traceability.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 = Caregiver enable/disable push, US2 = Patient TAKEN triggers push, US3 = Push tap deep-links to History, US4 = Backend models/security/dedup/stability, US5 = Docs & Contracts

## Path Conventions

- **Backend**: `api/` (Next.js App Router + Prisma)
- **iOS**: `ios/MedicationApp/` (SwiftUI)

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`

## Locked-in Decisions

- Transport: FCM (Firebase Cloud Messaging) via HTTP v1 REST (no firebase-admin SDK)
- Receivers: caregiver devices only (patient mode has no push settings UI)
- MVP push event: DOSE_TAKEN only
- Send timing: inline within TAKEN request (no cron/worker)
- Dedup: PushDelivery table with UNIQUE(eventKey, pushDeviceId)
  - eventKey priority: (1) `doseTaken:{recordingGroupId}` if present, (2) fallback `doseTaken:{patientId}:{YYYY-MM-DD}:{slot}:{takenAtTokyoMinute}`
- Unregister strategy: isEnabled=false (soft-disable, not delete)
- Token cleanup: disable on NotRegistered/invalid-token FCM errors
- New PushDevice table (not alter existing DeviceToken)

---

## Phase 1: Tests (Required First)

**Purpose**: Write all tests before implementation. Tests MUST fail initially and pass after their corresponding implementation phase completes.

### Backend Integration Tests — Register/Unregister

- [x] T001 [P] [US4] Create test fixtures, mock setup, and register upsert tests in `api/tests/integration/push-register.test.ts`
  - **Why**: Validates device registration correctness — upsert creates PushDevice with isEnabled=true, environment stored, lastSeenAt updated (FR-001, FR-002, SC-001)
  - **Files**: `api/tests/integration/push-register.test.ts`
  - **Done**: (1) Mock `supabaseJwt.verifySupabaseJwt` for caregiver sessions (returns `{ caregiverUserId: "caregiver-1" }`), (2) Mock `patientSessionVerifier` to reject patient tokens, (3) In-memory store for PushDevice rows, (4) Mock `prisma.pushDevice.upsert` writes to store, (5) Mock `prisma.pushDevice.updateMany` for disable, (6) Helper `caregiverHeaders()` returns `{ authorization: "Bearer caregiver-valid-jwt", "content-type": "application/json" }`, (7) Test: POST /api/push/register with valid body `{ token: "fcm-token-1", platform: "ios", environment: "DEV" }` → 200 `{ ok: true }`, store has row with isEnabled=true, (8) Test: re-register same token → 200, lastSeenAt updated (idempotent), (9) Test: register second token for same caregiver → 200, store has 2 rows
  - **Test**: `cd api && npm test`

- [x] T002 [P] [US4] Create unregister disable tests in `api/tests/integration/push-register.test.ts`
  - **Why**: Validates opt-out sets isEnabled=false without deleting the row (FR-003, SC-001)
  - **Files**: `api/tests/integration/push-register.test.ts`
  - **Done**: (1) Test: register then POST /api/push/unregister with `{ token: "fcm-token-1" }` → 200 `{ ok: true }`, store row has isEnabled=false, (2) Test: unregister non-existent token → 200 (idempotent, no error), (3) Test: re-register after unregister → 200, isEnabled back to true
  - **Test**: `cd api && npm test`

- [x] T003 [P] [US4] Create auth and validation error tests in `api/tests/integration/push-register.test.ts`
  - **Why**: Security — endpoints require caregiver auth; invalid input rejected (FR-010, NFR-002)
  - **Files**: `api/tests/integration/push-register.test.ts`
  - **Done**: (1) Test: POST /api/push/register without auth header → 401, (2) Test: POST with patient token (Bearer patient-token) → 401 or 403, (3) Test: POST /api/push/register with missing token field → 422, (4) Test: POST with invalid platform value → 422, (5) Test: POST with missing environment → 422, (6) Test: POST /api/push/unregister without auth → 401, (7) Test: POST /api/push/unregister with missing token → 422
  - **Test**: `cd api && npm test`

### Backend Integration Tests — Send Trigger + Dedup

- [x] T004 [P] [US2] Create push send trigger tests in `api/tests/integration/push-send-trigger.test.ts`
  - **Why**: Validates TAKEN recording triggers exactly one push per device with correct payload (FR-004, FR-006, SC-002)
  - **Files**: `api/tests/integration/push-send-trigger.test.ts`
  - **Done**: (1) Mock `fcmService.sendFcmMessage` as spy, (2) Mock `prisma.pushDevice.findMany` to return 2 enabled devices for linked caregiver, (3) Mock `prisma.pushDelivery.create` (insert-or-ignore), (4) Mock `prisma.caregiverPatientLink.findMany` for patient→caregiver linkage, (5) Test: call `notifyCaregiversOfDoseTaken({ patientId, displayName, date: "2026-02-11", slot: "morning", recordingGroupId: "group-uuid-1", withinTime: true, isPrn: false })` → `sendFcmMessage` called exactly 2 times (once per device), (6) Test: payload contains `{ type: "DOSE_TAKEN", patientId, date: "2026-02-11", slot: "morning", recordingGroupId: "group-uuid-1" }` in data keys, (7) Test: notification body contains displayName
  - **Test**: `cd api && npm test`

- [x] T005 [P] [US2] Create dedup tests preventing duplicate sends in `api/tests/integration/push-send-trigger.test.ts`
  - **Why**: PushDelivery dedup prevents duplicate pushes on Vercel retries (FR-005, SC-003)
  - **Files**: `api/tests/integration/push-send-trigger.test.ts`
  - **Done**: (1) Test: call `notifyCaregiversOfDoseTaken` twice with same recordingGroupId → `sendFcmMessage` called only on first call (PushDelivery unique constraint blocks second insert), (2) Test: eventKey = `doseTaken:{recordingGroupId}` when recordingGroupId present, (3) Test: no push when no linked caregiver has push enabled (empty device list), (4) Test: no push when updatedCount=0 (caller should not invoke, but verify graceful handling)
  - **Test**: `cd api && npm test`

- [x] T006 [P] [US4] Create fallback eventKey and linkage scoping tests in `api/tests/integration/push-send-trigger.test.ts`
  - **Why**: Validates fallback dedup key when recordingGroupId absent, and caregiver linkage privacy (FR-005, FR-010)
  - **Files**: `api/tests/integration/push-send-trigger.test.ts`
  - **Done**: (1) Test: call with recordingGroupId=undefined → eventKey matches fallback pattern `doseTaken:{patientId}:{date}:{slot}:{takenAtTokyoMinute}`, (2) Test: caregiver not linked to patient → no devices returned, no push sent, (3) Test: FCM returns UNREGISTERED → device disabled (isEnabled=false), `sendFcmMessage` result checked
  - **Test**: `cd api && npm test`

### iOS Unit Tests

- [x] T007 [P] [US1] Create unit tests for CaregiverPushSettingsViewModel in `ios/MedicationApp/Tests/Push/CaregiverPushSettingsTests.swift`
  - **Why**: Validates toggle ON/OFF flows call correct APIs with overlay state management (FR-001, FR-002, FR-003, FR-009, SC-001)
  - **Files**: `ios/MedicationApp/Tests/Push/CaregiverPushSettingsTests.swift`
  - **Done**: (1) Test: initial state isPushEnabled=false (default OFF), isUpdating=false, (2) Test: toggle ON → isUpdating=true, permission requested (mock grant), FCM token obtained (mock "test-token"), `registerPushDevice` called with token+platform+environment, on success isPushEnabled=true, isUpdating=false, (3) Test: toggle OFF → isUpdating=true, `unregisterPushDevice` called with token, on success isPushEnabled=false, isUpdating=false, (4) Test: permission denied → isPushEnabled stays false, error message set, (5) Test: network error during register → isPushEnabled stays false, isUpdating=false, error message set, (6) Test: persisted state restored on init (UserDefaults)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T008 [P] [US3] Create unit tests for push deep link routing in `ios/MedicationApp/Tests/Push/PushDeepLinkTests.swift`
  - **Why**: Validates remote push payload parsing produces correct navigation target (FR-006, FR-007, SC-004)
  - **Files**: `ios/MedicationApp/Tests/Push/PushDeepLinkTests.swift`
  - **Done**: (1) Test: `routeFromRemotePush(userInfo: ["type": "DOSE_TAKEN", "date": "2026-02-11", "slot": "morning"])` → target = NotificationDeepLinkTarget(dateKey: "2026-02-11", slot: .morning), (2) Test: userInfo with slot "bedtime" → target.slot = .bedtime, (3) Test: userInfo with unknown type "OTHER" → target is nil, (4) Test: userInfo missing "date" key → target is nil, (5) Test: userInfo missing "slot" key → target is nil, (6) Test: existing local notification parsing (`notif:2026-02-11:morning:1`) still works (backward compat)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### iOS UI Smoke Tests

- [x] T009 [P] [US1] Create UI smoke test for caregiver push toggle and overlay in `ios/MedicationApp/Tests/Push/CaregiverPushUITests.swift`
  - **Why**: Validates end-to-end toggle flow with full-screen overlay blocking interaction (FR-009, SC-001)
  - **Files**: `ios/MedicationApp/Tests/Push/CaregiverPushUITests.swift`
  - **Done**: (1) Test: caregiver Settings tab shows "見守りPush通知" section with toggle OFF, (2) Test: toggle ON → "更新中" overlay appears (accessibilityIdentifier "SchedulingRefreshOverlay"), (3) Test: overlay blocks taps on underlying content, (4) Test: after register completes, overlay dismisses, toggle shows ON, (5) Test: toggle OFF → overlay appears → dismisses → toggle shows OFF
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T010 [P] [US3] Create UI smoke test for push tap deep link to History in `ios/MedicationApp/Tests/Push/CaregiverPushUITests.swift`
  - **Why**: Validates push tap navigates to correct History date/slot with highlight (FR-007, SC-004)
  - **Files**: `ios/MedicationApp/Tests/Push/CaregiverPushUITests.swift`
  - **Done**: (1) Test: inject simulated push payload (DOSE_TAKEN, date, slot) → app switches to History tab, (2) Test: day detail opens for specified date, (3) Test: slot section highlighted (pulse/glow animation visible or highlight emitted)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T011 [P] [US1] Create UI smoke test verifying patient mode has no push settings in `ios/MedicationApp/Tests/Push/CaregiverPushUITests.swift`
  - **Why**: Product policy — patient mode must not show push settings UI (spec Assumptions, FR-012)
  - **Files**: `ios/MedicationApp/Tests/Push/CaregiverPushUITests.swift`
  - **Done**: (1) Test: in patient mode, no Settings tab visible in tab bar, (2) Test: no push toggle accessible in patient views
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: All test files exist and define expected behavior. Tests fail because backend push endpoints, iOS push settings UI, and deep link handling do not exist yet.

---

## Phase 2: Backend — Models + APIs + FCM Sender + Dedup + Trigger (US2 + US4)

**Purpose**: Add PushDevice/PushDelivery models, register/unregister routes, FCM sender, dedup dispatch, and wire into TAKEN flows. After this phase, backend tests (T001-T006) must pass.

### Locate Entry Points

- [x] T012 [US4] Locate existing auth middleware (`requireCaregiver`), caregiver-patient linkage query, and TAKEN trigger sites in `api/src/`
  - **Why**: Confirms exact import paths and function signatures before coding to avoid wrong scoping or broken imports
  - **Files**: `api/src/middleware/auth.ts`, `api/src/services/pushNotificationService.ts`, `api/src/services/doseRecordService.ts`, `api/src/services/slotBulkRecordService.ts`, `api/src/services/prnDoseRecordService.ts`
  - **Done**: (1) `requireCaregiver(authHeader)` from `api/src/middleware/auth.ts` returns `{ role, caregiverUserId }`, (2) `getLinkedCaregiverIds(patientId)` (private in pushNotificationService.ts) uses `prisma.caregiverPatientLink.findMany`, (3) `notifyCaregiversOfDoseRecord` called at: `doseRecordService.ts` line 55, `slotBulkRecordService.ts` line 198 (in loop), `prnDoseRecordService.ts` line 61, (4) Existing `errorResponse` from `api/src/middleware/error.ts`
  - **Test**: N/A (read-only investigation)

### Migration & Models

- [x] T013 [US4] Add PushDevice and PushDelivery models to Prisma schema and run migration in `api/prisma/schema.prisma`
  - **Why**: Persistence for device registry and dedup (data-model.md, FR-001, FR-005)
  - **Files**: `api/prisma/schema.prisma`, `api/prisma/migrations/*`
  - **Done**: (1) Add `PushDevice` model: id, ownerType, ownerId, token, platform (@default("ios")), environment, isEnabled (@default(true)), lastSeenAt (@default(now())), createdAt, updatedAt; @@unique([ownerType, ownerId, token]), @@index([ownerId]), (2) Add `PushDelivery` model: id, eventKey, pushDeviceId (FK → PushDevice.id), createdAt; @@unique([eventKey, pushDeviceId]), @@index([pushDeviceId]); relation to PushDevice, (3) Run `npx prisma migrate dev --name push_device_and_delivery`, (4) Verify migration applies cleanly and existing tests still pass
  - **Test**: `cd api && npm test`

### Repositories

- [x] T014 [P] [US4] Implement PushDevice repository in `api/src/repositories/pushDeviceRepo.ts`
  - **Why**: CRUD operations for device registration/disable/query (FR-001, FR-002, FR-003, FR-008)
  - **Files**: `api/src/repositories/pushDeviceRepo.ts`
  - **Done**: (1) `upsertPushDevice({ ownerType, ownerId, token, platform, environment })` — upsert by @@unique, set isEnabled=true, lastSeenAt=now on both create and update, (2) `disablePushDevice({ ownerType, ownerId, token })` — updateMany where match, set isEnabled=false, (3) `listEnabledPushDevicesForCaregivers(caregiverIds: string[])` — findMany where ownerId in caregiverIds AND ownerType="CAREGIVER" AND isEnabled=true, (4) `disablePushDeviceById(id: string)` — update by id, set isEnabled=false
  - **Test**: `cd api && npm test`

- [x] T015 [P] [US4] Implement PushDelivery repository in `api/src/repositories/pushDeliveryRepo.ts`
  - **Why**: Dedup insert-or-ignore for exactly-once push per device per event (FR-005, SC-003)
  - **Files**: `api/src/repositories/pushDeliveryRepo.ts`
  - **Done**: (1) `tryInsertDelivery({ eventKey, pushDeviceId }): Promise<boolean>` — attempts insert; returns true if inserted (new delivery), false if duplicate (unique constraint violation caught), (2) Uses try/catch on Prisma `create` catching P2002 unique constraint error, or `createMany` with `skipDuplicates: true` and checks count
  - **Test**: `cd api && npm test`

### Validator

- [x] T016 [P] [US4] Implement push register/unregister validator in `api/src/validators/pushRegister.ts`
  - **Why**: Input validation for both endpoints — token required, platform enum, environment enum (contracts/openapi.yaml)
  - **Files**: `api/src/validators/pushRegister.ts`
  - **Done**: (1) `validateRegisterRequest(body)` — validates: token (non-empty string), platform ("ios"), environment ("DEV"|"PROD"); returns `{ errors: string[], token?, platform?, environment? }`, (2) `validateUnregisterRequest(body)` — validates: token (non-empty string); returns `{ errors: string[], token? }`, (3) Error messages are descriptive: "token is required", "platform must be ios", "environment must be DEV or PROD"
  - **Test**: `cd api && npm test`

### API Routes

- [x] T017 [US4] Implement POST /api/push/register route in `api/app/api/push/register/route.ts`
  - **Why**: Device registration endpoint for caregiver toggle ON (FR-001, FR-002, contracts/openapi.yaml)
  - **Files**: `api/app/api/push/register/route.ts`
  - **Done**: (1) `export const runtime = "nodejs"`, (2) POST handler: extract auth header, `requireCaregiver(authHeader)` → get `caregiverUserId`, (3) Parse JSON body, call `validateRegisterRequest(body)` → return 422 on errors, (4) Call `upsertPushDevice({ ownerType: "CAREGIVER", ownerId: caregiverUserId, token, platform, environment })`, (5) Return 200 `{ ok: true }`, (6) Catch errors → `errorResponse(error)`
  - **Test**: `cd api && npm test`

- [x] T018 [US4] Implement POST /api/push/unregister route in `api/app/api/push/unregister/route.ts`
  - **Why**: Device disable endpoint for caregiver toggle OFF (FR-003, contracts/openapi.yaml)
  - **Files**: `api/app/api/push/unregister/route.ts`
  - **Done**: (1) POST handler: `requireCaregiver` → `caregiverUserId`, (2) Parse body, `validateUnregisterRequest(body)` → 422 on errors, (3) Call `disablePushDevice({ ownerType: "CAREGIVER", ownerId: caregiverUserId, token })`, (4) Return 200 `{ ok: true }` (idempotent — no error if token not found), (5) Catch → `errorResponse`
  - **Test**: `cd api && npm test`

### FCM Sender

- [x] T019 [US4] Implement FCM HTTP v1 sender service in `api/src/services/fcmService.ts`
  - **Why**: Push delivery transport using FCM REST API with Google service account JWT (research.md Decision 1, plan Key Implementation: fcmService.ts)
  - **Files**: `api/src/services/fcmService.ts`
  - **Done**: (1) Load service account from `FCM_SERVICE_ACCOUNT_JSON` env var (base64 → JSON), extract `client_email`, `private_key`, `project_id`, (2) Generate Google OAuth2 access token via RS256 JWT with scope `https://www.googleapis.com/auth/firebase.messaging`, iat/exp, (3) Cache access token for 50 minutes (same pattern as `apnsService.ts` line 47-57), (4) `sendFcmMessage(token, notification, data, apnsOverride?)` — POST to `https://fcm.googleapis.com/v1/projects/{projectId}/messages:send` with `{ message: { token, notification, data, apns: apnsOverride } }`, (5) Parse response: 200 → `{ success: true }`, error with UNREGISTERED or NOT_FOUND → `{ success: false, errorCode: "UNREGISTERED" }`, other → `{ success: false, errorCode: "UNKNOWN" }` with warning log, (6) `isFcmConfigured()` — returns true if env var present, (7) Export types `FcmSendResult`, `FcmNotification`, `FcmDataPayload`
  - **Test**: `cd api && npm test`

- [x] T020 [US4] Add NotRegistered/invalid-token handling to disable PushDevice on send errors in `api/src/services/fcmService.ts`
  - **Why**: Stale tokens must be silently disabled per FR-008 to prevent repeated failures
  - **Files**: `api/src/services/fcmService.ts`, `api/src/repositories/pushDeviceRepo.ts`
  - **Done**: (1) `sendFcmMessage` returns `errorCode: "UNREGISTERED"` on 404/UNREGISTERED/NOT_FOUND responses, (2) Caller (pushDispatchService / pushNotificationService) checks errorCode and calls `disablePushDeviceById(deviceId)` when UNREGISTERED, (3) Logged as warning (not error) — fire-and-forget, no throw
  - **Test**: `cd api && npm test`

### Push Dispatch + Dedup

- [x] T021 [US2] Implement notifyCaregiversOfDoseTaken with dedup in `api/src/services/pushNotificationService.ts`
  - **Why**: Core push orchestration — resolve devices, build eventKey, dedup via PushDelivery, send via FCM, handle errors (FR-004, FR-005, FR-006, FR-010, FR-011, SC-002, SC-003)
  - **Files**: `api/src/services/pushNotificationService.ts`
  - **Done**: (1) New export `notifyCaregiversOfDoseTaken(input: { patientId, displayName, date, slot, recordingGroupId?, withinTime, isPrn })`, (2) Resolve linked caregiver IDs via existing `getLinkedCaregiverIds(patientId)`, (3) Fetch enabled PushDevices via `listEnabledPushDevicesForCaregivers(caregiverIds)`, (4) Build eventKey: if `recordingGroupId` present → `doseTaken:{recordingGroupId}`, else → `doseTaken:{patientId}:{date}:{slot}:{takenAtTokyoMinute}` where takenAtTokyoMinute = current time in Tokyo rounded to minute (HH:mm), (5) For each device: call `tryInsertDelivery({ eventKey, pushDeviceId: device.id })`, (6) If inserted (true): build FCM payload with `notification: { title: "服薬記録", body: "{displayName}さんが薬を服用しました" }` (or "時間内に" variant if withinTime=true), `data: { type: "DOSE_TAKEN", patientId, date, slot, recordingGroupId: recordingGroupId ?? "" }`, `apns: { payload: { aps: { sound: "default", "thread-id": "patient-{patientId}" } } }`; call `sendFcmMessage(device.token, notification, data, apns)`, (7) If UNREGISTERED response: `disablePushDeviceById(device.id)`, (8) If duplicate (false): skip (dedup), (9) Entire function wrapped in try/catch — errors logged, never thrown (fire-and-forget), (10) Existing `notifyCaregiversOfDoseRecord` remains unchanged for backward compat
  - **Test**: `cd api && npm test`

### Wire Triggers

- [x] T022 [US2] Wire push trigger into slotBulkRecordService.ts — single push per bulk in `api/src/services/slotBulkRecordService.ts`
  - **Why**: Replace per-record push loop with single call per group (FR-004, research Decision 5)
  - **Files**: `api/src/services/slotBulkRecordService.ts`
  - **Done**: (1) Import `notifyCaregiversOfDoseTaken` from `pushNotificationService.ts`, (2) After the side effects loop (after line ~210), add single call: `void notifyCaregiversOfDoseTaken({ patientId: input.patientId, displayName: patient.displayName, date: input.date, slot: input.slot, recordingGroupId, withinTime: records.some(r => r.takenAt.getTime() <= r.scheduledAt.getTime() + DOSE_MISSED_WINDOW_MS), isPrn: false })`, (3) Remove individual `notifyCaregiversOfDoseRecord` calls from inside the per-record loop (lines ~198-204), (4) Keep `notifyCaregiversOfDoseRecord` import for any non-TAKEN push paths if still needed, or remove if unused
  - **Test**: `cd api && npm test`

- [x] T023 [US2] Wire push trigger into doseRecordService.ts and prnDoseRecordService.ts in `api/src/services/doseRecordService.ts`, `api/src/services/prnDoseRecordService.ts`
  - **Why**: Single-dose and PRN TAKEN recordings also trigger push (FR-004)
  - **Files**: `api/src/services/doseRecordService.ts`, `api/src/services/prnDoseRecordService.ts`
  - **Done**: (1) In `doseRecordService.ts` `createDoseRecordIdempotent` (line ~55): replace `notifyCaregiversOfDoseRecord(...)` with `notifyCaregiversOfDoseTaken({ patientId, displayName, date: <scheduledAt formatted YYYY-MM-DD Tokyo>, slot: <resolveSlot(scheduledAt, "Asia/Tokyo")>, recordingGroupId: undefined, withinTime, isPrn: medication?.isPrn ?? false })`, (2) In `prnDoseRecordService.ts` `createPrnRecord` (line ~61): replace `notifyCaregiversOfDoseRecord(...)` with `notifyCaregiversOfDoseTaken({ patientId, displayName, date: <takenAt YYYY-MM-DD Tokyo>, slot: <resolveSlot(takenAt, "Asia/Tokyo")>, recordingGroupId: undefined, withinTime: true, isPrn: true })`, (3) Import `resolveSlot` from `scheduleResponse.ts` and `notifyCaregiversOfDoseTaken` from `pushNotificationService.ts`, (4) Import date formatting helper (use existing `getLocalDateKey` from `scheduleService.ts` or Intl.DateTimeFormat)
  - **Test**: `cd api && npm test`

### Verification

- [x] T024 [US4] Verify all backend tests pass (T001-T006) against implemented code
  - **Why**: Confirms register/unregister, send trigger, dedup, auth, linkage, and NotRegistered handling (SC-005)
  - **Files**: `api/tests/integration/push-register.test.ts`, `api/tests/integration/push-send-trigger.test.ts`
  - **Done**: `cd api && npm test` exits 0 with all push-register and push-send-trigger integration tests passing
  - **Test**: `cd api && npm test`

**Checkpoint**: Backend push infrastructure complete. Register/unregister endpoints working. FCM sender implemented. Dedup via PushDelivery enforced. Trigger wired into all TAKEN flows. NotRegistered errors disable tokens. Backend tests pass.

---

## Phase 3: iOS — FCM Token + Settings Toggle + Deep Link (US1 + US3)

**Purpose**: Add Firebase SDK, caregiver Settings tab with push toggle, deep link handling for push tap to History. After this phase, iOS tests (T007-T011) must pass.

### Firebase Setup

- [X] T025 [US1] Add Firebase SDK (SPM) and configure in AppDelegate in `ios/MedicationApp/App/AppDelegate.swift`
  - **Why**: FirebaseMessaging SDK provides FCM token generation and refresh lifecycle (research Decision 6)
  - **Files**: `ios/MedicationApp/App/AppDelegate.swift`, Xcode project (Package.resolved)
  - **Done**: (1) Add FirebaseMessaging SPM package (URL: `https://github.com/firebase/firebase-ios-sdk`, product: FirebaseMessaging), (2) In `application(_:didFinishLaunchingWithOptions:)`: call `FirebaseApp.configure()`, (3) Conform AppDelegate to `MessagingDelegate`, set `Messaging.messaging().delegate = self`, (4) In `didRegisterForRemoteNotificationsWithDeviceToken`: forward APNs token to FCM via `Messaging.messaging().apnsToken = deviceToken`, (5) Add `GoogleService-Info.plist` placeholder (or document in quickstart.md for dev setup)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [X] T026 [US1] Modify DeviceTokenManager for FCM tokens in `ios/MedicationApp/Services/DeviceTokenManager.swift`
  - **Why**: Switch from APNs hex tokens to FCM registration tokens (research Decision 6)
  - **Files**: `ios/MedicationApp/Services/DeviceTokenManager.swift`
  - **Done**: (1) Replace APNs hex-encoding with FCM token retrieval: `Messaging.messaging().token` async, (2) New `handleFCMToken(_ token: String)` — stores in UserDefaults (`fcm.deviceToken`), (3) `registerWithBackend(apiClient:)` sends FCM token to `/api/push/register` with platform="ios" and environment (DEBUG → "DEV", release → "PROD"), (4) `unregisterFromBackend(apiClient:)` sends to `/api/push/unregister`, (5) `MessagingDelegate.messaging(_:didReceiveRegistrationToken:)` calls `handleFCMToken` for auto-refresh, (6) Retain backward compat for APNs token forwarding (needed by FCM SDK internally)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Networking

- [X] T027 [P] [US1] Add registerPushDevice and unregisterPushDevice to APIClient in `ios/MedicationApp/Networking/APIClient.swift`
  - **Why**: New API methods for push register/unregister endpoints (contracts/openapi.yaml)
  - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
  - **Done**: (1) `func registerPushDevice(token: String, platform: String, environment: String) async throws` — POST to `/api/push/register` with JSON body, caregiver session auth, (2) `func unregisterPushDevice(token: String) async throws` — POST to `/api/push/unregister` with JSON body, caregiver session auth, (3) Both decode `{ ok: true }` response, throw on non-200 using existing error mapping
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Settings UI

- [X] T028 [US1] Add CaregiverTab.settings and CaregiverSettingsView in `ios/MedicationApp/Features/PatientManagement/CaregiverHomeView.swift`, `ios/MedicationApp/Features/Settings/CaregiverSettingsView.swift`
  - **Why**: Caregiver needs a Settings tab with push toggle (FR-001, research Decision 7)
  - **Files**: `ios/MedicationApp/Features/PatientManagement/CaregiverHomeView.swift`, `ios/MedicationApp/Features/Settings/CaregiverSettingsView.swift`
  - **Done**: (1) Add `.settings` case to `CaregiverTab` enum, (2) Add settings case to CaregiverHomeView body switch with `CaregiverSettingsView` wrapped in NavigationStack, (3) Add settings icon/label to `CaregiverBottomTabBar` (SF Symbol: "gearshape.fill", label: localized "設定"), (4) Create `CaregiverSettingsView` with Form containing "見守りPush通知" Section and Toggle bound to viewModel, (5) Show `SchedulingRefreshOverlay` when `viewModel.isUpdating` is true, (6) Error alert for permission denied / network errors
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [X] T029 [US1] Implement CaregiverPushSettingsViewModel in `ios/MedicationApp/Features/Settings/CaregiverPushSettingsViewModel.swift`
  - **Why**: Encapsulates toggle state, permission flow, token acquisition, and API calls with overlay (FR-001, FR-002, FR-003, FR-009)
  - **Files**: `ios/MedicationApp/Features/Settings/CaregiverPushSettingsViewModel.swift`
  - **Done**: (1) `@Published var isPushEnabled: Bool` (default false, persisted in UserDefaults), (2) `@Published var isUpdating: Bool` (default false), (3) `@Published var errorMessage: String?`, (4) Toggle ON flow: set isUpdating=true → request `UNUserNotificationCenter.requestAuthorization(options: [.alert, .sound])` → if denied, set errorMessage and isUpdating=false → get FCM token via `Messaging.messaging().token` → call `apiClient.registerPushDevice(token:platform:environment:)` → set isPushEnabled=true, isUpdating=false → persist, (5) Toggle OFF flow: set isUpdating=true → call `apiClient.unregisterPushDevice(token:)` → set isPushEnabled=false, isUpdating=false → persist, (6) Error handling: catch errors, set errorMessage, reset isUpdating=false, (7) Init: restore isPushEnabled from UserDefaults
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Deep Link

- [X] T030 [US3] Extend NotificationDeepLinkRouter for remote push payload parsing in `ios/MedicationApp/Features/Notifications/NotificationDeepLinkRouter.swift`
  - **Why**: Remote push payloads use userInfo dict, not notification identifier format (research Decision 8, FR-006, FR-007)
  - **Files**: `ios/MedicationApp/Features/Notifications/NotificationDeepLinkRouter.swift`
  - **Done**: (1) Add `func routeFromRemotePush(userInfo: [AnyHashable: Any])` to `NotificationDeepLinkRouter`, (2) Parse `type` key — must be "DOSE_TAKEN" (else return/ignore), (3) Parse `date` key → dateKey string (YYYY-MM-DD), (4) Parse `slot` key → `NotificationSlot(rawValue:)`, (5) If all valid: set `target = NotificationDeepLinkTarget(dateKey:, slot:)`, (6) Existing `parse(identifier:)` for local notifications remains unchanged
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [X] T031 [US3] Extend NotificationCoordinator to route remote push taps in `ios/MedicationApp/Features/Notifications/NotificationDeepLinkRouter.swift`
  - **Why**: `didReceive(response:)` must distinguish remote push from local notification (FR-007)
  - **Files**: `ios/MedicationApp/Features/Notifications/NotificationDeepLinkRouter.swift`
  - **Done**: (1) In `userNotificationCenter(_:didReceive:)`: extract `userInfo` from response, (2) If userInfo contains `"type"` key: call `router.routeFromRemotePush(userInfo:)`, (3) Else: fall through to existing `router.route(identifier:)` for local notifications, (4) In `willPresent` (foreground): if remote push with `"type"` key, call `bannerPresenter.handleRemotePush(userInfo:)` (or adapt existing banner mechanism)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [X] T032 [US3] CaregiverHomeView: consume deep link target and switch to History tab in `ios/MedicationApp/Features/PatientManagement/CaregiverHomeView.swift`
  - **Why**: Caregiver must navigate to History on push tap (FR-007, SC-004)
  - **Files**: `ios/MedicationApp/Features/PatientManagement/CaregiverHomeView.swift`
  - **Done**: (1) Add `@EnvironmentObject var notificationRouter: NotificationDeepLinkRouter`, (2) Add `@State private var deepLinkTarget: NotificationDeepLinkTarget?`, (3) Add `.onReceive(notificationRouter.$target)` modifier: if target is non-nil and sessionStore.mode is caregiver, set `selectedTab = .history`, set `deepLinkTarget = target`, call `notificationRouter.clear()`, (4) Pass `deepLinkTarget` binding to `CaregiverHistoryView` / `HistoryMonthView`
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [X] T033 [US3] HistoryMonthView: deep link scroll to date/slot with highlight in `ios/MedicationApp/Features/History/HistoryMonthView.swift`
  - **Why**: Push tap must open date detail, scroll to slot, and highlight (FR-007, SC-004)
  - **Files**: `ios/MedicationApp/Features/History/CaregiverHistoryView.swift`, `ios/MedicationApp/Features/History/HistoryMonthView.swift`
  - **Done**: (1) Add `deepLinkTarget: Binding<NotificationDeepLinkTarget?>` parameter to HistoryMonthView (or CaregiverHistoryView), (2) On non-nil target: call `viewModel.selectDate(dateKey)` to open day detail, (3) After day detail loads: scroll to slot section using slot rawValue as ScrollView anchor ID, (4) Apply highlight animation — reuse `TodaySlotHighlight`-style pulse/glow (opacity animation for ~3 seconds) from `PatientTodayView.swift`, (5) Clear deepLinkTarget after handling, (6) Works for both background (app was running) and cold start (target set before view appears)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Localization

- [X] T034 [P] [US1] Add push-related localization strings to `ios/MedicationApp/Resources/Localizable.strings`
  - **Why**: All user-facing text must be localized (NFR-004)
  - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
  - **Done**: Keys added: (1) `"caregiver.tabs.settings" = "設定";`, (2) `"caregiver.settings.push.section.title" = "見守りPush通知";`, (3) `"caregiver.settings.push.toggle" = "Push通知を有効にする";`, (4) `"caregiver.settings.push.permission.denied" = "通知が許可されていません。設定アプリから通知を有効にしてください。";`, (5) `"caregiver.settings.push.error" = "エラーが発生しました。もう一度お試しください。";`, (6) `"caregiver.settings.push.enabled" = "Push通知が有効です";`, (7) `"caregiver.settings.push.disabled" = "Push通知が無効です";`
  - **Test**: Build succeeds; strings referenced by CaregiverSettingsView and ViewModel

### Patient Mode Guard

- [X] T035 [US1] Ensure patient mode hides push settings and cannot register (defensive) in `ios/MedicationApp/Features/PatientManagement/CaregiverHomeView.swift`
  - **Why**: Product policy — patient mode must not show push settings UI (spec Assumptions)
  - **Files**: `ios/MedicationApp/Features/PatientManagement/CaregiverHomeView.swift`, `ios/MedicationApp/Features/Settings/CaregiverPushSettingsViewModel.swift`
  - **Done**: (1) Settings tab only appears in CaregiverHomeView (patient mode uses PatientReadOnlyView which has no settings tab), (2) CaregiverPushSettingsViewModel guards `sessionStore.mode == .caregiver` before register/unregister calls (no-op if patient), (3) Verify no push toggle is accessible from any patient mode view
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Verification

- [X] T036 [US1] Verify all iOS tests pass (T007-T011) against implemented code
  - **Why**: Confirms push settings, deep link routing, overlay, and patient mode guard (SC-005)
  - **Files**: `ios/MedicationApp/Tests/Push/`
  - **Done**: `xcodebuild test` exits 0 with all Push tests passing: CaregiverPushSettingsTests, PushDeepLinkTests, CaregiverPushUITests
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: Full iOS push implementation complete. Caregiver Settings tab with push toggle. FCM token lifecycle. Deep link from push to History with highlight. Patient mode has no push UI. All iOS tests pass.

---

## Phase 4: Docs / QA Readiness (US5)

**Purpose**: Verify design documentation against implemented behavior and update spec artifacts.

- [x] T037 [P] [US5] Verify and update quickstart.md against implementation in `specs/012-push-foundation/quickstart.md`
  - **Why**: quickstart.md must accurately describe FCM setup, test flow, and deep link testing (constitution VI)
  - **Files**: `specs/012-push-foundation/quickstart.md`
  - **Done**: (1) FCM service account setup steps match implemented `fcmService.ts` env var names, (2) Register/unregister curl examples match implemented routes, (3) Deep link test payload matches implemented FCM data keys, (4) Simulator testing section is accurate
  - **Test**: N/A (documentation review)

- [x] T038 [P] [US5] Verify and update contracts/openapi.yaml against implementation in `specs/012-push-foundation/contracts/openapi.yaml`
  - **Why**: API contracts must match implemented endpoints (constitution VI)
  - **Files**: `specs/012-push-foundation/contracts/openapi.yaml`
  - **Done**: (1) Request/response schemas match route handlers, (2) Error codes (401, 422) match validator behavior, (3) FcmDoseTakenPayload matches implemented push payload
  - **Test**: N/A (documentation review)

- [x] T039 [P] [US5] Verify and update data-model.md against migration in `specs/012-push-foundation/data-model.md`
  - **Why**: Data model docs must match Prisma schema (constitution VI)
  - **Files**: `specs/012-push-foundation/data-model.md`
  - **Done**: (1) PushDevice fields match schema.prisma, (2) PushDelivery fields and constraints match, (3) Migration SQL matches generated migration
  - **Test**: N/A (documentation review)

- [x] T040 [P] Run full test suite validation — both backend and iOS
  - **Why**: Final confirmation that all tests pass with no regressions (SC-005)
  - **Files**: `api/tests/`, `ios/MedicationApp/Tests/`
  - **Done**: (1) `cd api && npm test` exits 0 — all push + existing tests pass, (2) `xcodebuild test` exits 0 — all Push + existing tests pass, (3) No regressions in 003, 004, 005, 006, 007, 008, 0115 tests
  - **Test**: Both commands exit 0

**Checkpoint**: All documentation finalized and verified against implementation. Full test suites green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Tests)**: No dependencies — start immediately
- **Phase 2 (Backend)**: Depends on T001-T006 fixtures existing; implementation makes T001-T006 pass
- **Phase 3 (iOS)**: Depends on Phase 2 completion (backend endpoints must exist for integration); implementation makes T007-T011 pass
- **Phase 4 (Docs)**: Depends on Phase 2 + Phase 3 completion (docs verified against implementation)

### User Story Dependencies

- **US1 (Enable/disable push)**: Backend register/unregister + iOS Settings UI. Independent of US2/US3.
- **US2 (TAKEN triggers push)**: Backend dispatch + dedup + trigger wiring. Requires US4 (models). Independent of US1 for backend but needs US1 device to exist.
- **US3 (Deep link to History)**: iOS deep link routing + History scroll/highlight. Independent of US2 but needs push payload format from US2.
- **US4 (Backend models/security)**: Foundation for US1 and US2. Must complete first.
- **US5 (Docs)**: Depends on all other stories being implemented.

### Within Each Phase

- Tasks marked [P] can run in parallel (different files, no shared state)
- Unmarked tasks depend on prior tasks in same phase completing

### Parallel Opportunities

```text
# Phase 1: All test files can be written in parallel
(T001 | T002 | T003 | T004 | T005 | T006) — backend tests
(T007 | T008) — iOS unit tests
(T009 | T010 | T011) — iOS UI smoke tests

# Phase 2: Locate first, then migration, then repos+validator in parallel, then routes, then service, then triggers
T012 -> T013 -> (T014 | T015 | T016) -> (T017 | T018) -> T019 -> T020 -> T021 -> (T022 | T023) -> T024

# Phase 3: Firebase setup first, then token manager, then parallel networking+localization, then settings, then deep link, then guards
T025 -> T026 -> (T027 | T034) -> T028 -> T029 -> T030 -> T031 -> T032 -> T033 -> T035 -> T036

# Phase 4: All docs in parallel
(T037 | T038 | T039 | T040)
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2 Backend)

1. Write all tests (Phase 1) — establish behavioral contract
2. Implement backend: models, repos, routes, FCM sender, dispatch, trigger wiring (Phase 2)
3. **STOP and VALIDATE**: Run `cd api && npm test` — all green

### Full Feature (Phase 3 + Phase 4)

4. Implement iOS: Firebase, token manager, Settings UI, deep link, localization (Phase 3)
5. **STOP and VALIDATE**: Run full iOS test suite — all green
6. Finalize docs (Phase 4)
7. **FINAL VALIDATION**: Both test suites green, quickstart walkthrough passes

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 40 |
| Phase 1 (Tests) | 11 |
| Phase 2 (Backend) | 13 |
| Phase 3 (iOS) | 12 |
| Phase 4 (Docs) | 4 |
| Parallel opportunities | 11 (Phase 1) + 3 (Phase 2) + 2 (Phase 3) + 4 (Phase 4) |
| US1 tasks | 12 |
| US2 tasks | 5 |
| US3 tasks | 7 |
| US4 tasks | 14 |
| US5 tasks | 4 |
| Non-goals excluded | Escalation push, inventory push, quiet hours, frequency settings, patient push UI, cron/worker |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Tests MUST fail before implementation and pass after
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
- Backend reuses: `requireCaregiver` from auth.ts, `getLinkedCaregiverIds` from pushNotificationService.ts, `CaregiverPatientLink`, `errorResponse`, `resolveSlot` from scheduleResponse.ts
- iOS reuses: `SchedulingRefreshOverlay`, `NotificationDeepLinkTarget`, `NotificationSlot`, `ReminderBannerPresenter`, `FullScreenContainer`
- All dates in Asia/Tokyo timezone (server and client)
- eventKey priority: recordingGroupId first, then fallback with patientId+date+slot+takenAtTokyoMinute
- FCM HTTP v1 REST (not firebase-admin SDK) to keep Vercel bundle small
- New PushDevice table separate from legacy DeviceToken (APNs)
- PushDelivery dedup is database-level UNIQUE constraint (reliable across serverless retries)
