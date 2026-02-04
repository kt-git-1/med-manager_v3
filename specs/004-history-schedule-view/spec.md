# Feature Specification: History Schedule View

**Feature Branch**: `004-history-schedule-view`  
**Created**: 2026-02-03  
**Status**: Draft  
**Input**: User description: "Add a History tab for patients and caregivers with a month calendar and day detail showing scheduled doses and derived status, with a blocking updating overlay and caregiver empty state when no patient is selected."

## Clarifications

### Session 2026-02-03

- Q: History navigation range (past) → A: Last 3 months (including current month)
- Q: Legend visibility → A: Always visible legend
- Q: Day detail ordering when multiple meds share a slot → A: Order by slot, then medication name (A–Z) within each slot
- Q: Error message copy for failed history load → A: 「読み込みに失敗しました。再試行してください。」
- Q: Empty state copy when no caregiver patient selected → A: 「患者を選択してください」

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View monthly history and day detail (Priority: P1)

As a patient, I can open the History tab to see a month calendar with status dots for each day and view the detailed list for a selected day, including future scheduled doses marked as pending.

**Why this priority**: This is the core value of the feature and the minimum usable history experience.

**Independent Test**: Open the History tab as a patient, inspect a month and a selected day, and confirm status indicators match the expected rules.

**Acceptance Scenarios**:

1. **Given** a signed-in patient with scheduled doses in the current month, **When** they open the History tab, **Then** the calendar shows all days of the month through month-end with status dots per slot for days that have schedules.
2. **Given** the patient selects a day with scheduled doses, **When** the day detail loads, **Then** the list shows each scheduled dose ordered by slot (morning, noon, evening, bedtime) with its status (taken, missed, or pending).
3. **Given** the patient selects a future day within the month, **When** the day detail loads, **Then** the scheduled doses are shown as pending.

---

### User Story 2 - View history for a linked patient (Priority: P2)

As a caregiver, I can view the same History tab for a selected patient, and I see a clear empty state when no patient is selected.

**Why this priority**: Caregivers need visibility into a patient's adherence while avoiding confusion when no patient is selected.

**Independent Test**: Open the History tab as a caregiver with and without a selected patient and validate the expected behavior.

**Acceptance Scenarios**:

1. **Given** a caregiver with a selected patient, **When** they open the History tab, **Then** they see the patient's month calendar and can open day details like a patient.
2. **Given** a caregiver with no selected patient, **When** they open the History tab, **Then** they see an empty state message and a call-to-action that navigates them to the Link/Patients tab.

---

### User Story 3 - Loading and retry behavior (Priority: P3)

As a user, I see a full-screen updating overlay during any history fetch, and I can retry if a request fails.

**Why this priority**: Clear loading and recovery prevents inconsistent state and protects users from partial data.

**Independent Test**: Trigger loading and error conditions for history data and verify that the overlay blocks interaction and retry recovers.

**Acceptance Scenarios**:

1. **Given** the app is fetching history data, **When** the overlay is visible, **Then** all interactions (tap/scroll) are disabled until loading completes.
2. **Given** a history request fails, **When** the error is shown, **Then** selecting retry re-shows the overlay and reloads the data.

---

### Edge Cases

- A caregiver opens History with no selected patient.
- A day has no scheduled doses in any slot.
- Multiple scheduled doses exist in the same slot on a day.
- A day crosses the 60-minute missed threshold while the app is in the foreground.
- A session becomes invalid while loading history data.
- The user switches months while a load is in progress.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow navigation across the last 3 months (including the current month) and show the selected month calendar including all remaining future days through month-end.
- **FR-002**: System MUST display up to four slot indicators per day in the order morning, noon, evening, bedtime, and hide indicators for slots with no scheduled doses.
- **FR-003**: System MUST aggregate multiple scheduled doses within the same slot using precedence: missed overrides pending, pending overrides taken.
- **FR-004**: Users MUST be able to select a day to view scheduled doses for that date, ordered by slot and then medication name (A–Z) within each slot, showing medication name, dosage, and status.
- **FR-005**: System MUST derive dose status as taken when a record exists, missed when no record exists and the current time is more than 60 minutes after the scheduled time, and pending otherwise.
- **FR-006**: System MUST allow access to patient history for the signed-in patient and authorized caregivers, and must conceal non-owned patient history as not found.
- **FR-007**: System MUST show a blocking, full-screen updating overlay for all history fetches (initial load, month change, day selection, foreground return, retry).
- **FR-008**: System MUST clear the overlay on failure, show the message 「読み込みに失敗しました。再試行してください。」, and allow retry that re-displays the overlay.
- **FR-009**: When no caregiver patient is selected, system MUST show an empty state with the message 「患者を選択してください」, a call-to-action to the Link/Patients tab, and no history data.
- **FR-010**: System MUST display an always-visible legend explaining the status dots on the history screen.
- **FR-011**: System MUST interpret month boundaries using the patient's local calendar (assumed Asia/Tokyo for MVP).

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Performance)**: History month data appears within 3 seconds for 95% of loads, and the updating overlay appears within 1 second of any request.
- **NFR-002 (Security/Privacy)**: Only authorized patient or caregiver users can access history data; non-owned patient requests return no identifiable data.
- **NFR-003 (UX/Accessibility)**: The overlay blocks all interaction; status indicators and legend provide readable labels for assistive technologies.
- **NFR-004 (Documentation/Operations)**: User-facing release notes or help content describe the History tab and status meanings.

### Key Entities *(include if feature involves data)*

- **Scheduled Dose**: A planned intake with a date, time slot, medication name, and dosage.
- **Dose Record**: Evidence that a scheduled dose was taken, used to derive status.
- **Daily Slot Summary**: Per-day summary of each slot's status (taken, missed, pending, or none).
- **History Day**: A calendar day with scheduled doses and slot summaries.
- **Patient**: The person whose schedule and history are displayed.

### Dependencies

- Medication regimen provides the scheduled doses and time slots.
- Dose recording provides taken records used to derive status.
- Family linking provides caregiver access control and the selected patient context.

### Scope Boundaries

- History view does not create, edit, or cancel dose records.
- No analytics reports, search, or filters in the History tab.
- PRN (as-needed) medication history is excluded from MVP.

### Assumptions

- Time slots are fixed to morning, noon, evening, and bedtime for MVP.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95% of users can locate a day's dose status within 30 seconds in a usability test.
- **SC-002**: 95% of History month views load and render within 3 seconds under typical conditions.
- **SC-003**: Caregivers with linked patients successfully access History without authorization errors in 99% of attempts, and non-owned patients receive no data.
- **SC-004**: 95% of simulated history-load failures recover successfully via retry without an app restart.
