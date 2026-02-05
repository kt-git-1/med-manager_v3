# Research: Inventory Tracking

## Decision 1: Idempotent inventory adjustments on TAKEN create/delete

- **Decision**: Adjust inventory only when a new TAKEN record is created or a TAKEN record is successfully deleted; ignore idempotent create requests that do not create a new record.
- **Rationale**: Prevents double-decrement on retry/double-tap while keeping adjustments aligned with actual dose record persistence.
- **Alternatives considered**:
  - Recompute inventory by aggregating all taken records on read (heavier, slower, and not event-driven).
  - Use background jobs to reconcile inventory periodically (disallowed by no-cron constraint).

## Decision 2: Low/out alerts on state transitions only

- **Decision**: Emit an inventory alert event only when inventory state transitions into LOW or OUT (NONE -> LOW, NONE/LOW -> OUT).
- **Rationale**: Avoids alert spam while still notifying caregivers at the right time.
- **Alternatives considered**:
  - Emit alerts on every update while in low/out state (noisy).
  - Client-side detection with polling (inconsistent, violates realtime requirement).

## Decision 3: Realtime delivery via existing caregiver banner pipeline

- **Decision**: Reuse existing caregiver realtime subscription (CaregiverEventSubscriber) and GlobalBannerPresenter for inventory alert display.
- **Rationale**: Minimizes new UI infrastructure, matches established UX, and meets realtime requirement without push.
- **Alternatives considered**:
  - Push notifications (out of scope, patient-mode only).
  - App-wide polling (inconsistent, higher latency, unnecessary network use).
