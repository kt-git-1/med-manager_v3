# Implementation Plan: Push Notification Foundation (012)

**Branch**: `012-push-foundation` | **Date**: 2026-02-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/012-push-foundation/spec.md`

## Summary

FCM-based push notification foundation for caregiver mode. Caregivers register devices via a Settings toggle (FCM token → POST /api/push/register). When a patient records TAKEN (single, bulk, or PRN), the system sends exactly one push notification per linked caregiver device with dedup via a PushDelivery table. Push tap deep-links to caregiver History (date detail → slot scroll + highlight). No cron/worker required; push is sent inline as a fire-and-forget side effect. TAKEN push is free (no billing gate).

## Technical Context

**Language/Version**: TypeScript 5.9 (Node >=20) for API; Swift 6.2 for iOS
**Primary Dependencies**: Next.js 16 App Router, Prisma 7.3, Vitest; SwiftUI, FirebaseMessaging, XCTest
**Storage**: PostgreSQL via Prisma (new PushDevice + PushDelivery tables)
**Testing**: Vitest (backend integration), XCTest (iOS unit + UI smoke)
**Target Platform**: Vercel (free plan) + iOS 26+
**Project Type**: Mobile + API
**Performance Goals**: Push delivery to FCM within 15s of TAKEN; register/unregister < 5s end-to-end
**Constraints**: No cron/worker; fire-and-forget inline send; Vercel function size limits; no PHI in push payload
**Scale/Scope**: MVP: 1 push event type (DOSE_TAKEN), caregiver devices only, iOS only

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: spec is the single source of truth for behavior. **Pass**
- Traceability: every change maps to spec + acceptance criteria + tests. **Pass**
- Test strategy: tests-first in Phase 1; Vitest for backend; XCTest for iOS; no external calls in CI (FCM mocked). **Pass**
- Security & privacy: caregiver auth via `requireCaregiver`; deny-by-default; no PHI in push payloads; no PII in logs. **Pass**
- Performance guardrails: FCM HTTP v1 REST call is non-blocking (fire-and-forget); PushDelivery dedup uses UNIQUE constraint insert-or-ignore. **Pass**
- UX/accessibility: reuses full-screen overlay pattern; VoiceOver labels on push toggle; localized strings. **Pass**
- Documentation: research, data model, contracts, quickstart updated in same branch. **Pass**

Post-Phase 1 re-check: **Pass** (research, data model, contracts, and quickstart aligned with spec).

## Project Structure

### Documentation (this feature)

```text
specs/012-push-foundation/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── openapi.yaml     # Phase 1 output — push register/unregister endpoints
├── checklists/
│   └── requirements.md  # Spec quality checklist (existing)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
api/
├── prisma/
│   └── schema.prisma                                    # MODIFY: add PushDevice + PushDelivery models
├── app/api/push/
│   ├── register/
│   │   └── route.ts                                     # NEW: POST /api/push/register
│   └── unregister/
│       └── route.ts                                     # NEW: POST /api/push/unregister
├── src/
│   ├── services/
│   │   ├── fcmService.ts                                # NEW: FCM HTTP v1 sender
│   │   └── pushNotificationService.ts                   # MODIFY: add dedup + FCM + deep link payload
│   ├── repositories/
│   │   ├── pushDeviceRepo.ts                            # NEW: PushDevice CRUD
│   │   └── pushDeliveryRepo.ts                          # NEW: PushDelivery dedup
│   └── validators/
│       └── pushRegister.ts                              # NEW: register/unregister validation
└── tests/integration/
    ├── push-register.test.ts                            # NEW: register/unregister tests
    └── push-send-trigger.test.ts                        # NEW: send trigger + dedup tests

