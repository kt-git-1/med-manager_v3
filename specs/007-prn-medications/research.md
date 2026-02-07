# Research: PRN Medications

## Decision 1: Separate PRN dose records from scheduled dose records

- **Decision**: Store PRN dose records in a dedicated table/entity rather than reuse scheduled dose records.
- **Rationale**: Scheduled records depend on `(patientId, medicationId, scheduledAt)` idempotency; PRN is free-form with `takenAt` and should not collide with scheduled logic.
- **Alternatives considered**:
  - Reuse scheduled dose records with a nullable scheduledAt (would break idempotency and scheduled reporting).
  - Store PRN as a special scheduled dose type (still conflicts with "no scheduled doses" requirement).

## Decision 2: Server-side `takenAt` for PRN creation

- **Decision**: Default `takenAt` to server time for PRN creation; accept client time only if explicitly provided and validated.
- **Rationale**: Avoids device clock drift and preserves ordering in history views.
- **Alternatives considered**:
  - Always trust client timestamps (risk inconsistent ordering).
  - Require client timestamps (adds friction, worse UX).

## Decision 3: Inventory adjustments triggered on PRN create/delete

- **Decision**: Adjust inventory only when a PRN record is created or deleted, following existing inventory rules and clamping at zero.
- **Rationale**: Keeps inventory aligned with actual PRN usage while avoiding double-decrement on retries.
- **Alternatives considered**:
  - Recompute inventory from all records on read (costly).
  - Background reconciliation (not allowed by constraints).

## Decision 4: History integration via day detail payload

- **Decision**: Extend the existing day detail response with PRN items (and optional month summary counts).
- **Rationale**: Keeps history UI consistent and avoids new endpoints.
- **Alternatives considered**:
  - Separate PRN history endpoints (more client complexity).
