# XP-007 Offline and Retry Contract Evidence — 2026-07-15

## Product contract

Android uses the same fail-safe rule for Patient and Caregiver reads:

1. The first load shows a blocking loading state. If it fails, the screen shows its full error state and retry action.
2. A successful response, including an authoritative empty list, becomes the last-known snapshot for that repository instance.
3. A later refresh failure keeps that snapshot visible, marks it stale, and offers an explicit retry action.
4. Stale caregiver data is read-only. Create, edit, record, delete, backfill, inventory and patient-management mutations are disabled in the UI and rejected again at the repository boundary.
5. Mutations remain server-first. Android has no offline mutation queue and never presents an unconfirmed write as synchronized.
6. A patient or month scope change discards the prior scope instead of showing one patient's health data under another scope.

## Privacy and retention boundary

The last-known snapshot is held only in each in-memory repository `StateFlow`. Medication, dose, inventory and history responses are not written to Room, DataStore, preferences, files or backup storage. Process death therefore discards these health-data snapshots; only the separately specified encrypted session and non-health UI preferences may be restored.

Explicit `hasLoaded` / `monthLoaded` state distinguishes a valid empty response from “nothing has ever loaded”. This prevents an empty medication, Today, inventory, patient or history response from collapsing back to the blocking first-load error after a failed refresh.

## Covered caregiver surfaces

| Surface | Retained scope | Stale UI tag | Retry | Stale mutations |
| --- | --- | --- | --- | --- |
| Patient selection/management | patient list | `caregiver-patient-stale` | list refresh | disabled and repository-guarded |
| Today | selected patient | `caregiver-today-stale` | Today refresh | disabled and repository-guarded |
| Medications/regimens | selected patient | `caregiver-medication-stale` | medication refresh | disabled and repository-guarded |
| Inventory/detail | selected patient | `caregiver-inventory-stale` | inventory refresh | disabled and repository-guarded |
| History month/day | selected patient and month/day | `caregiver-history-stale`, `caregiver-history-day-stale` | scoped refresh | backfill disabled and repository-guarded |

PDF report generation is an explicit on-demand request rather than a retained screen snapshot. Its existing failure state remains retryable and no report is fabricated or queued while offline.

## Automated evidence

- Repository JVM tests cover populated and authoritative-empty snapshots, refresh failure, scope preservation and stale mutation rejection; the final Debug and Release JVM suites pass 168/168 each.
- Production Compose tests cover visible retained content, stale banners/retry actions, disabled mutation controls and the distinction between refresh failure and mutation failure.
- The successful caregiver dose-record path keeps its success result visible when the automatic follow-up read fails, while rendering the shared stale state instead of a false mutation error.
- The complete API-level instrumentation matrix is recorded in `i01-api-matrix-20260715.md`: API 26, 33 and 35 each pass 95/95 (285/285 total), alongside Debug/Release assembly and Lint.

Physical airplane-mode, captive-portal and network-transition checks remain part of the Gate I device runbook. They verify the implemented contract; they do not imply a persistent health-data cache or offline write queue.