ios/MedicationApp/
├── App/
│   ├── AppDelegate.swift                                # MODIFY: Firebase setup + FCM token
│   └── MedicationApp.swift                              # MODIFY: wire caregiver deep link
├── Services/
│   └── DeviceTokenManager.swift                         # MODIFY: switch to FCM tokens
├── Features/
│   ├── PatientManagement/
│   │   └── CaregiverHomeView.swift                      # MODIFY: add .settings tab, consume deep link
│   ├── Settings/
│   │   ├── CaregiverSettingsView.swift                  # NEW: push toggle + overlay
│   │   └── CaregiverPushSettingsViewModel.swift         # NEW: push settings logic
│   ├── Notifications/
│   │   └── NotificationDeepLinkRouter.swift             # MODIFY: parse remote push userInfo
│   └── History/
│       ├── CaregiverHistoryView.swift                   # MODIFY: accept deep link target
│       └── HistoryMonthView.swift                       # MODIFY: scroll + highlight on deep link
├── Networking/
│   └── APIClient.swift                                  # MODIFY: add push register/unregister
├── Resources/
│   └── Localizable.strings                              # MODIFY: add push-related strings
└── Tests/
    └── Push/
        ├── CaregiverPushSettingsTests.swift              # NEW: unit tests
        ├── PushDeepLinkTests.swift                       # NEW: deep link parsing tests
        └── CaregiverPushUITests.swift                    # NEW: UI smoke tests
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). New push endpoints live under `/api/push/` namespace (caregiver auth). iOS adds a new Settings feature module and extends existing Notifications/History modules.

## Complexity Tracking

No Constitution violations noted.

## Phase 0: Outline & Research

### Research Tasks

All unknowns resolved from codebase inspection and dependency spec review:

- **FCM HTTP v1 vs firebase-admin SDK**: Use FCM HTTP v1 REST API directly with Google service account JWT auth. Avoids heavy `firebase-admin` dependency (~50MB), keeps Vercel function bundle within free-tier limits, and is consistent with the existing lightweight HTTP-based pattern in `api/src/services/apnsService.ts`. The iOS side uses FirebaseMessaging SDK for FCM token generation. Rationale: minimize server-side dependencies. Alternative rejected: firebase-admin SDK (too heavy for Vercel).

- **APNs coexistence**: Existing `api/src/services/apnsService.ts` remains for non-012 push paths (inventory alerts from 006). 012 introduces `fcmService.ts` as the new transport. For 012 MVP, `notifyCaregiversOfDoseRecord` is refactored to a new `notifyCaregiversOfDoseTaken` that uses FCM. Rationale: incremental migration avoids breaking existing flows. Alternative rejected: big-bang migration of all push paths (risky).

- **PushDevice vs existing DeviceToken**: Existing `DeviceToken` model lacks `isEnabled`, `environment`, `lastSeenAt`. Create new `PushDevice` model to cleanly separate FCM tokens from legacy APNs tokens. Old DeviceToken table stays for APNs paths until full migration. Rationale: clean separation avoids migration of existing data. Alternative rejected: alter DeviceToken (risks breaking inventory alerts).

- **Dedup strategy**: New `PushDelivery` table with UNIQUE(eventKey, pushDeviceId). eventKey = `doseTaken:{recordingGroupId}` for bulk, or `doseTaken:{doseRecordEventId}` for single/PRN. Insert-or-ignore before FCM send. Rationale: database-level dedup is reliable across retries. Alternative rejected: in-memory dedup (not reliable on serverless).

- **Bulk push refactoring**: Current `slotBulkRecordService.ts` calls `notifyCaregiversOfDoseRecord` per record in a loop (line 198). Refactor to call once after the loop with `notifyCaregiversOfDoseTaken` that accepts aggregated context (date, slot, recordingGroupId, withinTime, displayName). Rationale: spec FR-004 requires exactly one push per device per TAKEN action. Alternative rejected: keeping per-record push (violates spec).

- **iOS FCM token**: Firebase iOS SDK (FirebaseMessaging) provides FCM registration token via `Messaging.messaging().token`. `DeviceTokenManager` switches from hex-encoding APNs tokens to using FCM tokens. `AppDelegate` calls `FirebaseApp.configure()` and sets `Messaging.messaging().delegate`. Rationale: FCM SDK handles token refresh lifecycle. Alternative rejected: manual APNs-to-FCM token mapping (fragile).

- **Caregiver Settings tab**: `CaregiverHomeView` currently has 4 tabs (medications, history, inventory, patients). Add a 5th `.settings` tab with `CaregiverSettingsView`. PaywallUITests already reference a future Settings tab. Rationale: dedicated settings space for push toggle and future billing settings. Alternative rejected: embedding in PatientManagementView (crowded).

- **Deep link for caregiver History**: Currently only patient mode has deep link/scroll/highlight (via PatientReadOnlyView → PatientTodayView). For caregiver mode, extend `NotificationDeepLinkRouter` to parse remote push `userInfo` dict (not just local notification identifiers with `notif:` prefix). `CaregiverHomeView` listens for deep link target, switches to History tab, passes target to `HistoryMonthView` for scroll + highlight. Rationale: reuses existing router pattern. Alternative rejected: separate push deep link handler (redundant).

