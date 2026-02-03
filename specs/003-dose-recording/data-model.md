# Data Model: Dose Recording (003)

## Entities

### DoseRecord

- id (UUID)
- patientId (UUID, required)
- medicationId (UUID, required)
- scheduledAt (timestamp, required)
- takenAt (timestamp, required; server time)
- recordedByType (enum: patient, caregiver)
- recordedById (string, optional; caregiver only)
- createdAt / updatedAt (timestamp)

### ScheduledDose (derived)

- patientId (UUID)
- medicationId (UUID)
- scheduledAt (timestamp)
- effectiveStatus (enum: pending, taken, missed)
- display fields (medication name, dosage text, dose count per intake)

## Relationships

- Patient 1..n DoseRecord
- Medication 1..n DoseRecord
- ScheduledDose is derived from regimen schedule and joined with DoseRecord by (patientId, medicationId, scheduledAt).

## Validation Rules

- DoseRecord is unique on (patientId, medicationId, scheduledAt).
- takenAt is set by the server at creation.
- recordedByType = patient requires recordedById to be null.
- recordedByType = caregiver requires recordedById to be present.
- effectiveStatus is:
  - taken if a DoseRecord exists
  - missed if now > scheduledAt + 60 minutes and no DoseRecord exists
  - pending otherwise

## Indexes (proposed)

- DoseRecord: unique(patientId, medicationId, scheduledAt)
- DoseRecord: index(patientId, scheduledAt)

## State Transitions

- ScheduledDose: pending → taken (DoseRecord created)
- ScheduledDose: pending → missed (time passes threshold without DoseRecord)
- ScheduledDose: taken → pending/missed (caregiver delete; depends on time)
