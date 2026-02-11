# Feature Specification: Slot Bulk Dose Recording

**Feature Branch**: `0115-slot-bulk-dose-recording`  
**Created**: 2026-02-11  
**Status**: Draft  
**Input**: User description: "患者モードの服用記録を「薬ごと」から「時間帯スロット一括」に変更し、高齢者UXを改善する。スロットカードに予定薬一覧と一括記録ボタンを表示し、確認ダイアログ後にサーバへ一括記録する。"

**Dependencies**:

- 003-dose-recording: 服用記録（TAKEN/MISSED/PENDING）と withinTime（scheduledAt+60m以内）
- 004-history-schedule-view: 今日/履歴表示（スロット単位の閲覧が前提）
- 005-medication-notifications: スロットに基づく通知・状態更新

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Patient bulk-records a slot's doses (Priority: P1)

As a patient, I can record all scheduled medications for a time slot (morning, noon, evening, bedtime) in a single action so that I do not need to tap each medication individually.

The patient opens the Today tab, sees slot cards grouped by time of day, and taps the large "この時間のお薬を飲んだ" button on a slot card. A confirmation dialog appears showing the slot label, time, number of medication kinds, and total pill count. After confirming, the system records all PENDING and MISSED doses in that slot as TAKEN in a single bulk operation. A full-screen "更新中" overlay blocks interaction during the API call and subsequent data refresh.

**Why this priority**: This is the core interaction change that replaces per-medication recording with slot-based bulk recording, directly improving elderly patient UX by reducing the number of taps required.

**Independent Test**: Can be fully tested by loading a patient's Today tab with multiple medications in a slot and completing a bulk record.

**Acceptance Scenarios**:

1. **Given** a patient has 3 PENDING medications in the morning slot, **When** the patient taps "この時間のお薬を飲んだ" and confirms, **Then** all 3 medications are recorded as TAKEN and the slot card updates to show TAKEN status.
2. **Given** a bulk record is in progress, **When** the API call and data refresh are running, **Then** a full-screen "更新中" overlay is displayed and all user interaction is blocked.
3. **Given** a bulk record completes successfully, **When** the overlay dismisses, **Then** a brief success feedback (toast or banner) is shown and the slot card reflects the updated TAKEN status.
4. **Given** a patient has a mix of PENDING and MISSED medications in a slot, **When** the patient bulk-records, **Then** both PENDING and MISSED medications become TAKEN (MISSED ones with withinTime=false).

---

### User Story 2 - Slot card displays medication details and summary (Priority: P2)

As a patient, I can see each slot card showing all scheduled medications with their names, dosages, and per-dose counts, plus a summary of total pills and medication kinds, so I know exactly what and how much I am taking.

**Why this priority**: The slot card layout is essential for the patient to understand what medications are in each slot before deciding to record. It supports informed decision-making and reduces errors.

**Independent Test**: Can be tested by loading the patient Today tab and verifying the slot card layout matches the expected format for a patient with known medications.

**Acceptance Scenarios**:

1. **Given** a patient has medications scheduled for the morning slot, **When** the Today tab loads, **Then** the morning slot card header shows the slot name ("朝"), the patient's configured slot time (e.g., "07:30"), a status badge (PENDING/TAKEN/MISSED), and the remaining recordable count.
2. **Given** a slot card body, **When** the patient views it, **Then** each medication is listed on its own row showing medication name + dosage (e.g., "アムロジピン 5mg") and per-intake count (e.g., "1回2錠").
3. **Given** a slot card with 3 medications totalling 5 pills, **When** the patient views the card, **Then** a summary line at the bottom of the body shows "合計5錠（3種類）".
4. **Given** all medications in a slot are already TAKEN, **When** the slot card renders, **Then** the bulk record button is disabled or hidden and the remaining count shows 0.

---

### User Story 3 - Confirmation dialog with slot summary (Priority: P3)

As a patient, I must confirm before a bulk record is submitted so that accidental taps do not create irreversible records.

**Why this priority**: Since patients cannot undo records, the confirmation step is a critical safeguard against accidental recordings, especially for elderly users.

**Independent Test**: Can be tested by tapping the bulk record button and verifying the confirmation dialog content and behavior.

**Acceptance Scenarios**:

1. **Given** the patient taps "この時間のお薬を飲んだ" on the morning slot, **When** the confirmation dialog appears, **Then** it shows the title "{slotLabel}のお薬を記録", the body text "{slotLabel}（{slotTime}）のお薬（N種類 / 合計Y錠）を記録しますか？", and buttons "記録する" and "キャンセル".
2. **Given** the confirmation dialog is shown, **When** the patient taps "キャンセル", **Then** the dialog dismisses and no record is created.
3. **Given** the confirmation dialog is shown, **When** the patient taps "記録する", **Then** the bulk record API is called and the full-screen overlay appears.