- **NotRegistered handling**: On FCM send response, if error code is `UNREGISTERED` or `messaging/registration-token-not-registered`, set `PushDevice.isEnabled = false`. No retry. Rationale: stale tokens should be silently disabled per spec FR-008. Alternative rejected: deleting the token (prevents re-registration without full flow).

### Output

- `research.md` with all decisions, rationales, and alternatives consolidated.

## Phase 1: Design & Contracts

**Prerequisites**: `research.md` complete

### Data Model

**PushDevice — new** (in `api/prisma/schema.prisma`):
- `id` String @id @default(uuid())
- `ownerType` String (enum-like: "CAREGIVER")
- `ownerId` String (caregiverId)
- `token` String (FCM registration token)
- `platform` String @default("ios")
- `environment` String ("DEV" | "PROD")
- `isEnabled` Boolean @default(true)
- `lastSeenAt` DateTime @default(now())
- `createdAt` DateTime @default(now())
- `updatedAt` DateTime @updatedAt
- @@unique([ownerType, ownerId, token])
- @@index([ownerId])

**PushDelivery — new** (in `api/prisma/schema.prisma`):
- `id` String @id @default(uuid())
- `eventKey` String (e.g. "doseTaken:{recordingGroupId}")
- `pushDeviceId` String (FK → PushDevice.id)
- `createdAt` DateTime @default(now())
- @@unique([eventKey, pushDeviceId])
- @@index([pushDeviceId])

Full details in `data-model.md`.

### API Contracts

Two new endpoints:

**`POST /api/push/register`**

Auth: `Authorization: Bearer caregiver-{jwt}` (via `requireCaregiver`)

Request body:
```json
{
  "token": "fcm-registration-token",
  "platform": "ios",
  "environment": "DEV"
}
```

Success response (200):
```json
{ "ok": true }
```

Error responses: 401 Unauthorized, 422 Validation error

**`POST /api/push/unregister`**

Auth: `Authorization: Bearer caregiver-{jwt}` (via `requireCaregiver`)

Request body:
```json
{
  "token": "fcm-registration-token"
}
```

Success response (200):
```json
{ "ok": true }
```

Error responses: 401 Unauthorized, 422 Validation error

**Push payload (internal, to FCM HTTP v1)**:
```json
{
  "message": {
    "token": "fcm-device-token",
    "notification": {
      "title": "服薬記録",
      "body": "{displayName}さんが薬を服用しました"
    },
    "data": {
      "type": "DOSE_TAKEN",
      "patientId": "patient-123",
      "date": "2026-02-11",
      "slot": "morning",
      "recordingGroupId": "uuid-or-empty"
    },
    "apns": {
      "payload": {
        "aps": {
          "sound": "default",
          "thread-id": "patient-patient-123"
        }
      }
    }
  }
}
```

Full OpenAPI specification in `contracts/openapi.yaml`.

### Key Implementation: fcmService.ts

FCM HTTP v1 sender flow:
1. Load Google service account from env var (`FCM_SERVICE_ACCOUNT_JSON`, base64 encoded)
2. Generate Google OAuth2 access token via JWT (RS256, scope `https://www.googleapis.com/auth/firebase.messaging`)
3. Cache token for 50 minutes (same pattern as existing APNs JWT caching in `apnsService.ts`)
4. POST to `https://fcm.googleapis.com/v1/projects/{projectId}/messages:send`
5. Parse response: success → log, UNREGISTERED → return error code, other → log warning
6. Single function: `sendFcmMessage(token, notification, data, apnsOverride?)` → `{ success, errorCode? }`
7. Batch function: `sendFcmMessages(tokens, ...)` → array of results

### Key Implementation: notifyCaregiversOfDoseTaken

New function in `pushNotificationService.ts` (replaces per-record push for TAKEN events):
1. Resolve linked caregiver IDs via `CaregiverPatientLink`
2. Fetch enabled PushDevice records for those caregivers (`listEnabledPushDevicesForCaregivers`)
3. Build eventKey from context (recordingGroupId or doseRecordEventId)
4. For each device: try insert PushDelivery (insert-or-ignore via unique constraint)
5. If inserted (new): send FCM push with deep link payload
6. If duplicate: skip (dedup)
7. On UNREGISTERED response: disable PushDevice (isEnabled=false)
8. Fire-and-forget: errors logged but never thrown

