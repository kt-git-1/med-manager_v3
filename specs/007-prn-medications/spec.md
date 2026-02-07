# Feature Specification: PRN Medications

**Feature Branch**: `007-prn-medications`  
**Created**: 2026-02-07  
**Status**: Draft  
**Input**: User description: "Feature ID: 007 Feature slug: 007-prn-medications Output: specs/007-prn-medications/spec.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create PRN medications (Priority: P1)

As a caregiver, I can register and edit PRN medications so the patient can record them only when needed.

**Why this priority**: Caregiver setup is required before any patient action is possible.

**Independent Test**: Can be fully tested by creating a PRN medication and confirming its fields and badges appear in the caregiver list.

**Acceptance Scenarios**:

1. **Given** a caregiver is adding a medication, **When** they mark it as PRN, **Then** schedule inputs are hidden/disabled and PRN instructions can be entered optionally.
2. **Given** a caregiver is editing an existing medication, **When** they switch PRN on or off, **Then** the schedule requirement updates accordingly and the change saves successfully.

---

### User Story 2 - Record a PRN dose (Priority: P2)

As a patient, I can record that I took a PRN medication from the Today screen.

**Why this priority**: Recording PRN doses is the core patient-facing outcome of the feature.

**Independent Test**: Can be fully tested by selecting a PRN medication on Today, confirming the action, and seeing a record created without any undo option.

**Acceptance Scenarios**:

1. **Given** a patient views Today, **When** they confirm "I took it" for a PRN medication, **Then** a PRN record is created and a success message is shown.
2. **Given** a patient completes a PRN record, **When** they look for a cancel/undo action, **Then** no cancellation control is available to them.

---

### User Story 3 - Review PRN history (Priority: P3)

As a caregiver or patient, I can see PRN records in daily history so past usage is visible alongside scheduled doses.

**Why this priority**: History provides context and accountability but is secondary to creation.

**Independent Test**: Can be fully tested by creating PRN records and verifying they appear in day history with time and medication name.

**Acceptance Scenarios**:

1. **Given** a PRN record exists for a day, **When** a user views that day's history, **Then** the PRN entry appears in time order with medication name and time.

---

### Edge Cases

- What happens when a patient attempts to record a dose for a non-PRN medication?
- How does the system handle PRN recording when inventory tracking is disabled?
- What happens when a caregiver tries to delete a PRN record they do not own or is not linked to the patient?
- How does the system handle double-tap or repeated submissions of the same PRN action?
- What happens when a PRN record is deleted and inventory would exceed the starting quantity?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow caregivers to mark a medication as PRN and optionally add PRN instructions.
- **FR-002**: System MUST require a schedule when PRN is off and MUST NOT require a schedule when PRN is on.
- **FR-003**: System MUST display a medication type badge in caregiver lists, showing "scheduled" for non-PRN and "PRN" for PRN medications.
- **FR-004**: System MUST show a PRN section on the patient Today screen listing PRN medications with name and dose/notes.
- **FR-005**: System MUST allow patients to create a PRN record via a confirmation step from Today.
- **FR-006**: System MUST allow linked caregivers to create and delete PRN records; patients MUST be blocked from deleting PRN records.
- **FR-007**: System MUST prevent PRN records from being created for non-PRN medications.
- **FR-008**: System MUST exclude PRN medications from any reminder notification scheduling.
- **FR-009**: System MUST include PRN records in daily history views with time and medication name.
- **FR-010**: When inventory tracking is enabled for a medication, system MUST decrement stock on PRN record creation and increment stock on caregiver deletion.
- **FR-011**: System MUST block interaction during network sync using the global full-screen "updating" overlay.
- **FR-012**: System MUST record whether a PRN record was created by a patient or caregiver for auditing and display logic.

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Performance)**: PRN record creation and confirmation feedback MUST complete within 3 seconds for 95% of attempts under normal conditions.
- **NFR-002 (Security/Privacy)**: PRN record creation and deletion MUST enforce patient linkage, and non-linked caregivers MUST receive a not-found response behavior.
- **NFR-003 (UX/Accessibility)**: Confirmation, error, and success messages MUST be understandable without medical jargon and support localization.
- **NFR-004 (Documentation/Operations)**: Product documentation MUST include how to create PRN medications, record PRN doses, cancel PRN records (caregiver), and inventory effects.

### Key Entities *(include if feature involves data)*

- **Medication**: A patient medication that can be scheduled or PRN, including optional PRN instructions and inventory tracking settings.
- **PRN Dose Record**: A timestamped record of a PRN medication taken, including who recorded it and the quantity taken.
- **Inventory Status**: The current quantity and low/out state for a medication when inventory tracking is enabled.

### Assumptions

- A PRN record defaults to the medication's standard per-intake quantity unless explicitly adjusted.
- PRN medications are visible to both caregiver and patient roles once created.
- History views already exist and can accept PRN entries without changing navigation structure.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95% of caregivers can create or update a PRN medication in under 2 minutes without external help.
- **SC-002**: 95% of patient PRN recordings complete with a single confirmation and no duplicate records.
- **SC-003**: 100% of PRN records created by patients are not deletable by the patient role.
- **SC-004**: Daily history pages show PRN entries for the correct date in at least 99% of reviewed cases.
