# Research: Free Limit Gates (009)

## Research Summary

Feature 009 builds directly on the billing foundation (008) with minimal unknowns. All decisions below were resolved through codebase inspection — no external research or prototyping was required.

## Decision 1: Gate Trigger Point

**Decision**: The single gate point is `POST /api/patients`, specifically the `createPatientForCaregiver()` function in `api/src/services/linkingService.ts`.

**Rationale**: This is the only operation that creates a new `CaregiverPatientLink` record (and therefore increases the linked-patient count). The function creates both the `Patient` record and the `CaregiverPatientLink` in a single Prisma transaction.

**Alternatives considered**:
- Gate at linking code issuance (`POST /api/patients/{id}/linking-codes`): Rejected — this endpoint only generates a code for an already-linked patient. It does not create new links.
- Gate at code exchange (`POST /api/patient/link`): Rejected — this endpoint is called by the patient device, creates a `PatientSession`, and does not create a new `CaregiverPatientLink`.
- Gate at a separate "link patient" operation: Rejected — no such operation exists. The app model has a 1-patient-to-1-caregiver constraint enforced by a unique index on `CaregiverPatientLink.patientId`.

## Decision 2: Count Query (Active Links Only)

**Decision**: Count linked patients using `CaregiverPatientLink.count({ where: { caregiverId, status: "ACTIVE" } })`. Revoked links are excluded.

**Rationale**: The spec (FR-012) requires that only ACTIVE links count toward the limit. A caregiver who revokes a patient and then adds a new one should be allowed if their active count drops below the limit. The `CaregiverPatientLink` model has a `status` field with `ACTIVE` and `REVOKED` enum values, and a `revokedAt` timestamp.

**Alternatives considered**:
- Count `Patient` records by `caregiverId`: Rejected — `Patient.caregiverId` is set at creation and never cleared on revocation. This would count revoked patients toward the limit.
- Use `Patient.count` with a join to active links: Unnecessarily complex; direct count on `CaregiverPatientLink` is simpler.

## Decision 3: Race Condition Prevention

**Decision**: Place the count check and patient creation inside a single Prisma interactive `$transaction`. The count query and conditional insert are atomic.

**Rationale**: Prisma interactive transactions use database-level serialisation. If two concurrent requests arrive for the same caregiver, the second transaction will see the committed result of the first (the count will reflect the newly created link), preventing both from succeeding.

**Alternatives considered**:
- Application-level mutex (in-memory lock): Rejected — does not work across multiple server instances.
- Database advisory lock: Rejected — Prisma's interactive transaction already provides the necessary serialisation guarantees for this use case.
- Unique constraint trick: Not applicable — the constraint should be on "at most N links" which cannot be expressed as a simple unique index.

## Decision 4: Error Response Format

**Decision**: Return HTTP 403 with body `{ "code": "PATIENT_LIMIT_EXCEEDED", "message": "...", "limit": 1, "current": N }`.

**Rationale**: HTTP 403 is appropriate because this is an authorization/entitlement issue (the caregiver lacks the premium entitlement to perform this action). The custom body uses `code` (not `error`) to distinguish it from the generic error format `{ error: "forbidden", message: "..." }` used by `errorResponse()`. This allows the iOS client to unambiguously identify the limit error.

**Alternatives considered**:
- HTTP 409 Conflict: Rejected — 409 implies a resource state conflict (e.g., duplicate), not an entitlement-based restriction.
- HTTP 402 Payment Required: Rejected — while semantically fitting, 402 is officially "reserved for future use" and not consistently supported by HTTP clients.
- Reuse existing `errorResponse()` mapping: Rejected — the generic mapper produces `{ error: "forbidden" }` which is indistinguishable from auth failures. The limit error needs additional fields (`code`, `limit`, `current`).

## Decision 5: iOS Error Differentiation (403 Handling)

**Decision**: In `APIClient.mapErrorIfNeeded()`, parse the 403 response body for `"code": "PATIENT_LIMIT_EXCEEDED"` BEFORE the generic 403 handler. If found, throw `APIError.patientLimitExceeded(limit:current:)` without calling `handleAuthFailure`.

**Rationale**: The current 403 handler calls `sessionStore.handleAuthFailure(for: sessionStore.mode)` which clears the session — this is incorrect for a limit error. The limit error must be intercepted earlier and mapped to a distinct `APIError` case so the UI can show the paywall instead of logging the user out.

**Alternatives considered**:
- Use a different HTTP status (to avoid the 403 conflict): Rejected — 403 is the semantically correct status and the body differentiation is clean.
- Return 403 with the generic format and differentiate by message string: Rejected — fragile; message strings may be localised.

## Decision 6: iOS Pre-flight Gate Location

**Decision**: Gate the "add patient" button action in `PatientManagementView.swift` (line 144). Before setting `showingCreate = true`, check entitlement state and patient count.

**Rationale**: This prevents the `PatientCreateView` sheet from opening at all when the user is blocked, and prevents any network call (FR-005). The patient count is already available from `viewModel.patients` (loaded on view appear).

**Alternatives considered**:
- Gate inside `PatientCreateView` (after form submission): Rejected — poor UX; the user fills out a form only to be blocked.
- Gate only on API response (server fallback): Rejected — spec FR-005 explicitly requires a local pre-flight check. However, the server fallback is retained as a safety net (if the API returns `PATIENT_LIMIT_EXCEEDED`, the UI shows the paywall).

## Decision 7: Paywall Reuse

**Decision**: Reuse the existing `PaywallView` as-is for MVP. The existing paywall description (key `billing.paywall.description`) already mentions "複数患者の登録" as a premium benefit.

**Rationale**: The existing copy covers the use case. Adding a context-specific wrapper would add complexity with minimal UX benefit for MVP.

**Alternatives considered**:
- Create a separate `PatientLimitPaywallView` with custom title/body: Rejected for MVP — can be revisited when more gates are active.
- Pass a "reason" enum to `PaywallView` to customise copy: Viable future enhancement but not needed now.
