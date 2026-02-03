# Research: Dose Recording (003)

## Decision: Idempotent dose recording

- **Decision**: Enforce a unique key on (patientId, medicationId, scheduledAt) and treat repeated create requests as success that returns the existing record.
- **Rationale**: Protects against double-taps and retries while keeping the UI consistent.
- **Alternatives considered**:
  - Reject duplicates with a conflict error (worse UX; harder to recover).

## Decision: Missed status derivation

- **Decision**: Missed is derived when now is more than 60 minutes after scheduled time with no taken record; no missed records are stored.
- **Rationale**: Matches MVP requirement to persist only taken records and reduces storage/cleanup needs.
- **Alternatives considered**:
  - Persist missed records (adds lifecycle complexity and backfill logic).

## Decision: Reminder delivery strategy (iOS)

- **Decision**: Schedule local notifications for today’s scheduled doses on refresh and app foreground, limited to two notifications per dose.
- **Rationale**: Meets the reminder requirement without server-side push dependencies in MVP.
- **Alternatives considered**:
  - Server-driven push notifications (more infrastructure and permissions).

## Decision: Caregiver access concealment

- **Decision**: Return not found for caregiver requests to non-owned patients.
- **Rationale**: Prevents leaking patient existence and aligns with domain policy.
- **Alternatives considered**:
  - Forbidden responses (reveals resource existence).
