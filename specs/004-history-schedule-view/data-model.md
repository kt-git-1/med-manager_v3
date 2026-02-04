# Data Model: History Schedule View (004)

## Entities

### Scheduled Dose

- **Fields**: patientId, medicationId, scheduledAt, slot, medicationName, dosageText, doseCountPerIntake
- **Notes**: Produced by regimen schedule generator; used for both month and day views.

### Dose Record

- **Fields**: patientId, medicationId, scheduledAt, takenAt, recordedByType
- **Notes**: Only TAKEN is persisted; other statuses are derived.

### History Day Detail Item

- **Fields**: date, slot, scheduledAt, medicationName, dosageText, doseCountPerIntake, effectiveStatus
- **Ordering**: slot (morning → noon → evening → bedtime), then medicationName (A–Z).

### History Day Summary

- **Fields**: date, slotSummary (morning/noon/evening/bedtime = taken/missed/pending/none)
- **Aggregation**: For each slot, MISSED overrides PENDING, PENDING overrides TAKEN. If no doses in slot, status is NONE.

## Derived Status Rules

- **TAKEN**: a Dose Record exists for the scheduled dose.
- **MISSED**: no Dose Record and now > scheduledAt + 60 minutes.
- **PENDING**: otherwise (including future scheduled doses).

## Relationships

- Scheduled Dose + Dose Record (by patientId, medicationId, scheduledAt) → Effective Status.
- History Day Summary aggregates Scheduled Doses by date and slot.

## Constraints

- Month boundaries are interpreted in Asia/Tokyo (MVP).
- Navigation range is last 3 months including current; future days shown to month-end.
