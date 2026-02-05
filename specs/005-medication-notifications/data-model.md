# Data Model: Medication Notifications

## Entities

### NotificationPreference

- **id**: unique identifier (patient-scoped)
- **patientId**: linked patient
- **masterEnabled**: boolean (default false)
- **slotEnabled**: map of slot → boolean (morning/noon/evening/bedtime, default true)
- **rereminderEnabled**: boolean (default false)
- **updatedAt**: timestamp

### ReminderPlanEntry

- **id**: `notif:{YYYY-MM-DD}:{slot}:{1|2}`
- **date**: local date (Asia/Tokyo)
- **slot**: morning | noon | evening | bedtime
- **sequence**: 1 (primary) or 2 (secondary)
- **scheduledAt**: local datetime (slot time or +15 minutes)
- **status**: pending | taken | missed | none (derived from slot summary)

### DoseRecordEvent

- **id**: unique identifier
- **patientId**: linked patient
- **scheduledAt**: datetime of scheduled dose
- **takenAt**: datetime of record creation
- **withinTime**: boolean (takenAt <= scheduledAt + 60 minutes)
- **displayName**: patient display name (for banner)
- **createdAt**: timestamp

## Relationships

- **NotificationPreference** belongs to **Patient** (one per patient).
- **ReminderPlanEntry** is derived from slot summaries (no persistence required for MVP).
- **DoseRecordEvent** is created when a TAKEN record is persisted and is readable only by linked caregivers via RLS.

## Validation & Rules

- Reminder plan is computed for a 7-day rolling window (today..today+6, Asia/Tokyo).
- Only slots with slot summary == PENDING are scheduled.
- Secondary reminders are cancelled after TAKEN if no pending remain in the slot.
- Caregiver banner events are ignored when caregiver access is revoked or unauthorized.