### Key Implementation: iOS Deep Link

Extend `NotificationDeepLinkRouter` to handle remote push `userInfo`:
1. `NotificationCoordinator.didReceive(response:)` extracts `userInfo` from remote notification
2. New method `routeFromRemotePush(userInfo: [AnyHashable: Any])` parses `type`, `date`, `slot` from `data` keys
3. Sets `target` as `NotificationDeepLinkTarget(dateKey:, slot:)` (same type as local notifications)
4. `CaregiverHomeView` observes `notificationRouter.$target` → switches to `.history` tab
5. `HistoryMonthView` receives deep link target → opens date detail → scrolls to slot → applies highlight

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
- Test strategy: Pass (tests written first; no external calls; FCM mocked)
- Security & privacy: Pass (caregiver auth via `requireCaregiver`; no PHI in payloads; no PII in logs)
- Performance guardrails: Pass (fire-and-forget FCM call; dedup via DB constraint; no N+1)
- UX/accessibility: Pass (reuses overlay; localized strings; VoiceOver labels)
- Documentation: Pass (research, data model, contracts, quickstart updated)

## Phase 2: Implementation Plan (Tasks)

### Phase 1: Tests / Contracts (test-first)

1) Backend integration tests for push register/unregister
   - **Files**: `api/tests/integration/push-register.test.ts`
   - **Covers**: register upsert (creates PushDevice, sets isEnabled=true, lastSeenAt updated); unregister (sets isEnabled=false); auth required (401 without token); validation errors (422 for missing token/platform); idempotent re-register
   - **Mock pattern**: follows `entitlement-claim.test.ts` — mock auth + repos, lazy import route handler
   - **Tests**: `cd api && npm test`

2) Backend integration tests for push send trigger + dedup
   - **Files**: `api/tests/integration/push-send-trigger.test.ts`
   - **Covers**: TAKEN triggers `notifyCaregiversOfDoseTaken` with correct payload (patientId, date, slot, recordingGroupId, displayName); dedup via PushDelivery prevents duplicate sends for same eventKey+deviceId; no push sent when updatedCount=0; no push sent when no linked caregiver with push enabled; NotRegistered response disables token
   - **Tests**: `cd api && npm test`

3) iOS unit tests for push settings and deep link
   - **Files**: `ios/MedicationApp/Tests/Push/CaregiverPushSettingsTests.swift`, `ios/MedicationApp/Tests/Push/PushDeepLinkTests.swift`
   - **Covers**: toggle ON calls register after permission+token obtained (mock token provider); toggle OFF calls unregister; overlay shown during register/unregister; deep link router parses remote push userInfo with type/date/slot; unknown type returns nil
   - **Tests**: `xcodebuild ... test`

4) iOS UI smoke tests for push flow
   - **Files**: `ios/MedicationApp/Tests/Push/CaregiverPushUITests.swift`
   - **Covers**: caregiver enable push toggle → "更新中" overlay → enabled state visible; push tap deep link → opens History tab → date detail → scroll to slot → highlight event emitted
   - **Tests**: `xcodebuild ... test`

### Phase 2: Backend

5) Prisma migration — add PushDevice + PushDelivery models
   - **Files**: `api/prisma/schema.prisma`
   - **Action**: Add PushDevice and PushDelivery models as designed. Run `npx prisma migrate dev --name push_device_and_delivery`.

6) Push device repository
   - **Files**: `api/src/repositories/pushDeviceRepo.ts`
   - **Action**: `upsertPushDevice({ ownerType, ownerId, token, platform, environment })` — upsert by unique(ownerType, ownerId, token), set isEnabled=true, lastSeenAt=now. `disablePushDevice({ ownerType, ownerId, token })` — set isEnabled=false. `listEnabledPushDevicesForCaregivers(caregiverIds)` — where ownerId in caregiverIds AND isEnabled=true. `disablePushDeviceById(id)` — set isEnabled=false by id.

7) Push delivery repository
   - **Files**: `api/src/repositories/pushDeliveryRepo.ts`
   - **Action**: `tryInsertDelivery({ eventKey, pushDeviceId })` — insert with ON CONFLICT DO NOTHING (returns boolean: true if inserted, false if duplicate). Uses Prisma `createMany` with `skipDuplicates: true` or raw SQL upsert.

