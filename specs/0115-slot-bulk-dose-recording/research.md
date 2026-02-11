# Research: Slot Bulk Dose Recording (0115)

## Decision: Bulk record atomicity approach

- **Decision**: Use Prisma `$transaction` with a batch of `upsert` operations on `DoseRecord`. Side effects (DoseRecordEvent, push notifications, inventory deltas) fire after the transaction completes, outside the transaction boundary.
- **Rationale**: The `$transaction([...])` batch form ensures all upserts succeed or all roll back. The existing unique constraint `[patientId, medicationId, scheduledAt]` makes each upsert naturally idempotent. Keeping side effects outside the transaction keeps the DB write fast and avoids holding the transaction open for network calls (push notifications) or secondary writes (events, inventory).
- **Alternatives considered**:
  - Sequential `createDoseRecordIdempotent` calls: not atomic — if one call fails mid-loop, earlier records persist while later ones don't.
  - Single raw SQL batch: loses Prisma type safety and requires manual SQL construction.
  - Wrapping side effects inside the transaction: risks long-held transactions and push notification failures rolling back dose records.

## Decision: Slot time source of truth

- **Decision**: Use `scheduledAt` from generated scheduled doses (via `generateScheduleForPatientWithStatus`) as the source of truth for slot time display, MISSED threshold, and withinTime calculation. The iOS app passes custom slot times as query parameters (existing `parseSlotTimesFromParams` pattern) to the backend API for slot resolution.
- **Rationale**: The `scheduledAt` already reflects the patient's regimen times as configured in the medication regimen. Custom slot times from `NotificationPreferencesStore` on iOS are passed via query params (`morningTime`, `noonTime`, `eveningTime`, `bedtimeTime`) and used by `resolveSlot()` to assign doses to slots. This maintains consistency between iOS display and backend logic without introducing server-side slot time storage.
- **Alternatives considered**:
  - Server-side slot time storage per patient: adds a new database table and sync logic, over-engineered for MVP where regimen times already encode the patient's schedule.
  - Hardcoded default slot times: violates the requirement that slot times are per-patient configurable.

## Decision: Side effects for bulk records

- **Decision**: After the transaction succeeds, loop through newly created records and fire `createDoseRecordEvent` + `notifyCaregiversOfDoseRecord` + `applyInventoryDeltaForDoseRecord` per record. Push notifications use the existing fire-and-forget pattern.
- **Rationale**: Reuses the exact same side-effect pipeline as single-record creation from `doseRecordService.ts`. Events and inventory adjustments are per-medication (each medication has its own `doseCountPerIntake` for inventory delta), so they must fire per record. Push notifications could be batched per `recordingGroupId` in a future enhancement.
- **Alternatives considered**:
  - Single combined notification per bulk operation: better UX for caregivers but out of scope for MVP. The `recordingGroupId` field lays the groundwork for this optimization.
  - Skip side effects entirely: violates existing behavior contracts (events, inventory, push).

## Decision: recordingGroupId implementation

- **Decision**: Add `recordingGroupId String?` column to `DoseRecord` in the Prisma schema. Generate a UUID in the service layer and attach it to all records created in a bulk operation. The field is nullable to preserve backward compatibility with existing single-record creation flows.
- **Rationale**: Enables downstream features (grouped push notifications, PDF slot-level annotation, batch banners) to identify records from the same bulk action. No new index is needed since the field is not used for filtering in critical queries.
- **Alternatives considered**:
  - Separate junction table linking a group UUID to dose record IDs: over-engineered for a simple grouping concern.
  - Derive grouping from `takenAt` proximity: fragile and cannot distinguish between concurrent single-record and bulk-record flows.

## Decision: Endpoint path

- **Decision**: `POST /api/patient/dose-records/slot` under the existing patient route namespace.
- **Rationale**: Follows the existing convention where patient endpoints live under `/api/patient/` (e.g., `/api/patient/dose-records`, `/api/patient/today`). The `/slot` suffix clearly indicates this is a slot-level bulk operation. Auth via `requirePatient` is consistent with other patient endpoints.
- **Alternatives considered**:
  - `POST /api/dose-records/slot`: breaks the `/api/patient/` namespace convention and would require additional auth routing logic.
  - `POST /api/patient/today/record-slot`: mixes the Today view concept with the recording action; endpoints should represent resources, not UI screens.

## Decision: Response shape

- **Decision**: Return `{ updatedCount, remainingCount, totalPills, medCount, slotTime, slotSummary, recordingGroupId }`.
- **Rationale**:
  - `updatedCount`: tells the client how many records were newly created (0 for idempotent repeat calls).
  - `remainingCount`: PENDING+MISSED count after the update; used for button state on the client.
  - `totalPills` (Y) and `medCount` (N): allow the client to display success feedback matching the slot card summary format ("合計Y錠（N種類）").
  - `slotTime` (HH:mm): the resolved slot time for the target slot, derived from the first dose's `scheduledAt` or the custom slot time parameter; ensures UI display consistency.
  - `slotSummary`: reuses the existing `buildSlotSummary` format for the full day, allowing the iOS client to update all slot badges without a separate refresh.
  - `recordingGroupId`: the UUID for the bulk operation group, returned for client logging/tracking.
- **Alternatives considered**:
  - Returning full dose records: too verbose for a bulk operation and the client already has the schedule data.
  - Omitting `slotSummary`: would require the client to make a separate Today refresh call to update all slot badges.
