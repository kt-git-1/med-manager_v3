# Research: History Retention Limit (010)

## Research Summary

Feature 010 builds on the billing foundation (008), the free limit gates pattern (009), and the history views (004). All decisions below were resolved through codebase inspection — no external research or prototyping was required.

## Decision 1: Cutoff Date Calculation

**Decision**: Compute `cutoffDate = todayTokyo - 29 days` (inclusive) server-side in Asia/Tokyo, using the same timezone handling already present in the history routes (`historyTimeZone = "Asia/Tokyo"`). Define `RETENTION_DAYS_FREE = 30` as a named constant.

**Rationale**: The spec defines a 30-day viewable window: `[cutoffDate, todayTokyo]` inclusive on both ends. Subtracting 29 days from today gives exactly 30 viewable days. The Asia/Tokyo timezone is already hardcoded in all 4 history route files (`api/app/api/patient/history/month/route.ts` etc.) and in `api/src/constants.ts` as `DEFAULT_TIMEZONE`. Using a named constant keeps the limit adjustable for future plan tiers.

**Alternatives considered**:
- UTC-based cutoff: Rejected — the spec explicitly requires Asia/Tokyo, and all existing history date calculations already use this timezone.
- Client-side cutoff calculation: Rejected — the spec mandates server-side enforcement (NFR-001). The client may display a local cutoff for UX but the server is authoritative.
- Dynamic retention per plan tier (database-stored): Over-engineered for MVP — a constant is sufficient.

## Decision 2: Premium Resolution for Caregiver Sessions

**Decision**: Query `CaregiverEntitlement` with `{ where: { caregiverId: session.caregiverUserId, status: "ACTIVE" } }` using `prisma.caregiverEntitlement.findFirst()`. Returns non-null if premium.

**Rationale**: This is the exact same pattern used in `linkingService.ts` (lines 53-55) for the patient limit gate in feature 009. The entitlement check is read-only and does not require a transaction.

**Alternatives considered**:
- Use `getEntitlements()` from `entitlementService.ts`: Returns more data than needed (full entitlement list). A simple `findFirst` for an ACTIVE record is more efficient.
- Cache entitlement status: Not needed for MVP — single query per request is fast enough.

## Decision 3: Premium Resolution for Patient Sessions

**Decision**: For patient sessions, query `CaregiverPatientLink` with `{ where: { patientId: session.patientId, status: "ACTIVE" } }` to find the linked `caregiverId`, then query `CaregiverEntitlement` with `{ where: { caregiverId, status: "ACTIVE" } }`.

**Rationale**: The spec requires that a patient linked to a premium caregiver inherits premium access (FR-007). The `CaregiverPatientLink` model has a unique constraint on `patientId`, enforcing the 1:1 relationship from feature 002. This two-step lookup is simple, read-only, and requires no transaction.

**Alternatives considered**:
- Use `Patient.caregiverId` directly: Rejected — `Patient.caregiverId` is set at creation time and is not updated on link revocation. Using `CaregiverPatientLink` with `status: "ACTIVE"` correctly reflects the current link state.
- Join query (Prisma `include`): Could combine into one query, but two sequential queries are clearer and easier to test independently.
- Cache patient-caregiver mapping: Not needed for MVP — two queries per request is acceptable.

## Decision 4: Month Straddling Rule (MVP Simplification)

**Decision**: For month endpoints, lock the entire month if `firstDayOfMonth < cutoffDate`. This means a month is accessible only when its first day is on or after the cutoff date.

**Rationale**: The spec allows the MVP to lock straddling months (FR-004: "MVP simplification: straddling months are locked"). This avoids the complexity of partial-month data return with `isTruncated` and `effectiveFrom` fields. The lock UI explains this to the user.

**Alternatives considered**:
- Return partial month data (only dates >= cutoffDate) with `isTruncated: true`: Deferred to post-MVP — adds response format complexity and iOS UI changes.
- Lock only when the entire month is before cutoff (allow straddling): Rejected — this would let free users see some data from before the cutoff, undermining the gate.