8) Push register/unregister validator
   - **Files**: `api/src/validators/pushRegister.ts`
   - **Action**: `validateRegisterRequest(body)` — validates token (non-empty string), platform ("ios"), environment ("DEV"|"PROD"). `validateUnregisterRequest(body)` — validates token (non-empty string). Returns `{ errors, ...parsed }`.

9) POST /api/push/register route
   - **Files**: `api/app/api/push/register/route.ts`
   - **Action**: `requireCaregiver` → validate body → `upsertPushDevice({ ownerType: "CAREGIVER", ownerId: caregiverUserId, ... })` → return `{ ok: true }`. Error handling via `errorResponse`.

10) POST /api/push/unregister route
    - **Files**: `api/app/api/push/unregister/route.ts`
    - **Action**: `requireCaregiver` → validate body → `disablePushDevice({ ownerType: "CAREGIVER", ownerId: caregiverUserId, token })` → return `{ ok: true }`.

11) FCM HTTP v1 sender service
    - **Files**: `api/src/services/fcmService.ts`
    - **Action**: Implement `sendFcmMessage(token, notification, data, apnsOverride?)` using Google service account JWT (RS256) for OAuth2 token. Cache access token for 50 minutes. POST to FCM HTTP v1 endpoint. Return `{ success, errorCode? }`. Handle UNREGISTERED error code. `isEnabled()` check via env var `FCM_SERVICE_ACCOUNT_JSON`.

12) Refactor pushNotificationService.ts
    - **Files**: `api/src/services/pushNotificationService.ts`
    - **Action**: Add `notifyCaregiversOfDoseTaken(input)` function accepting `{ patientId, displayName, date, slot, recordingGroupId?, withinTime, isPrn }`. Resolves linked caregivers → fetches enabled PushDevices → builds eventKey → dedup via PushDelivery → sends via FCM → disables on UNREGISTERED. Fire-and-forget. Existing `notifyCaregiversOfDoseRecord` remains for backward compatibility during transition.

13) Wire trigger in slotBulkRecordService.ts
    - **Files**: `api/src/services/slotBulkRecordService.ts`
    - **Action**: Replace per-record `notifyCaregiversOfDoseRecord` loop (lines ~195-205) with single call to `notifyCaregiversOfDoseTaken({ patientId, displayName, date: input.date, slot: input.slot, recordingGroupId, withinTime: <any record within time>, isPrn: false })` after the side effects loop.

14) Wire trigger in doseRecordService.ts + prnDoseRecordService.ts
    - **Files**: `api/src/services/doseRecordService.ts`, `api/src/services/prnDoseRecordService.ts`
    - **Action**: In `createDoseRecordIdempotent`, replace `notifyCaregiversOfDoseRecord` call with `notifyCaregiversOfDoseTaken` passing date (from scheduledAt, Tokyo), slot (resolved from scheduledAt), withinTime, and no recordingGroupId. In `createPrnRecord`, same replacement with isPrn=true and slot derived from takenAt.

15) Verify all backend tests pass
    - **Tests**: `cd api && npm test`

### Phase 3: iOS

16) Add Firebase SDK via SPM, configure in AppDelegate
    - **Files**: `ios/MedicationApp/App/AppDelegate.swift`, Xcode project
    - **Action**: Add FirebaseMessaging SPM package. In `application(_:didFinishLaunchingWithOptions:)`, call `FirebaseApp.configure()`. Set `Messaging.messaging().delegate = self`. Forward APNs token to FCM via `Messaging.messaging().apnsToken = deviceToken`.

17) Modify DeviceTokenManager for FCM tokens
    - **Files**: `ios/MedicationApp/Services/DeviceTokenManager.swift`
    - **Action**: Replace APNs hex-encoding with FCM token retrieval via `Messaging.messaging().token`. Store FCM token in UserDefaults. `registerWithBackend` sends FCM token (not APNs token) to `/api/push/register`. `unregisterFromBackend` sends to `/api/push/unregister`. Add environment detection (DEBUG → "DEV", release → "PROD").

