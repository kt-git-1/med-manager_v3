# Feature Specification: Dose Recording

**Feature Branch**: `003-dose-recording`  
**Created**: 2026-02-03  
**Status**: Draft  
**Input**: User description: "Dose recording for scheduled doses with patient and caregiver flows, missed status, auto-sync, and reminders."

## Clarifications

### Session 2026-02-03

- Q: When is a dose considered missed relative to the 60-minute threshold? → A: Missed only when more than 60 minutes have passed since scheduled time.
- Q: Should reminder notifications allow multiple distinct scheduled doses, even if they are close together? → A: Allow multiple reminders as long as they are distinct scheduled doses.
- Q: What response should caregivers receive when accessing non-owned patients? → A: Always return not found.
- Q: How many reminder attempts per scheduled dose are allowed? → A: Up to 2 reminders per scheduled dose.
- Q: What feedback is shown when a patient records a dose that is already taken? → A: Show "already recorded" and keep status as taken.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Patient records a taken dose (Priority: P1)

As a patient, I can confirm that I took a scheduled dose so it is recorded as taken and no longer shows as pending or missed.

**Why this priority**: This is the core daily action for patients and enables adherence tracking.

**Independent Test**: Can be fully tested by loading today's schedule and recording a single dose as taken.

**Acceptance Scenarios**:

1. **Given** a scheduled dose for today that is pending, **When** the patient taps "Taken" and confirms, **Then** the dose shows as taken and remains taken on refresh.
2. **Given** a scheduled dose for today, **When** the patient taps "Taken" twice quickly and confirms once, **Then** only one taken record exists and the UI remains consistent.
3. **Given** a scheduled dose recorded as taken by the patient, **When** the patient attempts to undo, **Then** the app communicates that undo is not available for patients.

---

### User Story 2 - Caregiver records or cancels a taken dose (Priority: P2)

As a caregiver, I can record a taken dose for a specific patient or remove it when it was recorded by mistake.

**Why this priority**: Caregivers need to assist patients and correct entries on their behalf.

**Independent Test**: Can be tested by selecting a patient and creating then deleting a taken record for a scheduled dose.

**Acceptance Scenarios**:

1. **Given** a caregiver viewing a patient's today list, **When** the caregiver records a dose as taken, **Then** the dose shows as taken for that patient.
2. **Given** a caregiver recorded a dose as taken, **When** the caregiver deletes the record, **Then** the dose returns to pending or missed based on time.
3. **Given** a caregiver is not associated with a patient, **When** they try to access that patient's today list or dose record actions, **Then** the patient appears not found.

---

### User Story 3 - Patients see missed doses and reminders (Priority: P3)

As a patient, I receive reminders at dose time and see missed doses highlighted if not recorded within an hour.

**Why this priority**: Timely reminders and visible missed status encourage adherence without requiring manual refresh.

**Independent Test**: Can be tested by simulating a dose time and verifying reminder delivery and missed status display after the threshold.

**Acceptance Scenarios**:

1. **Given** a scheduled dose time arrives, **When** the patient is using the app or the app is backgrounded, **Then** a reminder notification is delivered for that dose.
2. **Given** a scheduled dose is not recorded for more than 60 minutes after its scheduled time, **When** the today list is refreshed, **Then** the dose is displayed as missed with a warning emphasis.
3. **Given** the today screen appears or the app returns to foreground, **When** data is syncing, **Then** a full-screen updating overlay blocks interaction until the refresh completes.

---

### Edge Cases

- Exactly 60 minutes after scheduled time is still not missed; missed starts after 60 minutes.
- How does the app behave if the patient session has been revoked?
- What happens when a caregiver tries to delete a dose that is already not recorded?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display today's scheduled doses with an effective status of pending, taken, or missed.
- **FR-002**: System MUST allow patients to record a scheduled dose as taken, and patients MUST NOT be able to delete or edit that record.
- **FR-003**: System MUST allow caregivers to record and delete taken doses for patients they are authorized to manage.
- **FR-004**: System MUST treat a dose as missed when the current time is more than 60 minutes after the scheduled time and no taken record exists.
- **FR-005**: System MUST treat dose recording as idempotent for the same scheduled dose so repeated requests do not create duplicates.
- **FR-005a**: System MUST show an "already recorded" confirmation when a patient attempts to record an already taken dose.
- **FR-006**: System MUST show a confirmation dialog before a patient records a dose, including the medication name, scheduled time, and a warning that it cannot be undone in patient mode.
- **FR-007**: System MUST auto-refresh the today list on screen appearance, app foregrounding, and after recording actions, and MUST block interaction with a full-screen updating message during refresh.
- **FR-008**: System MUST deliver a reminder notification to patients at the scheduled dose time for today's schedule.
- **FR-008a**: System MUST deliver reminders per scheduled dose and MUST NOT suppress reminders for distinct scheduled doses.
- **FR-008b**: System MUST limit reminders to at most two notifications per scheduled dose.
- **FR-009**: System MUST require a valid patient session for patient actions and MUST prevent caregivers from learning about patients they do not manage.
- **FR-009a**: System MUST return not found for caregiver access to non-owned patients.
- **FR-010**: System MUST store only taken records, and MUST derive pending and missed status without storing them.
- **FR-011**: System MUST treat a scheduled dose as uniquely identified by patient, medication, and scheduled time.

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Performance)**: Users MUST see the refreshed today list within 5 seconds of opening the screen or returning to the app under typical network conditions.
- **NFR-002 (Security/Privacy)**: Revoked patient sessions MUST lose access immediately, and unauthorized caregiver requests MUST appear as not found.
- **NFR-003 (UX/Accessibility)**: The "Taken" action MUST be visually large and easy to tap, missed doses MUST be visually emphasized, and the updating overlay MUST clearly indicate progress.
- **NFR-004 (Documentation/Operations)**: User-facing guidance MUST be updated to explain missed status, caregiver undo, and reminders.

### Key Entities *(include if feature involves data)*

- **Scheduled Dose**: A planned intake time tied to a patient and medication, used as the unit for recording.
- **Dose Record**: A recorded taken event for a scheduled dose, including who recorded it and when.
- **Dose Reminder**: A notification tied to a scheduled dose time for a patient.

## Assumptions

- Today’s schedule is already available from the regimen feature and includes the medication display details needed for confirmation.
- Caregivers already have a way to select a patient they manage.

## Out of Scope

- Patients undoing or editing taken records.
- Ad-hoc or free-form dose recording outside the schedule.
- Full dose history or calendar views beyond today.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Patients can complete a taken recording flow in under 30 seconds from tap to updated status.
- **SC-002**: In acceptance tests, 100% of scheduled doses reflect the correct pending/taken/missed status for defined time scenarios.
- **SC-003**: At least 95% of reminder notifications are delivered within 5 minutes of the scheduled dose time.
- **SC-004**: Caregivers can record and delete taken doses for authorized patients without errors in 100% of test runs.
