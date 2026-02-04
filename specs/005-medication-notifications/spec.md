# Feature Specification: Medication Notifications

**Feature Branch**: `005-medication-notifications`  
**Created**: 2026-02-04  
**Status**: Draft  
**Input**: User description: "Medication reminders with patient settings, tap routing, and caregiver in-app banner using the defined scheduling rules."

## Clarifications

### Session 2026-02-04

- Q: What are the default reminder settings on first use? → A: Master OFF, all slot toggles ON.
- Q: What happens when a reminder fires while the app is already open? → A: Show a brief in-app banner; highlight the slot if the user is on Today.
- Q: If multiple qualifying dose events arrive while caregiver app is foregrounded, how should banners behave? → A: Queue banners and show each for about 3 seconds in arrival order.
- Q: What should happen when a reminder is tapped but the slot is no longer pending? → A: Open Today, scroll to the slot, and show a brief “already recorded” message.
- Q: What should happen when no pending slots exist in the 7-day window? → A: Schedule no reminders and show a brief “no scheduled reminders” note in Settings.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receive and act on reminders (Priority: P1)

As a patient, I receive timely reminders for each scheduled slot and can quickly navigate to the correct section to record a dose.

**Why this priority**: This is the primary value of the feature and directly supports on-time medication adherence.

**Independent Test**: Enable reminders, simulate a reminder for a pending slot, tap it, and verify the Today view navigates and highlights the correct slot.

**Acceptance Scenarios**:

1. **Given** a pending slot for today within the next 7 days, **When** the reminder fires, **Then** a notification is shown with the slot-specific message.
2. **Given** a patient taps a reminder, **When** the app opens, **Then** the Today view is focused and the related slot is highlighted for a few seconds.

---

### User Story 2 - Manage reminder settings (Priority: P2)

As a patient, I can enable or disable reminders globally and per slot, and choose whether to receive a second reminder.

**Why this priority**: Users need control and consent for reminders to avoid notification fatigue.

**Independent Test**: Toggle settings in the Settings tab and verify that reminder scheduling updates accordingly.

**Acceptance Scenarios**:

1. **Given** reminders are enabled, **When** the user disables a slot toggle, **Then** reminders for that slot are no longer scheduled.
2. **Given** notifications are denied at the system level, **When** the Settings tab is opened, **Then** guidance is shown and reminder toggles are disabled.

---

### User Story 3 - Caregiver receives in-app confirmation (Priority: P3)

As a caregiver, I see a brief in-app banner when a patient records a dose within the allowed time window.

**Why this priority**: Caregivers need timely feedback without relying on push notifications or background jobs.

**Independent Test**: Record a qualifying dose and verify a banner appears on any caregiver screen while the app is in the foreground.

**Acceptance Scenarios**:

1. **Given** a patient records a dose within the defined time window, **When** the caregiver app is foregrounded, **Then** a banner appears for about 3 seconds with the patient name.

---

### Edge Cases

- What happens when the 7-day scheduling window crosses a month boundary?
- When no pending slots exist in the 7-day window, no reminders are scheduled and a brief “no scheduled reminders” note appears in Settings.
- When a reminder fires while the app is open, an in-app banner appears; if on Today, the related slot is highlighted.
- If a reminder is tapped after the slot is no longer pending, the app opens Today, scrolls to the slot, and shows a brief “already recorded” message.
- What happens when a scheduling refresh fails while the app is in the foreground?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST build a rolling 7-day reminder plan (today through today+6) using the slot summary as the source of truth.
- **FR-002**: System MUST schedule reminders only for slots with a status of PENDING.
- **FR-003**: System MUST treat each reminder unit as a unique date + slot combination, not per medication.
- **FR-004**: System MUST support up to two reminders per slot: one at slot time and an optional second reminder 15 minutes later.
- **FR-005**: System MUST use stable identifiers for reminders in the format `notif:{YYYY-MM-DD}:{slot}:{1|2}`.
- **FR-006**: System MUST present a master reminder toggle, per-slot toggles (morning/noon/evening/bedtime), and a re-reminder toggle in the patient Settings tab.
- **FR-006a**: On first use, the master toggle MUST be OFF while all slot toggles default to ON.
- **FR-007**: When system notification permission is denied, the Settings tab MUST show guidance and disable reminder toggles.
- **FR-008**: Tapping a reminder MUST open the Today view, scroll to the related slot, and highlight it for a few seconds.
- **FR-009**: The reminder message MUST be fixed by slot: "朝のお薬の時間です", "昼のお薬の時間です", "夜のお薬の時間です", "眠前のお薬の時間です".
- **FR-010**: The reminder plan MUST refresh automatically on app launch, app foreground return, settings changes, and after a successful dose record.
- **FR-011**: After a successful dose record, the system MUST refresh today’s status and cancel secondary reminders for the same date+slot when no pending doses remain.
- **FR-012**: When a dose is recorded within the time window, the caregiver app MUST show a banner for about 3 seconds with "{displayName}さんが時間内に薬を服用しました！" while foregrounded.
- **FR-013**: The within-time window MUST be defined as takenAt less than or equal to scheduledAt + 60 minutes.
- **FR-014**: If the app is already open when a reminder fires, the app MUST show a brief in-app banner; if the user is on the Today view, the related slot MUST be highlighted.
- **FR-015**: When multiple qualifying dose events arrive while the caregiver app is foregrounded, banners MUST be queued and shown sequentially for about 3 seconds each in arrival order.
- **FR-016**: If a reminder is tapped after the slot is no longer pending, the app MUST open Today, scroll to the slot, and show a brief “already recorded” message.
- **FR-017**: When there are no pending slots in the 7-day window, the system MUST schedule no reminders and show a brief “no scheduled reminders” note in Settings.

### Assumptions & Dependencies

- Fixed slot times for MVP are morning/noon/evening/bedtime at 08:00/12:00/19:00/22:00 in Asia/Tokyo.
- Patient access uses the existing patient session model and non-owned resources remain concealed.
- The daily/monthly slot summary is available and reflects TAKEN/MISSED/PENDING/NONE.
- No server-side scheduled jobs are used; reminders are scheduled from the client when the app is in use.

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Performance)**: Foreground scheduling refresh MUST complete within 10 seconds for the 7-day window under normal conditions.
- **NFR-002 (Security/Privacy)**: Caregivers MUST only see in-app banners for patients they are authorized to access.
- **NFR-003 (UX/Accessibility)**: Any foreground scheduling refresh MUST show a full-screen "更新中" overlay that blocks interactions; on failure, the overlay is removed and a retry is offered.
- **NFR-004 (Documentation/Operations)**: Patient-facing help content MUST describe how to enable reminders and what each slot message means.

### Key Entities *(include if feature involves data)*

- **Reminder Plan**: A 7-day set of reminders keyed by date and slot, including primary and optional secondary reminders.
- **Notification Preference**: Patient choices for master enablement, per-slot enablement, and re-reminder.
- **Dose Event**: A record that a dose was taken, including scheduled time, actual time, and patient identity for caregiver visibility.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95% of reminder scheduling refreshes complete within 10 seconds while the app is foregrounded.
- **SC-002**: 95% of reminder taps navigate to the correct slot and highlight it within 2 seconds.
- **SC-003**: 90% of patients who enable reminders receive notifications for all pending slots within the 7-day window.
- **SC-004**: 95% of caregiver banner events appear within 5 seconds of a qualifying dose record while the caregiver app is foregrounded.