18) Add registerPushDevice / unregisterPushDevice to APIClient
    - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
    - **Action**: New methods: `registerPushDevice(token:platform:environment:) async throws` — POST to `/api/push/register`. `unregisterPushDevice(token:) async throws` — POST to `/api/push/unregister`. Uses caregiver session token auth.

19) Add CaregiverTab.settings + CaregiverSettingsView
    - **Files**: `ios/MedicationApp/Features/PatientManagement/CaregiverHomeView.swift`, `ios/MedicationApp/Features/Settings/CaregiverSettingsView.swift`
    - **Action**: Add `.settings` case to `CaregiverTab`. Add settings tab case in CaregiverHomeView body switch. Create `CaregiverSettingsView` with "見守りPush通知" section containing a toggle. Uses `CaregiverPushSettingsViewModel`. Shows `SchedulingRefreshOverlay` during register/unregister.

20) CaregiverPushSettingsViewModel
    - **Files**: `ios/MedicationApp/Features/Settings/CaregiverPushSettingsViewModel.swift`
    - **Action**: `@Published isPushEnabled: Bool`. `@Published isUpdating: Bool`. Toggle ON: request `UNUserNotificationCenter` permission → get FCM token → call `apiClient.registerPushDevice(...)` → set isPushEnabled=true. Toggle OFF: call `apiClient.unregisterPushDevice(...)` → set isPushEnabled=false. Error handling with user-facing alert. Persist last-known state in UserDefaults.

21) Extend NotificationDeepLinkRouter for remote push
    - **Files**: `ios/MedicationApp/Features/Notifications/NotificationDeepLinkRouter.swift`
    - **Action**: Add `routeFromRemotePush(userInfo: [AnyHashable: Any])`. Parses `type` key (must be "DOSE_TAKEN"), `date` key → dateKey, `slot` key → NotificationSlot. Sets `target`. Existing `parse(identifier:)` for local notifications unchanged.

22) Extend NotificationCoordinator for remote push
    - **Files**: `ios/MedicationApp/Features/Notifications/NotificationDeepLinkRouter.swift`
    - **Action**: In `didReceive(response:)`, check if notification is remote (has `userInfo` with `type` key). If remote, call `router.routeFromRemotePush(userInfo:)`. Otherwise fall through to existing `route(identifier:)`.

23) CaregiverHomeView: consume deep link, switch to History
    - **Files**: `ios/MedicationApp/Features/PatientManagement/CaregiverHomeView.swift`
    - **Action**: Add `@EnvironmentObject var notificationRouter: NotificationDeepLinkRouter`. Add `.onReceive(notificationRouter.$target)` handler: if target is non-nil and mode is caregiver, switch `selectedTab` to `.history`, pass deep link target to history view, then clear router.

24) HistoryMonthView: deep link scroll + highlight
    - **Files**: `ios/MedicationApp/Features/History/CaregiverHistoryView.swift`, `ios/MedicationApp/Features/History/HistoryMonthView.swift`
    - **Action**: Add `deepLinkTarget: NotificationDeepLinkTarget?` binding. On non-nil target: open day detail for `dateKey`, scroll to slot section, apply `TodaySlotHighlight`-style pulse/glow animation for ~3 seconds. Reuses highlight pattern from `PatientTodayView.swift`.

25) Localizable.strings additions
    - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
    - **Action**: Add keys: `caregiver.settings.push.section.title` ("見守りPush通知"), `caregiver.settings.push.toggle` ("Push通知"), `caregiver.settings.push.permission.denied` (permission guidance), `caregiver.settings.push.error` (network error), `caregiver.tabs.settings` ("設定"). Update `CaregiverBottomTabBar` with settings tab label.

26) Verify all iOS tests pass
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 4: Docs / Finalization

27) Generate spec artifacts
    - **Files**: `specs/012-push-foundation/research.md`, `data-model.md`, `quickstart.md`, `contracts/openapi.yaml`
    - **Action**: Already written as part of /speckit.plan execution.

28) Agent context update
    - **Action**: Run `.specify/scripts/bash/update-agent-context.sh cursor-agent`

## Acceptance Criteria

- Caregiver can enable/disable push in Settings; device registered/disabled server-side
- Patient TAKEN (single, bulk, PRN) triggers exactly one push per caregiver device (deduped)
- Push tap navigates to caregiver History at correct date/slot with highlight
- No cron/worker required; compatible with Vercel free plan
- All required tests pass (backend integration + iOS unit/UI smoke)

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`
