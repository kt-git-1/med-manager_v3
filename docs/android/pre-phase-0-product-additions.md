# Pre-Phase 0 product additions

**Status:** API deployed and schema verified on Staging; Android commit/DebugView cross-role verification pending
**Branches:** API/iOS `staging`; Android `android-dev`
**Authority:** current iOS Staging `AnalyticsService.swift`, API schema/routes/tests, then this contract

These additions are release prerequisites and run before the Play publication Phase 0 roadmap. They do not authorize Play Console submission, Production deployment, or merging Android into the iOS branch.

## A. Firebase Analytics parity

Android must use the same privacy-first contract as the current iOS Staging build:

- collection is OFF until an explicit user decision;
- Patient and Caregiver use one shared consent state;
- disabling collection stops transport and resets device Analytics data;
- Firebase user ID is always unset and ad-personalization signals are disabled;
- tests, previews and explicitly suppressed sessions emit nothing;
- only fixed event names, exact parameter keys and enum values pass the wrapper;
- identity, patient, medication, dosage, inventory, date/time, dose status, notification content, linking code, token, URL and free text are prohibited.

The Android allowlist must include all current iOS Staging events. The parity additions in this work unit are:

| Event | Exact parameters |
|---|---|
| `core_action_failed` | fixed `action_name`, fixed `reason` |
| `patient_link_code_share_tapped` | `surface=patient_management` |
| `notification_permission_result` | fixed `result`, fixed `surface` |

`notification_permission_result.result` accepts only `authorized`, `denied`, `provisional`, or `unavailable`. Purchase/restore result values cannot be used in this event, and notification values cannot be used in purchase/restore events.

Verification requires unit tests of consent and the event-specific allowlist, Compose tests for instrumented operations, and Staging DebugView inspection using synthetic actions only. DebugView evidence must not contain health or identity data.

## B. Medication archive and history preservation

Deleting a medication is a soft archive, not a physical row deletion. The operation sets `isArchived=true`, `isActive=false`, and one immutable `archivedAt` timestamp.

Behavior is split by read purpose:

| Surface | Archived medication behavior |
|---|---|
| medication list, Today, future schedule, reminders and new recording | excluded immediately |
| Patient/Caregiver day and month history | schedules before `archivedAt` remain |
| history streak and caregiver PDF report | schedules before `archivedAt` remain |
| PRN history | existing record remains through its medication relation |

The archive boundary is an exclusive upper bound: a generated scheduled dose must satisfy `scheduledAt < archivedAt`. Medication/regimen start and end boundaries continue to apply.

The migration adds nullable `Medication.archivedAt` and backfills already archived rows from `updatedAt`. It does not delete or rewrite `DoseRecord`, `PrnDoseRecord`, patient, regimen or inventory rows. Existing iOS response shapes are unchanged, so this is backward compatible with the current Staging iOS app.

Permanent patient/account deletion remains a separate destructive operation and still removes dependent medication and history data according to the account-deletion contract.

## Exit criteria

- API migration, typecheck, schedule unit tests, route/integration tests and full API regression pass.
- Android Analytics unit and UI tests pass for both Staging and Production variants where applicable.
- Staging confirms: archive removes the drug from Today and registration targets, while Patient and Caregiver history still show pre-archive scheduled and PRN records.
- Staging DebugView confirms the three parity events and the privacy exclusion list.
- Only after these checks pass does the Play publication Phase 0 roadmap resume.

## 2026-08-22 verification checkpoint

- `staging@470e2a2` contains the API, migration and iOS deletion-copy changes.
- `GET /api/health` returned HTTP 200 from the canonical Staging host.
- A non-existent medication lookup returned the expected HTTP 404 through the deployed Prisma medication query. This proves the deployed runtime can query the additive `archivedAt` schema instead of failing on a missing column.
- API typecheck, lint, formatting and 326 Vitest tests passed locally.
- Android Staging/Production JVM suites, Staging lint/build and 53 API 35 Compose tests passed locally.
- Authenticated archive/history behavior and Firebase DebugView remain the final live checks.
