# Android API and Session Contracts

**Pinned reference:** `main@1cf8aef`
**Time zone:** `Asia/Tokyo` unless an endpoint explicitly carries another value
**Machine dates:** `YYYY-MM-DD`; timestamps are ISO-8601

This document records the client-observable contract. Backend validators and tests remain authoritative for field-level behavior.

## 1. Authentication policies

Every Android request declares exactly one policy; the client must not infer it from the current screen.

| Policy | Authorization header | Refresh behavior | Failure behavior |
|---|---|---|---|
| `PUBLIC` | Never sent, even if a stale token exists | None | 401/403 is surfaced without clearing any stored session |
| `PATIENT` | `Bearer <patientSessionToken>` | Proactive refresh when expiring; one forced refresh and one retry on 401 | Refresh failure or second 401 invalidates only the patient token and returns to linking |
| `CAREGIVER` | `Bearer caregiver-<Supabase access token>` | Refresh Supabase token before expiry; concurrent requests coalesce | 401 invalidates caregiver auth and selected patient; 403 does not |

`POST /api/patient/link` is `PUBLIC`. It is a parity defect if a generic interceptor attaches a current patient or caregiver token to it.

## 2. Shared response/error rules

- Successful list/object responses normally use `{ "data": ... }`; some history, slot-bulk and PRN routes intentionally return top-level payloads. Parse the exact endpoint shape.
- Patient endpoints decode into Kotlin serialization wire DTOs, then map into domain models. UI state must not depend on endpoint DTOs or raw `JSONObject` values; unknown response fields are ignored, while missing required fields fail contract decoding.
- 400/422 validation bodies may contain `message` or `messages`; Android maps them to typed validation errors and safe UI copy.
- 401 means the credential for a protected request is invalid. Public-request 401 must not mutate session state.
- Unauthorized caregiver access to another patient's resources is concealed as 404 where the backend applies ownership hiding.
- 403 may be a generic forbidden response or a typed domain error.
- 409 with `code = "insufficient_inventory"` is not an auth failure.
- 429 is a rate-limit/lockout response. Linking has both an IP limit and a patient-code attempt lock.
- 5xx and transport errors never expose server internals or raw exception text to users.

Typed domain errors:

| Code | HTTP | Required fields | Client behavior |
|---|---:|---|---|
| `PATIENT_LIMIT_EXCEEDED` | 403 | `limit`, `current` | Keep caregiver session; show initial-release/premium gate UX |
| `HISTORY_RETENTION_LIMIT` | 403 | `cutoffDate`, `retentionDays` | Keep session; render role-appropriate retention lock |
| `insufficient_inventory` | 409 | code/message | Keep session; retain insufficient items as unrecorded |
| `INVALID_RANGE` | 400 | code/message | Keep session; keep PDF range editor open |

## 3. Entry, session and patient management

| ID | Method/path | Policy | Request | Success response/side effect |
|---|---|---|---|---|
| API-001 | `POST /api/patient/link` | PUBLIC | `{ code: six digits }` | `{data:{patientSessionToken,expiresAt?}}`; code is one-time, normally valid 15 minutes |
| API-002 | `POST /api/patient/session/refresh` | PATIENT, retry disabled | no meaningful body | Rotated patient token and expiry; old token is revoked |
| API-003 | `DELETE /api/patient/session` | PATIENT, retry disabled | none | Revoke current patient-device session before local unlink |
| API-004 | `GET /api/patients` | CAREGIVER | none | `{data:[{id,displayName,slotTimes}]}` |
| API-005 | `POST /api/patients` | CAREGIVER | `{displayName}`; nonblank, max 50 | Created patient; server enforces patient count |
| API-006 | `PATCH /api/patients/{patientId}` | CAREGIVER | `{slotTimes:{morning,noon,evening,bedtime}}` | Updated patient slot times |
| API-007 | `POST /api/patients/{patientId}/linking-codes` | CAREGIVER | none | `{data:{code,expiresAt}}`; invalidates earlier active code and can reactivate a revoked link |
| API-008 | `POST /api/patients/{patientId}/revoke` | CAREGIVER | none | Revokes active patient sessions but preserves patient data |
| API-009 | `DELETE /api/patients/{patientId}` | CAREGIVER | none | Permanently deletes patient and dependent data; clear selection only after success |
| API-010 | `DELETE /api/me` | CAREGIVER | none | Deletes caregiver account and disables/removes server device registrations before local reset |

Link exchange additionally has a persistent IP limit of 20 requests per 15 minutes. Known patient-code failures are counted; five failed attempts lock that patient code exchange for five minutes.

## 4. Medication and regimen

