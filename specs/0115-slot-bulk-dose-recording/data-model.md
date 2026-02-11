# Data Model: Slot Bulk Dose Recording (0115)

## Entities

### DoseRecord (modified)

Existing fields (unchanged):
- id (UUID, PK)
- patientId (UUID, required)
- medicationId (UUID, required)
- scheduledAt (timestamp, required)
- takenAt (timestamp, required; server time at creation)
- recordedByType (enum: PATIENT, CAREGIVER)
- recordedById (string, optional; caregiver only)
- createdAt / updatedAt (timestamp)

New field:
- **recordingGroupId** (UUID, nullable) — Groups dose records created in a single bulk recording operation. Null for records created via the existing single-record endpoint.

### ScheduledDose (derived, unchanged)

- patientId (UUID)
- medicationId (UUID)
- scheduledAt (timestamp)
- effectiveStatus (enum: pending, taken, missed)
- medicationSnapshot:
  - name (string)
  - dosageText (string)
  - doseCountPerIntake (number)
  - dosageStrengthValue (number)
  - dosageStrengthUnit (string)
  - notes (string, optional)

### SlotCard (UI-only, not persisted)

A computed grouping of scheduled doses for one time slot on a given date:
- slot (enum: morning, noon, evening, bedtime)
- slotTime (HH:mm, derived from first dose's scheduledAt or custom slot time)
- doses (array of ScheduledDose)
- aggregateStatus (enum: pending, taken, missed, none — worst-case across doses)
- totalPills (number — sum of doseCountPerIntake across all doses in the slot)
- medCount (number — count of distinct medications in the slot)
- remainingCount (number — count of doses with status PENDING or MISSED)

### RecordingGroup (logical, not a separate table)

- recordingGroupId (UUID) — shared across all DoseRecord rows created in one bulk operation
- No separate table; the grouping is implicit via the `recordingGroupId` column on DoseRecord

## Relationships

- Patient 1..n DoseRecord (unchanged)
- Medication 1..n DoseRecord (unchanged)
- DoseRecord optionally belongs to a RecordingGroup via `recordingGroupId`
- SlotCard is derived from ScheduledDose by resolving each dose's `scheduledAt` to a slot via `resolveSlot()`

## Validation Rules

- DoseRecord is unique on (patientId, medicationId, scheduledAt) — enforced by database constraint.
- takenAt is set by the server at creation time.
- recordedByType = PATIENT for bulk records; recordedById is null.
- recordingGroupId is a valid UUID when set; null for single-record creation flows.
- Bulk record request requires: `date` (YYYY-MM-DD), `slot` (morning|noon|evening|bedtime).
- Custom slot times (query params) must match HH:MM format between 00:00 and 23:59.

## Indexes

Existing (unchanged):
- DoseRecord: unique(patientId, medicationId, scheduledAt)
- DoseRecord: index(patientId, scheduledAt)

New:
- None. `recordingGroupId` is not indexed because it is only used for display/grouping, not for query filtering in hot paths.

## State Transitions

- ScheduledDose: pending → taken (bulk record creates DoseRecord for all PENDING doses in slot)
- ScheduledDose: missed → taken (bulk record creates DoseRecord for all MISSED doses in slot; withinTime=false)
- ScheduledDose: taken → (no change) (bulk record skips doses already TAKEN; idempotent)
- ScheduledDose: taken → pending/missed (caregiver deletes a record; existing behavior, unchanged)

## withinTime Derivation

For each dose updated in a bulk record:
```
withinTime = takenAt <= scheduledAt + 60 minutes
```
- `takenAt` is the server time at bulk record execution.
- `scheduledAt` is from the generated scheduled dose, reflecting the patient's regimen time.
- A dose that was MISSED (scheduledAt + 60m already passed) will have `withinTime = false`.
- A dose that was PENDING (scheduledAt + 60m has not passed) will have `withinTime = true`.
