# Phase 3 — Caregiver Mode

This document records caregiver implementation evidence against the current iOS/API baseline. A row may be `IMPLEMENTED` based on deterministic tests while remaining short of `VERIFIED` until matched iOS visuals and physical-device evidence exist.

## D01 caregiver shell and patient selection — 2026-07-14

### Runtime path

- Authenticated caregiver routing now enters `CaregiverHomeScreen`; the former session-ready placeholder is no longer the product destination.
- The bottom navigation follows the current iOS order: Today, Medications, Inventory, History, Settings. Today is the only initially loaded tab.
- A tab is added to the loaded set only after selection. Loaded content remains composed to preserve state; hidden content is transparent, has lower stacking order, disables its actions and is hidden from accessibility.
- `GET /api/patients` uses the caregiver auth policy and a Kotlin-serialization DTO boundary. Unknown response fields are ignored, while required patient identity fields remain strict.
- `CaregiverPatientRepository` owns feature patient state while `CaregiverSelectionRepository` remains the only persisted selected-ID owner.

### Selection contract

After every successful patient-list load:

1. A stored ID still present in the response is retained.
2. An invalid stored ID is cleared.
3. A sole patient is auto-selected when no valid selection exists.
4. Zero patients, multiple patients without a selection and network failure remain separate UI states.
5. Logout or leaving authenticated caregiver mode clears the feature cache and persisted selection, preventing display-name leakage into a later account.

### Verification

- Repository tests cover sole-patient auto-selection, invalid-ID clearing, valid selection restoration/change and explicit load failure.
- API tests cover caregiver bearer authentication, URL, typed slot-time mapping and optional slot times.
- Compose tests cover Today initial state, all five tabs, Settings selection, persistence across tab switches and shared no-patient states.
- `./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest` passes with 43/43 API-35 instrumentation tests.

### Residual before verification

- Match UI-200 shell and selection states against current iOS light/dark/large-text captures.
- Verify process/configuration restoration, TalkBack traversal and compact/large physical devices.
- Implement D02 create/edit/link/revoke/delete/account lifecycle; the non-Settings feature tabs intentionally remain gated landing surfaces until their later vertical slices.

`CG-001` and `CG-014` are `IMPLEMENTED`, not yet `VERIFIED`.

## D02 patient creation increment — 2026-07-14

- Settings exposes a production patient-creation form above the selection list.
- Display names are trimmed and rejected locally when blank or longer than 50 characters; invalid input never reaches the API.
- `POST /api/patients` uses a serialized `{displayName}` body with caregiver authentication and maps the typed created-patient envelope.
- A successful create is prepended to the list and persisted as the active patient. A `PATIENT_LIMIT_EXCEEDED` response keeps the existing list/selection and renders the specific initial-release limit message.
- Repository/API tests cover normalization, invalid short-circuiting, request body, success selection and patient-limit preservation. Compose tests cover the 51-character form error.

`CG-002` and the first D02 checklist row are `IMPLEMENTED`. Slot-time edit, linking, revoke/delete and caregiver-account deletion remain.

## D02 time-preset increment — 2026-07-14

- The selected patient exposes four native 24-hour time pickers for morning, noon, evening and bedtime, initialized from the authoritative patient response.
- Saving sends all four strict `HH:mm` values through `PATCH /api/patients/{patientId}` and replaces local values with the server response only after success.
- Malformed values never reach the API. Failure retains the previous repository values and provides an inline retryable error.
- `MutationDomain.SLOT_TIMES` invalidates patient/caregiver Today, patient/caregiver History, caregiver Medications and notification scheduling without inventing a dose or inventory mutation.
- API/repository/freshness tests cover the exact PATCH body, response mapping, validation, success-only state change and revision behavior. The Compose test reaches the production preset card through the Settings lazy list.
- The full gate passes with 44/44 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

`CG-002A` and the second D02 checklist row are `IMPLEMENTED`. Physical notification rescheduling and matched iOS visual verification remain before `VERIFIED`.

## D02 linking-code increment — 2026-07-14

- The selected patient can issue a new code through payload-free `POST /api/patients/{patientId}/linking-codes` with caregiver authentication.
- The typed response preserves the six-digit code and authoritative ISO expiry; UI renders the expiry in Tokyo time and explains the 15-minute validity contract.
- Copy places only the code in the Android clipboard. Share opens the Android Sharesheet with the current iOS-equivalent onboarding message, code and expiry.
- Issuing, issued and retryable failure states are explicit. A code is cleared whenever the selected patient changes or a new patient/account context replaces it, preventing cross-patient disclosure.
- API/repository tests cover the payload-free request, response mapping and selection scoping. Compose coverage verifies the issue action remains reachable in the production Settings list.

`CG-003` and the third D02 checklist row are `IMPLEMENTED`; physical clipboard/Sharesheet and matched iOS visual checks remain before `VERIFIED`.

## D02 destructive lifecycle increment — 2026-07-14

- Data-preserving link revoke, irreversible patient cascade deletion and caregiver-account deletion use separate routes, copy and confirmation dialogs.
- Revoke and patient delete remove local patient state, clear the scoped linking code, reconcile sole/empty selection and publish dose/medication/inventory/notification/slot-time freshness only after a 2xx server response.
- Caregiver-account deletion calls `DELETE /api/me` first. Only a successful response clears the caregiver feature repository and invokes local session logout; a failure explicitly preserves patients, selection and authentication.
- Logout remains a separate local action and is not presented as account deletion.
- API tests verify all three method/path pairs. Repository tests prove success-only mutation, sole-patient reconciliation, broad freshness invalidation and failure preservation. Compose coverage verifies the revoke confirmation states that patient sessions expire while data remains.

`CG-004`, `CG-013` and the remaining D02 checklist rows are `IMPLEMENTED`. D02 is implementation-complete; matched iOS visuals and production/physical destructive-operation verification remain before `VERIFIED`.