| ID | Method/path | Policy | Key contract |
|---|---|---|---|
| API-020 | `GET /api/medications?patientId=...` | Current role; caregiver patient resolved explicitly | Returns medication list with lifecycle, PRN, inventory, next schedule and regimen summary fields |
| API-021 | `POST /api/medications` | CAREGIVER | Creates regular or PRN medication; patientId is mandatory |
| API-022 | `GET/PATCH/DELETE /api/medications/{id}?patientId=...` | CAREGIVER for mutation | Fetch/edit/archive/delete semantics remain server-authoritative |
| API-023 | `GET/POST /api/medications/{id}/regimens` | CAREGIVER | Time zone, start/end, `times`, `daysOfWeek`, enabled schedule |
| API-024 | `PATCH /api/regimens/{id}` | CAREGIVER | Partial update including enabled state |

Medication DTO fields required by Android include `id`, `patientId`, name/dosage, `doseCountPerIntake`, strength value/unit, notes, PRN fields, start/end, inventory fields, active/archive state, next schedule, regimen times and days. Optional fields must not be replaced by guessed values except where the iOS decoder defines a default.

## 5. Today, recording and PRN

| ID | Method/path | Policy | Key contract |
|---|---|---|---|
| API-030 | `GET /api/patient/today` | PATIENT | Current patient's schedule; optional slot-time query supported |
| API-031 | `GET /api/patients/{patientId}/today` | CAREGIVER | Selected patient's schedule |
| API-032 | `GET /api/patient/slot-times` | PATIENT | Four validated `HH:mm` values |
| API-033 | `POST /api/patient/dose-records` | PATIENT | `{medicationId,scheduledAt}`; patient cannot delete |
| API-034 | `POST /api/patients/{patientId}/dose-records` | CAREGIVER | Caregiver proxy record |
| API-035 | `DELETE /api/patients/{patientId}/dose-records?...` | CAREGIVER | Caregiver removes a taken record; inventory is compensated server-side |
| API-036 | `POST /api/patient/dose-records/slot` | PATIENT | `{date,slot}` plus optional slot times; server enforces `scheduledAt-30m ... +60m` |
| API-037 | `POST /api/patients/{patientId}/dose-records/slot` | CAREGIVER | `{date,slot}`; caregiver may record older missed slots and is not blocked by patient window |
| API-038 | `POST /api/patients/{patientId}/prn-dose-records` | PATIENT or authorized CAREGIVER | `{medicationId,takenAt?,quantityTaken?}`; returns record and optional inventory snapshot |
| API-039 | `DELETE /api/patients/{patientId}/prn-dose-records/{id}` | CAREGIVER | Deletes caregiver-manageable PRN record |

Schedule dose identity is `(patientId, medicationId, scheduledAt)` and the UI key is stable. Only taken records are stored; pending/missed are derived. Status ordering and aggregation follow current iOS/backend tests.

Slot-bulk response is top-level:

```json
{
  "updatedCount": 2,
  "remainingCount": 1,
  "insufficientCount": 1,
  "totalPills": 3,
  "medCount": 2,
  "slotTime": "08:00",
  "slotSummary": { "morning": "taken" },
  "recordingGroupId": "optional-uuid"
}
```

- Repeated operations are idempotent.
- Inventory-sufficient doses can succeed while insufficient doses remain; Android must preserve all returned counts.
- Inventory never becomes negative and the backend is authoritative under concurrency.
- A successful scheduled individual/bulk mutation updates UI immediately. Reminder rebuild and cross-tab refresh happen afterward and must not delay the success state.
- A failed follow-up refresh does not turn an already successful mutation into failure.

### Cross-screen mutation freshness

- Successful writes advance monotonic domain revisions for `DOSE`, `MEDICATION` and/or `INVENTORY`; failed writes and zero-update bulk results do not. Scheduled-dose/regimen changes additionally advance `NOTIFICATION_PLAN`.
- Consumer mappings are explicit: patient/caregiver Today, caregiver Medications, patient/caregiver History and caregiver Inventory observe their data domains; the notification scheduler observes only `NOTIFICATION_PLAN`, so PRN records cannot rebuild scheduled alarms.
- A consumer owns a cursor. A new cursor is stale by definition, so first-visit and process-recreated screens fetch authoritative data even if no in-memory event survived.
- Revision state outlives destination composition within the process. A mutation emitted before a lazy tab exists remains visible to its later-created cursor.
- Cursor refresh is mutex-serialized. Concurrent collectors cannot duplicate a refresh for one revision snapshot; a revision emitted during refresh remains pending for the next pass; a failed refresh consumes nothing.

## 6. History and PDF

