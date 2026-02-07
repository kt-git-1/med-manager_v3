# Feature Specification: Inventory Tracking

**Feature Branch**: `006-inventory-tracking`  
**Created**: Feb 6, 2026  
**Status**: Draft  
**Input**: User description: "Caregiver-only inventory tracking with automatic adjustments from dose records and realtime low/out alerts, without scheduled jobs."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage medication inventory (Priority: P1)

Caregivers can view and manage inventory per medication for a selected patient from a dedicated Inventory tab, including enabling inventory, setting quantity, and setting a low-stock threshold.

**Why this priority**: This is the core value of the MVP and enables caregivers to keep medications stocked.

**Independent Test**: Can be fully tested by selecting a patient, updating inventory values, and verifying the list and detail views reflect the updates.

**Acceptance Scenarios**:

1. **Given** no patient is selected, **When** the caregiver opens the Inventory tab, **Then** an empty state with a clear CTA to select/link a patient is shown.
2. **Given** a patient is selected, **When** the caregiver views the Inventory list, **Then** each medication shows its name, remaining quantity, and low/out badge if applicable.
3. **Given** a caregiver edits inventory settings for a medication, **When** the update succeeds, **Then** the new values appear in the list and detail views.
4. **Given** inventory is enabled with a valid schedule, **When** the caregiver views the list or detail, **Then** the list shows `あとX日` and the detail shows `あとX日分` with a `補充目安` date; if no plan is available, show `—`.

---

### User Story 2 - Inventory updates from dose records (Priority: P2)

Inventory automatically decreases when a dose is recorded as taken and is restored when a taken record is removed, without double-counting.

**Why this priority**: Automatic updates prevent manual errors and keep inventory accurate.

**Independent Test**: Can be fully tested by creating and deleting a taken record and observing the corresponding inventory changes.

**Acceptance Scenarios**:

1. **Given** inventory is enabled for a medication, **When** a new taken record is created, **Then** the remaining quantity decreases by the configured per-dose amount and never drops below zero.
2. **Given** a taken record is removed, **When** the deletion succeeds, **Then** the remaining quantity increases by the same per-dose amount.
3. **Given** an idempotent create that does not produce a new taken record, **When** the request completes, **Then** inventory does not change.

---

### User Story 3 - Realtime low/out alerts (Priority: P3)

Caregivers receive a transient banner when a medication transitions into low stock or out-of-stock status.

**Why this priority**: Timely alerts help prevent missed doses due to shortages.

**Independent Test**: Can be fully tested by changing inventory to cross the threshold and verifying a banner is shown.

**Acceptance Scenarios**:

1. **Given** a medication transitions from not-low to low, **When** the transition occurs, **Then** a low-stock banner appears to caregivers.
2. **Given** a medication transitions to out-of-stock, **When** the transition occurs, **Then** an out-of-stock banner appears to caregivers.
3. **Given** inventory changes without a low/out state transition, **When** updates occur, **Then** no new banner is shown.

---

### Edge Cases

- What happens when inventory is disabled for a medication that receives taken records?
- How does the system handle inventory reaching zero from a large per-dose decrement?
- What happens when the low-stock threshold is set to zero?
- How does the system handle caregivers attempting access to a non-owned patient?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a caregiver-only Inventory tab for each patient.
- **FR-002**: The system MUST show an empty state and CTA when no patient is selected.
- **FR-003**: The system MUST allow caregivers to enable/disable inventory tracking per medication.
- **FR-004**: The system MUST allow caregivers to set current quantity and low-stock threshold per medication.
- **FR-005**: The system MUST display remaining quantity and low/out status in the medication list.
- **FR-006**: The system MUST decrease inventory when a new taken record is created and increase inventory when a taken record is removed.
- **FR-007**: The system MUST prevent inventory from going below zero.
- **FR-008**: The system MUST avoid inventory changes on idempotent taken creations.
- **FR-009**: The system MUST emit a caregiver-visible alert only when inventory transitions into low or out-of-stock status.
- **FR-010**: The system MUST restrict inventory visibility and edits to caregivers only.
- **FR-011**: The system MUST conceal non-owned or non-linked patient access as not found.
- **FR-012**: The system MUST compute refill plan fields (`dailyPlannedUnits`, `daysRemaining`, `refillDueDate`) using Tokyo day boundaries.
- **FR-013**: The system MUST display `あとX日` in the list and `あとX日分` + `補充目安` in the detail, or `—` when any refill plan field is null.

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Performance)**: Inventory updates and list refreshes MUST be visible to caregivers within 5 seconds of a successful update.
- **NFR-002 (Security/Privacy)**: Unauthorized patient access MUST not reveal existence of the patient or their medications.
- **NFR-003 (UX/Accessibility)**: All inventory-related network actions MUST display a full-screen blocking "updating" overlay to prevent interactions during updates.
- **NFR-004 (Operations)**: Inventory alerts MUST be event-driven without relying on scheduled server jobs or always-on workers.
- **NFR-005 (Documentation)**: Specs documentation MUST include updated contracts, quickstart, and data model notes, explicitly stating the no-cron and realtime banner constraints.

### Assumptions & Dependencies

- Inventory quantities are tracked as whole-number counts, and per-dose decrement uses the configured per-intake count.
- Low-stock status is when remaining quantity is strictly below the configured threshold; out-of-stock is when remaining is zero.
- Inventory changes are ignored when inventory tracking is disabled for a medication.
- The existing caregiver banner system and patient-selection empty-state pattern are available for reuse.

### Testing Requirements

- Contract tests for inventory retrieval, update, and adjustment operations.
- Integration tests that validate decrement on new taken records, increment on deletion, and no double-decrement on idempotent creates.
- Authorization tests that verify non-owned patient access is concealed as not found.
- UI tests for Inventory tab empty state, patient-selected list/detail view, and blocking "updating" overlay.
- Realtime banner regression tests for low/out alert display using the existing caregiver banner system.

### Key Entities *(include if feature involves data)*

- **Medication Inventory Settings**: Per-medication values for enabled state, current quantity, and low-stock threshold.
- **Inventory Adjustment Record**: An audit-friendly record of manual or system-driven inventory changes.
- **Inventory Alert Event**: A record of low/out transitions used to notify caregivers in realtime.
- **Dose Record (Taken)**: A completed intake record that triggers automatic inventory updates.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95% of caregivers can update a medication's inventory from the Inventory tab in under 60 seconds.
- **SC-002**: 95% of low/out transitions surface a banner to caregivers within 5 seconds.
- **SC-003**: Inventory quantity never becomes negative after any sequence of taken creates/deletes.
- **SC-004**: 100% of unauthorized patient access attempts return a not-found outcome in tests.
