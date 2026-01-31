# Data Model: Medication Regimen (001)

## Entities

### Medication

- id (UUID)
- patientId (UUID)
- name (string)
- dosageText (string, display-only)
- doseCountPerIntake (int)
- dosageStrengthValue (number)
- dosageStrengthUnit (string)
- notes (string, optional)
- startDate (date, required)
- endDate (date, optional)
- inventoryCount (int, optional)
- inventoryUnit (string, optional)
- isActive (boolean)
- isArchived (boolean)
- createdAt / updatedAt (timestamp)

### Regimen

- id (UUID)
- patientId (UUID)
- medicationId (UUID)
- timezone (string, IANA TZ)
- startDate (date, required)
- endDate (date, optional)
- times (array of `HH:mm` strings, min 1, unique)
- daysOfWeek (array of enum, optional; if empty => every day)
- enabled (boolean)
- createdAt / updatedAt (timestamp)

### Scheduled Dose (derived, not stored)

- patientId
- medicationId
- scheduledAt (datetime, seconds=0, timezone-aware)
- medicationSnapshot (name, dosage, doseCountPerIntake, dosageStrength)

## Relationships

- Medication 1..n Regimen
- Caregiver 1..n Patient Link
- Patient 1..n Medication

## Validation Rules

- `times` must be valid `HH:mm`, unique, and length >= 1.
- `startDate <= endDate` when endDate exists.
- Regimen date range is canonical; Medication dates must not conflict.
- `enabled` false excludes schedule generation; `isArchived` excludes list/schedules.
- Concurrent updates return 409 (optimistic concurrency).

## Indexes (proposed)

- Medication: `(patientId, isActive)`, `(patientId, startDate, endDate)`
- Regimen: `(patientId, medicationId, enabled)`, `(patientId, startDate, endDate)`