## Decision 5: Error Class and Response Format

**Decision**: Create `HistoryRetentionError` extending `Error` with `statusCode: 403`, `cutoffDate: string` (YYYY-MM-DD), and `retentionDays: number`. The route handler catches this error before `errorResponse()` and returns a custom 403 JSON body:

```json
{
  "code": "HISTORY_RETENTION_LIMIT",
  "message": "履歴の閲覧は直近30日間に制限されています。",
  "cutoffDate": "2026-01-12",
  "retentionDays": 30
}
```

**Rationale**: Follows the exact same pattern as `PatientLimitError` in feature 009. The `code` field distinguishes this from generic 403 errors and from `PATIENT_LIMIT_EXCEEDED`. The `cutoffDate` and `retentionDays` fields allow the client to display context without hardcoding.

**Alternatives considered**:
- Use HTTP 409 Conflict: Rejected — 403 is consistent with the existing billing gate pattern (009) and semantically correct (entitlement-based restriction).
- Add retention fields to the generic `errorResponse()` mapper: Rejected — the generic mapper produces `{ error: "forbidden", message: "..." }` which is indistinguishable from auth failures. Custom fields need explicit handling.

## Decision 6: iOS Error Differentiation (403 Handling)

**Decision**: In `APIClient.mapErrorIfNeeded()`, add a `parseHistoryRetentionLimit(from:)` check for `"code": "HISTORY_RETENTION_LIMIT"` BEFORE the generic 403 handler, alongside the existing `parsePatientLimitExceeded`. If found, throw `APIError.historyRetentionLimit(cutoffDate:retentionDays:)` without calling `handleAuthFailure`.

**Rationale**: The current 403 handler calls `sessionStore.handleAuthFailure(for: sessionStore.mode)` which clears the session — incorrect for a retention error. This is the same issue solved for `PATIENT_LIMIT_EXCEEDED` in feature 009. The retention error must be intercepted earlier and mapped to a distinct `APIError` case so the UI shows the lock overlay instead of logging the user out.

**Alternatives considered**:
- Use a different HTTP status: Rejected — 403 is consistent and correct; body differentiation is clean.
- Combine with `patientLimitExceeded` into a generic "billing gate error": Rejected — the two errors carry different payloads and trigger different UI flows.

## Decision 7: Shared Retention Service

**Decision**: Create `api/src/services/historyRetentionService.ts` with exported functions for cutoff calculation, premium resolution, and retention checking (for both month and day endpoints). All 4 route handlers call into this shared service.

**Rationale**: The retention check logic is identical across all 4 endpoints (only the date extraction differs: year/month for month endpoints, date string for day endpoints). Centralizing avoids duplication and makes testing easier.

**Alternatives considered**:
- Middleware approach: Rejected — Next.js App Router route handlers are individual files; shared logic is better expressed as a service function called from each handler.
- Inline the check in each route: Rejected — duplicates ~20 lines of logic across 4 files.

## Decision 8: Retention Lock UI (iOS)

**Decision**: Create a new `HistoryRetentionLockView` with two variants (caregiver: paywall buttons, patient: informational only). The view is shown as an overlay when `HistoryViewModel.retentionLocked` is true. In caregiver mode, the "アップグレード" button presents `PaywallView` via `.sheet`.

**Rationale**: The spec requires distinct UIs for caregiver (FR-009: paywall navigation) and patient (FR-010: no purchase buttons). A single view with a `mode` parameter handles both variants cleanly. Reusing the existing `PaywallView` avoids creating a new purchase flow.

**Alternatives considered**:
- Two separate views (`CaregiverHistoryLockView` / `PatientHistoryLockView`): Rejected — too much duplication; the views share most of their layout.
- Show a generic error and let the user navigate to Settings to upgrade: Rejected — spec requires direct paywall navigation from the lock UI.