---

### User Story 4 - MISSED doses remain recordable via bulk (Priority: P4)

As a patient, I can still bulk-record doses that are past the 60-minute window (MISSED) so that late medication intake is still tracked.

**Why this priority**: Patients who are late should not be blocked from recording. Tracking late doses (with withinTime=false) is important for adherence visibility.

**Independent Test**: Can be tested by advancing past the slot time + 60 minutes and verifying that the slot shows MISSED status but bulk recording still works.

**Acceptance Scenarios**:

1. **Given** a slot's scheduledAt + 60 minutes has passed and no doses are recorded, **When** the Today tab displays the slot, **Then** the slot card shows a MISSED status badge to alert the patient.
2. **Given** a slot is in MISSED state, **When** the patient taps bulk record and confirms, **Then** all MISSED doses become TAKEN with withinTime=false and takenAt set to the current time.
3. **Given** the patient has a custom slot time different from the default, **When** MISSED status is evaluated, **Then** the scheduledAt used for the 60-minute threshold reflects the patient's configured slot time, not the default.

---

### User Story 5 - Caregiver sees bulk-recorded results (Priority: P5)

As a caregiver, I see patient dose records created by bulk recording reflected correctly in the caregiver's today, history, and calendar views, without any change to my existing per-dose record/cancel workflow.

**Why this priority**: Caregiver flows must remain stable. This story ensures backward compatibility and data consistency across user roles.

**Independent Test**: Can be tested by having a patient bulk-record in patient mode, then switching to caregiver mode and verifying the records appear correctly.

**Acceptance Scenarios**:

1. **Given** a patient bulk-records a morning slot, **When** the caregiver views the patient's today tab, **Then** all medications in that slot show as TAKEN with recordedByType=PATIENT.
2. **Given** a caregiver viewing a patient's schedule, **When** the caregiver taps record or cancel on individual doses, **Then** the existing per-dose caregiver workflow operates unchanged.
3. **Given** a patient bulk-records and the caregiver views history/calendar, **When** the data loads, **Then** the slot summary shows the correct aggregated status reflecting the bulk-recorded doses.

---

### Edge Cases

- **All doses already TAKEN**: The bulk record button is disabled or hidden. If the API is called anyway (e.g., race condition), it returns updatedCount=0 and creates no new records (idempotent).
- **Mixed statuses in a slot**: Only PENDING and MISSED doses are updated to TAKEN; already-TAKEN doses are skipped. The response reflects the actual number updated.
- **Network failure during bulk record**: The full-screen overlay remains visible. An error toast is shown. No partial commit occurs (the operation is transactional).
- **Patient slot time differs from default**: The slot card header, MISSED threshold, and withinTime calculation all use the patient's configured slot time, not the default 08:00/12:00/19:00/22:00.
- **Concurrent duplicate requests**: The second request returns updatedCount=0 since all doses are already TAKEN from the first request (idempotent).
- **Slot with zero scheduled medications**: The slot card is not displayed (or displayed with an empty state and no record button).
- **Patient session revoked mid-operation**: The API returns an authentication error. The overlay dismisses and an appropriate error message is shown.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display slot cards on the patient Today tab, one per time slot (morning, noon, evening, bedtime), ordered by slot sequence.
- **FR-002**: Each slot card header MUST show: slot label (朝/昼/夜/眠前), the patient's configured slot time (HH:mm), a status badge (PENDING/TAKEN/MISSED), and remaining recordable count (PENDING + MISSED count).
- **FR-003**: Each slot card body MUST list all scheduled medications for that slot, each row showing medication name + dosage text (e.g., "アムロジピン 5mg") and per-intake count (e.g., "1回2錠").
- **FR-004**: Each slot card body MUST display a summary line at the bottom: "合計Y錠（N種類）" where Y is the sum of doseCountPerIntake across all listed medications and N is the count of distinct medications.
- **FR-005**: Each slot card MUST include a primary action button labeled "この時間のお薬を飲んだ" that is disabled or hidden when remaining recordable count equals zero.
- **FR-006**: System MUST show a confirmation dialog before executing a bulk record, containing the slot label, slot time, medication kind count (N), and total pill count (Y), with "記録する" and "キャンセル" buttons.
- **FR-007**: Bulk record MUST update all doses with status PENDING or MISSED in the target slot to TAKEN, setting takenAt to the current time (Asia/Tokyo). Doses already TAKEN MUST be skipped (idempotent).
- **FR-008**: For each dose updated, withinTime MUST be computed as `takenAt <= scheduledAt + 60 minutes`, where scheduledAt is derived from the patient's configured slot time or the generated scheduled dose time.
- **FR-009**: System MUST display a full-screen "更新中" overlay during the bulk record API call and any subsequent data refresh, blocking all user interaction until complete.
- **FR-010**: Patients MUST NOT be able to undo or cancel bulk-recorded doses (existing patient immutability policy).
- **FR-011**: Caregiver per-dose record and cancel workflows MUST remain unchanged. Caregivers MUST NOT use the bulk record endpoint in this feature scope.
- **FR-012**: Each bulk record operation SHOULD generate a recordingGroupId (UUID) and attach it to all dose records updated in that operation, to support downstream features (push notifications, banners, PDF export) that need slot-level grouping.
- **FR-013**: All slot time displays, MISSED status thresholds, and withinTime calculations MUST use the patient's per-patient slot time settings, not hardcoded defaults.
- **FR-014**: The bulk record API MUST be transactional: either all eligible doses are updated or none are (no partial updates on failure).
- **FR-015**: On successful bulk record, the system MUST show brief success feedback (toast or banner) after the overlay dismisses.
- **FR-016**: On API failure, the system MUST dismiss the overlay and show an error message. No records are created on failure.