| ID | Method/path | Policy | Key contract |
|---|---|---|---|
| API-040 | `GET /api/patient/history/month?year&month` | PATIENT | Four-slot summaries plus optional `prnCountByDay` |
| API-041 | `GET /api/patient/history/day?date` | PATIENT | Scheduled details plus PRN items |
| API-042 | `GET /api/patients/{id}/history/month?year&month` | CAREGIVER | Same core model, selected patient |
| API-043 | `GET /api/patients/{id}/history/day?date` | CAREGIVER | Same core model, selected patient |
| API-044 | `GET /api/patients/{id}/history/report?from&to` | CAREGIVER | Patient, range and per-day slot/PRN report data; max 90 inclusive days |
| API-045 | `GET /api/patient/history/streak` | PATIENT | `{ currentStreakDays: Int, isAtLeast: Boolean, todayStatus: complete|inProgress|missed|noSchedule }` |

History month accepts current `days` and legacy `monthSummary`; day accepts current `doses` and legacy `dayDetails`. Android may support both for compatibility but tests must prefer the current response. Retention uses `[todayTokyo-29d, todayTokyo]` for free access, with the server deciding entitlement.

API-045 is supplementary to the ordinary history response. Its load or refresh failure hides/retains only the streak presentation and must not replace valid month/day history with an error. Android must parse the four `todayStatus` values strictly, preserve the server's `isAtLeast` qualifier, and refresh streak on History entry/refresh, slot-time changes, scheduled-dose mutations and selected patient-session changes.

The production caregiver repository uses API-042/API-043 with caregiver auth and selected-patient isolation. A missed scheduled item may be backfilled only through confirmation-protected `POST /api/patients/{id}/dose-records`; local history and freshness change only after a successful response. Remote caregiver navigation accepts only `type=DOSE_TAKEN` or `type=DOSE_MISSED` plus a linked `patientId`, ISO local `date` and canonical `slot`; all other event types remain rejected.

PDF generation is on-device. Patient mode renders no PDF action. Android uses the system share sheet and a content URI, never a publicly writable raw file path.

When billing is enabled, Android first reads API-063 and treats unknown entitlement as locked. Premium generation calls API-044 only after inclusive Tokyo-date validation (`to <= today`, `from <= to`, maximum 90 days). The response is rendered into the app cache, prior report files are removed, and only `${applicationId}.fileprovider` grants temporary read access. The initial public release keeps `BILLING_ENABLED=false`, matching current iOS `billingEnabled=false`; therefore no PDF entry or unsupported Google Play purchase claim is exposed in production.

## 7. Inventory

| ID | Method/path | Policy | Key contract |
|---|---|---|---|
| API-050 | `GET /api/patients/{id}/inventory` | CAREGIVER | Inventory list, low/out flags, consumption projections and refill date |
| API-051 | `PATCH /api/patients/{id}/medications/{medId}/inventory` | CAREGIVER | Enable/disable and/or absolute quantity update |
| API-052 | `POST /api/patients/{id}/medications/{medId}/inventory/adjust` | CAREGIVER | Reason plus delta or absolute quantity |

`isInsufficientForDose = inventoryEnabled && inventoryQuantity < doseCountPerIntake`. Low/out presentation uses server fields; Android does not recalculate caregiver alert transitions.

## 8. Push, entitlement and platform mapping

| ID | Method/path | Policy | Android requirement |
|---|---|---|---|
| API-060 | `POST/DELETE /api/device-tokens` | CAREGIVER | Legacy device-token endpoint if still referenced by current server contract |
| API-061 | `POST /api/push/register` | CAREGIVER | Send FCM token, `platform = android`, and environment supported by backend |
| API-062 | `POST /api/push/unregister` | CAREGIVER | Soft-disable/unregister current token |
| API-063 | `GET /api/me/entitlements` | CAREGIVER | Server entitlement is source of truth |
| API-064 | iOS `POST /api/iap/claim` | CAREGIVER | Do not reuse StoreKit payload. Google Play billing requires a separately approved backend contract before Android billing work |

Remote push payload must not contain medication name, dosage, dose time/result detail, free text or tokens. Current caregiver navigation requires an allowlisted event type (`DOSE_TAKEN` or `DOSE_MISSED`), patientId, Tokyo date, slot and optional recordingGroupId. Both allowed types route to caregiver History, exact day/slot and temporary highlight; missing, malformed or unknown values are ignored.

## 9. Contract-test gate

For every API row used in a phase, Android must have tests for:

- Exact method, path, query and auth policy
- Full success fixture and missing optional fields
- Date/time parsing at Tokyo day/month boundaries
- Relevant 401/403/404/409/422/429 behavior
- No session mutation for public/domain errors
- One-refresh/one-retry ceiling for patient auth
- Cancellation and follow-up refresh failure
- Backend/idempotency behavior through integration smoke tests where safe
