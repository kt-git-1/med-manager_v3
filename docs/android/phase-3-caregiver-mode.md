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

## D03 caregiver tutorial — 2026-07-14

- The current iOS ten-step sequence and Japanese copy are reproduced: Today, Medications, Inventory, History, Settings, time presets, registration, code issue, code share and notification permission.
- Each step selects the real persistent tab. Settings steps scroll the production lazy list toward the time, registration or linking-code surface when that surface exists; absent-patient steps fall back to the real registration field.
- Back/next controls preserve step order. Skip and final completion persist a one-time caregiver tutorial decision independently from patient mode.
- The final primary action marks completion, selects Settings, brings the real patient-name field into view and requests Android notification permission. The final secondary action uses the pinned “あとで設定する” path without requesting permission.
- The overlay exposes a localized pane title and step count. Its content scrolls so skip/back/primary actions remain reachable at 200% font.
- Compose tests cover canonical final copy/actions, real Today-to-Medications navigation, persisted skip, full ten-step completion, registration focus, permission invocation and 200% pane/action reachability.
- The full gate passes with 48/48 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

D03 and the caregiver portion of `XP-003` are `IMPLEMENTED`. Matched iOS visual captures and physical TalkBack/permission verification remain before `VERIFIED`.

## E01 caregiver medication list — 2026-07-14

- The Medications tab now uses `GET /api/medications?patientId=...` with caregiver authentication and the strict shared medication DTO/domain boundary.
- Loading, patient-list failure, zero patients, no selection, medication-load failure, empty list, filtered empty and populated content are distinct states.
- The current patient header, scheduled/PRN/ended metrics and All/Scheduled/PRN/Ended filters follow the current iOS information hierarchy.
- Cards expose name, regular/PRN/ended status, dosage, per-intake count, daily/weekday slot schedule and inventory remaining/out/insufficient status without relying on color alone.
- A patient switch clears old items before loading the new patient. Caregiver logout/account context loss clears the medication cache, preventing cross-account display.
- The Medications freshness cursor observes medication, inventory and slot-time revisions only while the tab is visible.
- API/repository tests cover caregiver auth/path, strict mapping, patient-switch isolation and explicit failure. Compose tests cover populated/filter/schedule/inventory and canonical empty states.
- The full gate passes with 50/50 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

E01 and `CG-005` are `IMPLEMENTED`. Paired iOS visuals, large text, TalkBack and physical verification remain.

## E02 caregiver medication form — 2026-07-15

- The production medication list now exposes a selected-patient add action and a per-card edit action. Both open one typed `CaregiverMedicationDraft` surface, so create and update cannot drift in fields or validation.
- The form mirrors the current iOS medication inputs: regular/PRN kind, name, strength value/unit including unknown strength, per-intake count, start/end dates, PRN instructions, notes and optional initial inventory.
- Validation reports all applicable errors together for blank name/unit/value, non-positive strength or dose, reversed dates and negative/non-numeric inventory. Optional empty values retain the current iOS/API null-or-zero contract.
- Create sends caregiver-authenticated `POST /api/medications` with the selected `patientId`; edit sends `PATCH /api/medications/{id}?patientId=...` without leaking `patientId` into the update body. Both decode the strict shared medication DTO.
- A successful server response alone updates the selected-patient list and publishes medication, inventory and notification-plan freshness. Failure preserves the previous list and draft, and exposes a retryable inline error.
- JVM tests cover aggregate validation, wire mapping, exact method/path/body/auth, successful create state/revisions and failed update preservation. Compose tests cover add navigation, regular-to-PRN conditional content, validation summary and edit prepopulation.
- The full gate passes with 52/52 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

E02 and `CG-006` are `IMPLEMENTED`. E03 owns regular-medication daily/weekday slot selection and regimen create/update/disable. Matched iOS visual captures, large-text/TalkBack and physical-device verification remain before `VERIFIED`.

## E03 regimen schedule CRUD — 2026-07-15