### Non-Functional Requirements

- **NFR-001 (Performance)**: The bulk record operation MUST complete (API response returned) within 2 seconds for a typical slot containing up to 10 medications.
- **NFR-002 (Idempotency)**: Repeated bulk record calls for the same patient, date, and slot MUST NOT create duplicate records or produce errors.
- **NFR-003 (Atomicity)**: The bulk update MUST be executed as a single atomic operation to ensure all-or-nothing semantics.
- **NFR-004 (Accessibility)**: Slot cards, the bulk record button, and the confirmation dialog MUST support VoiceOver with descriptive labels including slot name, medication count, and action description.
- **NFR-005 (Localization)**: All user-facing strings (slot labels, button text, confirmation text, overlay text, feedback messages) MUST use localized string resources.

### Key Entities

- **Slot Card**: A UI grouping of scheduled doses for a single time slot (morning/noon/evening/bedtime) on a given date for a specific patient. Contains header metadata, medication rows, summary, and action button.
- **Dose Record**: An existing data entity representing a single scheduled dose's recording state. Optionally extended with a `recordingGroupId` (UUID, nullable) field to link records created in the same bulk operation.
- **Slot Time Setting**: Per-patient configuration of slot times (HH:mm per slot). Each patient may have custom slot times that override the system defaults (08:00, 12:00, 19:00, 22:00). The recording system uses these patient-specific times for all display, MISSED evaluation, and withinTime calculations.
- **Recording Group**: A logical grouping of dose records created in a single bulk operation, identified by a shared `recordingGroupId` UUID.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Patients can view all scheduled medications per slot and complete bulk recording in 2 taps (button + confirm) within 10 seconds from the Today tab.
- **SC-002**: Bulk recording of a slot with up to 10 medications completes end-to-end (tap confirm to overlay dismiss) in under 3 seconds.
- **SC-003**: 100% of PENDING and MISSED doses in a targeted slot are updated to TAKEN in a single bulk action, with zero manual per-dose taps required.
- **SC-004**: Repeated bulk record operations for the same slot produce no duplicate records, no errors, and return updatedCount=0 on subsequent calls.
- **SC-005**: Slot time displayed in the UI matches the patient's configured slot time settings, and MISSED status / withinTime calculations are consistent with that displayed time.
- **SC-006**: Caregiver today, history, and calendar views correctly reflect dose records created via patient bulk recording without any caregiver workflow changes.
- **SC-007**: All backend integration tests and iOS unit/UI smoke tests defined in the testing requirements pass.

## Assumptions

- Patient-specific slot times are already managed per-patient in the existing system. No new slot time storage or settings UI is introduced in this feature.
- The existing dose record data model uses a unique constraint on patient, medication, and scheduled time, which naturally prevents duplicate records and supports idempotent bulk operations.
- The "更新中" full-screen overlay pattern already exists in the application (per Global UX convention from dependency specs) and can be reused.
- Inventory adjustments (if applicable) are handled per dose record as in the existing single-record flow.
- The bulk record endpoint is for patient sessions only. Caregiver bulk recording is explicitly out of scope for this feature.
