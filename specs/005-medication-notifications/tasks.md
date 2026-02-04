# Tasks: Medication Notifications

**Input**: Design documents from `/specs/005-medication-notifications/`  
**Prerequisites**: plan.md (required), spec.md, research.md, data-model.md, contracts/  
**Tests**: Tests are REQUIRED by spec (iOS unit + UI smoke, backend integration)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1/US2/US3)
- Each task includes Purpose (Why), Files, Done Criteria (AC), and Test command

---

## Phase 1: Tests (Required First)

**Purpose**: Lock requirements and acceptance before implementation

### Tests for User Story 1 (P1)

- [x] T001 [P] [US1] Add unit tests for NotificationPlanBuilder (7-day window, month crossover, PENDING-only) in `ios/MedicationApp/Tests/Notifications/NotificationPlanBuilderTests.swift` (Why: enforce scheduling rules; AC: tests cover window, month boundary, PENDING filter; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T002 [P] [US1] Add unit tests for stable id generation + cancel rules in `ios/MedicationApp/Tests/Notifications/NotificationSchedulerTests.swift` (Why: avoid duplicate notifications and validate cancel on TAKEN; AC: ids match `notif:{YYYY-MM-DD}:{slot}:{1|2}` and secondary cancel triggers when slot no longer pending; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T003 [P] [US1] Add unit tests for notification tap routing (Today tab + scroll target + highlight) in `ios/MedicationApp/Tests/Notifications/NotificationDeepLinkTests.swift` (Why: ensure correct navigation; AC: Today tab selected, scroll target computed, highlight event emitted; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T004 [P] [US1] Add UI smoke test for notification tap → Today + highlight in `ios/MedicationApp/Tests/Notifications/NotificationTapUITests.swift` (Why: core reminder flow; AC: Today tab opened, slot highlighted; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

### Tests for User Story 2 (P2)

- [x] T005 [P] [US2] Add unit tests for settings toggles triggering reschedule in `ios/MedicationApp/Tests/Notifications/NotificationSettingsTests.swift` (Why: settings must refresh plan; AC: toggles update store and call reschedule; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T006 [P] [US2] Add UI smoke tests for Settings permission states + overlay blocking in `ios/MedicationApp/Tests/Notifications/NotificationSettingsUITests.swift` (Why: UX requirement; AC: denied state disables toggles + guidance, overlay blocks input during refresh; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

### Tests for User Story 3 (P3)

- [x] T007 [P] [US3] Add UI smoke test for caregiver banner on any screen in `ios/MedicationApp/Tests/Caregiver/CaregiverBannerUITests.swift` (Why: caregiver visibility; AC: banner appears while foregrounded from any screen; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [x] T008 [P] [US3] Add API integration test for dose record event insertion in `api/tests/integration/dose-record-event.test.ts` (Why: backend emits event; AC: event row created with withinTime; Test: `npm test` from `api/`)
- [x] T009 [P] [US3] Add API integration test for RLS concealment in `api/tests/integration/dose-record-event-rls.test.ts` (Why: security/privacy; AC: non-owned caregiver cannot read events; Test: `npm test` from `api/`)

---

## Phase 2: Backend (Events + RLS)

**Purpose**: Emit realtime events for caregiver banner with secure access

### Implementation for User Story 3

- [ ] T010 [US3] Add `dose_record_events` model + migration in `api/prisma/schema.prisma` and `api/prisma/migrations/*_dose_record_events/` (Why: persist event payload; AC: migration applies and schema includes withinTime fields; Test: `npm test`)
- [ ] T011 [US3] Implement event repository in `api/src/repositories/doseRecordEventRepo.ts` (Why: isolate write + read logic; AC: insert/read methods typed and covered by tests; Test: `npm test`)
- [ ] T012 [US3] Emit event on TAKEN in `api/src/services/doseRecordService.ts` (Why: generate realtime payload; AC: event created only on TAKEN; withinTime computed as `takenAt <= scheduledAt + 60m`; Test: `npm test`)
- [ ] T013 [US3] Add RLS policies + realtime publication in `api/prisma/migrations/*_dose_record_events/` (Why: restrict caregiver visibility; AC: caregivers see only linked patients; Test: `npm test`)
- [ ] T014 [US3] Update tests/fixtures for caregiver linking in `api/tests/_db/testDb.ts` (Why: RLS tests need linkage; AC: fixtures create linked caregiver/patient; Test: `npm test`)

---

## Phase 3: iOS Notifications + Settings

**Purpose**: Build local notification permissions, preferences, scheduling, and refresh

### Implementation for User Story 1 (P1)

- [ ] T015 [P] [US1] Create `NotificationPlanBuilder` in `ios/MedicationApp/Features/Notifications/NotificationPlanBuilder.swift` (Why: compute 7-day plan from slotSummary; AC: month crossover fetch, PENDING-only slots, Tokyo boundaries; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T016 [P] [US1] Create `NotificationScheduler` in `ios/MedicationApp/Features/Notifications/NotificationScheduler.swift` (Why: schedule/cancel local notifications; AC: identifiers use `notif:{YYYY-MM-DD}:{slot}:{1|2}` and secondary at +15; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T017 [US1] Implement `SchedulingRefreshCoordinator` in `ios/MedicationApp/Features/Notifications/SchedulingRefreshCoordinator.swift` (Why: auto refresh on launch/foreground/settings/TAKEN; AC: triggers per requirement and uses overlay; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T018 [US1] Integrate history month/day fetch in `ios/MedicationApp/Networking/HistoryClient.swift` (or existing) for plan building (Why: slotSummary source of truth; AC: fetches month and next month on boundary; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T019 [US1] Wire full-screen overlay for scheduling refresh in `ios/MedicationApp/Shared/Views/FullScreenContainer.swift` and Settings/Today flows (Why: block interactions during refresh; AC: overlay shows on refresh and removed on error with retry; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

### Implementation for User Story 2 (P2)

- [ ] T020 [P] [US2] Create `NotificationPermissionManager` in `ios/MedicationApp/Features/Notifications/NotificationPermissionManager.swift` (Why: centralize authorization state; AC: can query/refresh permission and expose denied/authorized; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T021 [P] [US2] Create `NotificationPreferencesStore` in `ios/MedicationApp/Features/Notifications/NotificationPreferencesStore.swift` (Why: persist master/slot/rereminder toggles; AC: defaults master OFF, slot toggles ON, rereminder OFF; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T022 [US2] Update Settings UI in `ios/MedicationApp/Features/Settings/NotificationSettingsView.swift` (Why: master/slot/rereminder toggles + guidance; AC: denied state disables toggles and shows guidance text; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

---

## Phase 4: Deep Link + Highlight

**Purpose**: Route notification taps to Today and highlight target slot

### Implementation for User Story 1 (P1)

- [ ] T023 [US1] Implement `NotificationDeepLinkRouter` in `ios/MedicationApp/Features/Notifications/NotificationDeepLinkRouter.swift` (Why: handle notification taps; AC: opens Today tab and routes slot target; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T024 [US1] Add slot highlight effect in `ios/MedicationApp/Features/Today/TodaySlotHighlight.swift` (Why: visual confirmation; AC: glow/pulse for a few seconds; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T025 [US1] Handle non-pending tap case in `ios/MedicationApp/Features/Today/TodayView.swift` (Why: clarify already recorded state; AC: shows brief “already recorded” message; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T026 [US1] Show in-app banner when reminder fires while open in `ios/MedicationApp/Shared/Banner/ReminderBannerPresenter.swift` (Why: foreground reminder cue; AC: banner appears; if on Today, highlight triggers; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

---

## Phase 5: Caregiver Realtime Banner

**Purpose**: Deliver realtime caregiver banners without push or cron

### Implementation for User Story 3 (P3)

- [ ] T027 [US3] Add `CaregiverEventSubscriber` in `ios/MedicationApp/Features/Caregiver/CaregiverEventSubscriber.swift` (Why: subscribe to realtime events; AC: listens while foreground and filters `withinTime == true`; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T028 [US3] Implement `GlobalBannerPresenter` in `ios/MedicationApp/Shared/Banner/GlobalBannerPresenter.swift` (Why: show caregiver banner; AC: top banner ~3 seconds, newest wins (queue length 1); Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T029 [US3] Wire lifecycle start/stop in `ios/MedicationApp/Features/Caregiver/CaregiverSessionController.swift` (Why: avoid background work; AC: subscribes on foreground, unsubscribes on background; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)
- [ ] T030 [US3] Handle revoked/unauthorized caregiver sessions in `ios/MedicationApp/Features/Caregiver/CaregiverEventSubscriber.swift` (Why: security/privacy; AC: no events shown when access revoked; Test: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`)

---

## Phase 6: Docs

**Purpose**: Documentation and final verification

- [ ] T031 [P] Update `specs/005-medication-notifications/quickstart.md` with test commands and reminder flows (Why: run/test guidance; AC: commands match plan; Test: N/A)
- [ ] T032 [P] Update `specs/005-medication-notifications/contracts/openapi.yaml` with endpoints used + DoseRecordEvent schema (Why: contract traceability; AC: endpoints and event schema present; Test: N/A)
- [ ] T033 [P] Update `specs/005-medication-notifications/data-model.md` if implementation diverges (Why: maintain spec alignment; AC: entities match code; Test: N/A)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Tests)**: No dependencies
- **Phase 2 (Backend)**: Depends on Phase 1 backend tests
- **Phase 3 (iOS Notifications/Settings)**: Depends on Phase 1 iOS tests
- **Phase 4 (Deep Link + Highlight)**: Depends on Phase 3 scheduling core
- **Phase 5 (Caregiver Realtime Banner)**: Depends on Phase 2 backend events
- **Phase 6 (Docs)**: Depends on implementation completion

### User Story Dependencies

- **US1 (P1)**: Phases 1, 3, 4
- **US2 (P2)**: Phase 3
- **US3 (P3)**: Phases 1, 2, 5

### Parallel Opportunities

- T001–T004 can run in parallel
- T005–T009 can run in parallel
- T015–T016 can run in parallel
- T020–T021 can run in parallel

---

## Parallel Example: User Story 1

```bash
# Tests in parallel
Task: "T001 NotificationPlanBuilder tests"
Task: "T002 NotificationScheduler tests"
Task: "T003 NotificationDeepLink tests"

# Implementation in parallel after scheduling core setup
Task: "T015 NotificationPlanBuilder implementation"
Task: "T016 NotificationScheduler implementation"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 → Phase 3 (US1)
2. Build Phase 4 (Deep link + highlight)
3. Validate with `xcodebuild ... test`

### Incremental Delivery

1. US1 → validate reminder flows
2. US2 → add Settings controls
3. US3 → add backend events + caregiver banners
4. Docs and final verification