- Regular medication forms now reproduce the current iOS daily/weekday frequency choice, canonical Monday-through-Sunday ordering and morning/noon/evening/bedtime slot selection. Existing enriched regimen values accept both canonical slot keys and patient-specific resolved times.
- Regular medications require at least one time slot; weekly schedules additionally require at least one weekday. Switching to PRN clears and hides the regular schedule, matching the iOS form state transition.
- Saving first commits the medication, then reads `GET /api/medications/{medicationId}/regimens`: no existing regimen uses POST, an active or historical regimen uses PATCH with `enabled: true`, and switching to PRN disables every active regimen with PATCH.
- Regimen bodies preserve the authoritative `Asia/Tokyo` timezone, medication start/end dates, canonical slot keys and ordered weekday keys. Every request uses caregiver authentication.
- If regimen persistence fails after the medication server mutation succeeds, the authoritative medication remains in local state, freshness is published, the draft remains open and the UI reports failure so the schedule can be retried without pretending the first mutation rolled back.
- JVM/API tests cover schedule validation/order, exact list/create/update paths and bodies, create/re-enable/disable branching and partial-success preservation. Compose tests cover weekly controls, weekday/slot interaction, edit prepopulation and PRN schedule removal.
- The full gate passes with 53/53 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

E03 and `CG-007` are `IMPLEMENTED`. Physical notification timing, matched iOS captures, large-text and TalkBack verification remain before `VERIFIED`.

## E04 inventory fields and medication lifecycle — 2026-07-15

- Create and edit share the iOS medication lifecycle fields: start date, optional end date, optional initial inventory, fixed tablet inventory unit and notes. Reversed dates, negative/non-numeric inventory and all other form errors are rejected together before any request.
- Ended medications remain derived from an end date before the Tokyo calendar day and continue to participate in the All/Ended metrics and filters. Archived medication is excluded by the authoritative backend list contract.
- Existing medication edit exposes the current iOS destructive action and exact confirmation intent. Confirmation sends caregiver-authenticated `DELETE /api/medications/{id}?patientId=...`; the backend archives the medication and marks it inactive.
- The local item is removed and medication, inventory and notification-plan freshness is published only after a 2xx response. Failure keeps the medication, current draft and every revision unchanged, then shows an inline retryable error.
- API/repository tests cover the exact DELETE method/path/auth, request without body, success-only removal and revision changes, and failure preservation. Compose coverage proves deletion is unavailable during create and requires the explicit irreversible-action dialog during edit.
- The full gate passes with 54/54 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

E04 and the medication lifecycle portion of `CG-006` are `IMPLEMENTED`. Full inventory enable/adjust/refill behavior remains deliberately owned by F06/`CG-009`; E05 now owns cross-feature invalidation and current-iOS visual comparison.

## E05 cross-feature invalidation and current-iOS visual comparison — 2026-07-15

- Medication create/edit/archive publishes medication, inventory and notification-plan revisions only after the medication server mutation succeeds. Patient Today, caregiver Today, caregiver medications, caregiver inventory and the notification scheduler consume the domains they depend on; patient/caregiver history correctly ignore medication-only changes.
- A dedicated revision-matrix test initializes every consumer, publishes one medication mutation and proves each affected consumer refreshes exactly once while unrelated history consumers remain fresh.
- Fresh current-iOS and Android API-35 captures compare the real production list and form components at Japanese/light/default font. The accepted files and capture conditions are recorded in `docs/android/evidence/e05-20260715/README.md`.
- The Android list now mirrors the iOS visual hierarchy with a patient identity header, 2 x 2 semantic metric grid, colored filters, white medication cards, type/inventory emphasis and canonical-slot Japanese labels. The form uses the iOS order and hierarchy of hero/progress, basic information, medication kind and schedule while retaining native Android controls.
- Physical-device rendering, dark/large-font captures and TalkBack traversal remain release verification work; they do not block E05 from reaching `IMPLEMENTED`.

E05 and Gate E are `IMPLEMENTED`. Gate F now owns caregiver Today proxy actions and complete inventory management.
