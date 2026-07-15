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

## F01 caregiver Today load and aggregation — 2026-07-15

- Android now follows the current iOS `CaregiverTodayViewModel` read contract: caregiver-authenticated `GET /api/patients/{patientId}/today`, `GET /api/medications?patientId=...` and `GET /api/patients/{patientId}/inventory` load together for the selected patient.
- A patient switch clears the previous patient's Today content before loading. Any failed member of the three-read snapshot produces the canonical retryable error rather than mixing stale and partial data.
- Doses sort pending, missed and taken, then by scheduled time. Active non-archived PRN medication, insufficient-dose medication IDs and low-stock state are derived from authoritative medication/inventory responses.
- The production Today tab now renders the iOS information hierarchy: patient header, missed warning, next action, slot-based progress, PRN entry and morning/noon/evening/bedtime timeline. Empty, no-patient, no-selection, loading and retry states use the shared caregiver copy.
- The initial selected-patient request now reproduces the current-iOS progress plus exact `読み込み中...` state, while cached refresh and mutations use the blocking `更新中...` overlay instead of a thin inline indicator.
- Today binds the shared caregiver freshness cursor across dose, medication, inventory and slot-time domains. Hidden-tab isolation remains owned by the existing lazy caregiver shell.
- API/JVM tests cover exact paths/auth, strict response mapping, sorting/filtering/inventory aggregation and failed patient-switch isolation. Compose tests cover populated aggregation, empty navigation, no-patient and retryable failure states.
- The full gate passes with 59/59 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

F01 is `IMPLEMENTED`; F02–F04 add the individual, bulk and PRN proxy mutations without changing this read-state contract.

## F02–F05 caregiver Today mutations — 2026-07-15

- Individual proxy recording sends caregiver-authenticated `POST /api/patients/{patientId}/dose-records`; undo sends `DELETE` with encoded medication and scheduled-time query parameters. The local dose becomes taken-by-caregiver or pending again only after server success.
- Slot recording sends `POST /api/patients/{patientId}/dose-records/slot` with the scheduled dose's Tokyo calendar date and canonical slot. Android applies no patient recording-window restriction, so the current backend caregiver exception can record older missed slots.
- Every slot action requires the iOS-equivalent confirmation, explicitly identifies older missed records, maps complete/partial/nothing-to-record results and preserves inventory-insufficient doses.
- PRN selection and confirmation use `POST /api/patients/{patientId}/prn-dose-records`. Known insufficient inventory disables the medication before the request; authoritative 409 inventory errors map to the same warning.
- PRN selection now mirrors the current iOS scrollable medication-list hierarchy and copy, including patient-specific confirmation. The sheet remains open with an enabled retry action after failure and dismisses only after repository success.
- Successful individual/slot/PRN writes publish the correct dose, inventory and scheduled-notification freshness domains. Failed requests keep prior dose state and publish no revisions.
- Follow-up refresh now distinguishes initial loading from an in-place refresh. A transient refresh failure retains the successful local status and success message, keeps the timeline visible and adds an inline retry warning instead of replacing the screen with a fatal empty state.
- Exact method/path/body/auth, older Tokyo date behavior, partial inventory results, success/failure revisions and follow-up preservation have JVM/API coverage. Production Compose tests exercise individual record/undo, older-missed bulk confirmation, PRN confirmation and refresh-failure preservation.
- The full gate passes with 63/63 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

F02–F05 and `CG-008`, `CG-015`, `CG-016` are `IMPLEMENTED`. F06 now owns complete inventory management.

## F06 caregiver inventory management — 2026-07-15

- The production Inventory tab now loads caregiver-authenticated `GET /api/patients/{patientId}/inventory` for only the selected patient and clears prior-patient state before switching. Empty, no-patient, no-selection, loading, retryable failure and populated states use the shared caregiver shell contract.
- The list mirrors the current iOS information hierarchy with patient identity, action/managed/unconfigured/ended metrics, All/Low/Out filters, semantic status labels, remaining quantity, days remaining and refill due date. Empty inventory links directly to the selected patient's Medications tab.
- Detail settings send `PATCH /api/patients/{patientId}/medications/{medicationId}/inventory`. Refill and correction send `POST .../inventory/adjust` with `REFILL` plus a positive delta or `SET` plus a non-negative absolute quantity.
- Refill offers authoritative 7/14/21-day planned-unit presets with the dose-count fallback and also accepts a positive custom amount. Refill and correction require explicit quantity confirmation; invalid values never leave the device.
- Only a successful 2xx response replaces the local item with the authoritative response and publishes inventory freshness. Failure preserves quantity, settings and revisions, while exposing an inline retryable error.
- The Inventory freshness cursor observes dose, medication and inventory revisions only while the tab is visible, so Today recording and medication lifecycle changes refresh it without hidden-tab requests.
- API/JVM tests cover exact caregiver auth, method/path/body, authoritative replacement, validation, failure preservation and inventory-only revisions. Production Compose tests cover metrics/filtering, refill/correction confirmation and enabling inventory management.
- The full gate passes with 66/66 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

F06 and `CG-009` are `IMPLEMENTED`. The UI-204 rebaseline also covers the current-iOS loading/updating lifecycle, action-first guide, empty onboarding, sectioned list, status help and one-week quick refill, with a direct API-35 light capture. F07 owns the explicit cross-tab revision matrix before Gate F closes.

## F07 cross-tab freshness matrix — 2026-07-15

- The shared cursor matrix now proves all seven consumers against scheduled-dose, PRN-dose, inventory-adjustment, medication-lifecycle and patient-slot-time revisions.
- Scheduled doses refresh both Today screens, both History consumers, caregiver Medications, caregiver Inventory and notification scheduling. PRN doses refresh the same data consumers but deliberately do not reschedule fixed notifications.
- Inventory-only changes refresh patient/caregiver Today, caregiver Medications and caregiver Inventory, but not History or notification scheduling. Medication lifecycle changes additionally refresh notification scheduling, while slot-time changes refresh Today, History, caregiver Medications and scheduling without inventing an inventory revision.
- Each expected consumer refreshes exactly once per revision. Hidden consumers keep the revision pending until revisited, concurrent collectors cannot duplicate a fetch and failed refreshes do not consume pending work.

F07 and `CG-017` are `IMPLEMENTED`. Gate F is complete with the 66/66 API-35 instrumentation, JVM, Debug/Release and Lint gate passing; Gate G now owns caregiver History, PDF, push and settings completion.

## G01 caregiver History and notification destination — 2026-07-15

- The production History tab uses caregiver-authenticated `GET /api/patients/{patientId}/history/month?year&month` and `GET /api/patients/{patientId}/history/day?date` for only the selected patient. Patient and month switches clear incompatible snapshots before loading, while same-month refresh keeps visible content.
- The month surface mirrors the current iOS hierarchy with patient identity, previous/current-month navigation, Sunday-first calendar, four scheduled-slot status dots, PRN marker and semantic legend. The former Android-only day bottom sheet has been removed: selected-day counts/help and scheduled/PRN detail now continue inline below the calendar, with recorder attribution, empty/loading/retry states and exact Tokyo date/time presentation.
- Structured `HISTORY_RETENTION_LIMIT` responses show the server-owned cutoff and retention days separately from generic load failure. Older locked months never mix data from the previously displayed month.
- A caregiver can backfill only a missed scheduled item after an explicit medication-name confirmation. Android sends the exact caregiver dose-record body, replaces the day from authoritative month/day responses and publishes dose, inventory and notification-plan revisions only after 2xx success.
- MainActivity accepts only the server's privacy-minimal `DOSE_TAKEN` data payload (`patientId`, ISO date and canonical slot). A valid target survives cold composition, selects the linked patient and retained History tab, opens the exact day and highlights only the requested slot for four seconds; invalid type/date/slot payloads are ignored.
- Exact paths/auth/bodies, response aliases, patient isolation, retention separation, backfill success/failure revisions and strict push parsing have JVM/API coverage. Production Compose tests cover the message-bearing loader, blocking backfill overlay, inline calendar-to-day/backfill, exact remote slot highlight and cross-patient History-tab navigation; direct API-35 light evidence records UI-206.
- The full gate passes with 69/69 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

G01, `CG-010` and `XP-002` are `IMPLEMENTED`. Physical notification-tap/process-death evidence remains in release verification; G02 now owns on-device PDF export and sharing.

## G02 caregiver PDF report — 2026-07-15

- Android mirrors the current iOS release gate: `BILLING_ENABLED=false` hides the PDF entry in production. The complete billing-enabled path is implemented and tested without sending Apple StoreKit claims or inventing an unapproved Google Play billing contract.
- On tap, `GET /api/me/entitlements` is the source of truth. Free users see the PDF lock, premium users see This month/Last month/Last 30 days/Last 90 days/Custom presets, and unknown/network state remains safely locked with a retryable error.
- All ranges use inclusive Asia/Tokyo dates and are recomputed on submission. Future end dates, reversed ranges, malformed custom dates and ranges over 90 days are rejected locally before `GET /api/patients/{patientId}/history/report?from&to`.
- The typed response includes patient/range, daily four-slot scheduled items, PRN items, status and recorder. Structured retention failure remains distinct from generic generation failure.
- Android generates an A4 multipage PDF entirely on-device with Japanese text, summary/adherence, daily rows, recorder attribution, continuation headers, explicit white page background and page numbers. Cache output replaces older reports.
- Sharing uses `ACTION_SEND`, `application/pdf`, a non-exported FileProvider content URI and temporary read permission. No raw private/public file path is exposed.
- JVM/API tests cover presets, validation, entitlement/report paths and typed failure state. API-35 tests cover free lock, premium custom validation, `%PDF` output and provider URI. The generated PDF was pulled from the emulator, checked with `pdfinfo`, rendered through Poppler and visually verified after fixing a device-theme-dependent black background.
- The full gate passes with 72/72 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint.

G02 and `CG-011` are `IMPLEMENTED`. G03 now owns Android FCM permission and token lifecycle.

## G03 caregiver FCM permission and token lifecycle — 2026-07-15

- Firebase Messaging is included without a checked-in `google-services.json`. Runtime values use `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID` and `FIREBASE_SENDER_ID`; a build with missing values remains runnable and reports the unavailable notification configuration in Settings.
- Analytics collection and Messaging auto-init both remain manifest-disabled. FCM initializes only after the caregiver explicitly enables notifications and, on Android 13+, grants `POST_NOTIFICATIONS`.
- The caregiver Settings switch persists device intent, registers the current token through caregiver-authenticated `POST /api/push/register` with `platform=android` and build-correct `DEV`/`PROD`, re-registers refreshed tokens and shows syncing/configuration/failure states.
- Disabling takes effect locally before network work, disables FCM auto-init and calls `POST /api/push/unregister`. A failed unregister is persisted and retried later without re-enabling local notification display.
- The backend validator now accepts the documented `android` platform while continuing to reject unsupported platforms. Its Android upsert is covered by the existing push integration suite.
- The messaging service ignores locally disabled or invalid events. Valid privacy-minimal `DOSE_TAKEN` data builds a generic notification and preserves the strict patient/date/slot History destination already implemented by G01.
- API contract and repository tests cover exact auth/path/body, enable, token refresh, disable failure, pending retry and missing configuration. Production Compose coverage confirms the explicit Settings control; the full gate passes with 73/73 API-35 instrumentation tests plus JVM, Debug/Release assembly and Lint. The backend push suite passes 14/14.

G03 is complete. `CG-012` and `XP-001` remain `PARTIAL` until G04 adds account-delete defense-in-depth cleanup and closes background-payload privacy/dedup behavior; physical FCM delivery remains a release-matrix item.

## G04 push privacy, deduplication and deletion cleanup — 2026-07-15

- The server now branches by registered device platform. iOS retains its current notification/APNs envelope, while Android receives high-priority data-only FCM so Android never lets a background system notification expose the server's patient-specific display text.
- Android accepts only `DOSE_TAKEN`, non-empty patient ID, strict ISO date and one canonical slot. The displayed title/body are fixed generic resources with no patient name, medication, dosage, result detail, email, free text or token; navigation data remains limited to the documented target fields.
- Server `PushDelivery` uniqueness remains the authoritative event/device deduplication. Android additionally persists a bounded 100-entry FCM message-ID window so redelivery across service recreation does not display the same message twice; the route-derived notification ID also updates rather than stacks an identical target.
- Caregiver proxy recording continues to exclude the acting caregiver before device lookup. Unlinked, disabled and environment-mismatched devices remain outside delivery, and FCM `UNREGISTERED` disables stale devices.
- `DELETE /api/me` already removes server PushDelivery/PushDevice records before the successful response. Only after that response, Android erases enabled/token/registered/pending-unregister state and disables auto-init before clearing the caregiver session. Ordinary logout first performs the soft-disable/unregister lifecycle.
- API tests cover Android data-only content, privacy field exclusion, actor exclusion, event/device dedup and stale-device disable. JVM tests cover process-persistent bounded message dedup and deletion cleanup with no redundant post-deletion network call.

G04, `CG-012` and `XP-001` are `IMPLEMENTED`; physical background/Doze/process-death delivery remains Gate I evidence. G05 now owns the remaining complete Settings flow.

## G05 caregiver complete Settings flow — 2026-07-15

- Settings now carries the current iOS legal/support hierarchy and Japanese copy. Privacy policy, terms and support open the canonical HTTPS destinations in an external browser and remain independent of patient selection state.
- The account card uses the iOS account heading/explanation. Logout is no longer immediate: it presents the current family-mode confirmation, locally disables notification display, attempts authenticated push unregister and then clears the caregiver session.
- Account deletion retains its stronger irreversible confirmation describing the family account and patient/medication/history/inventory data affected. The server operation must succeed before Android performs FCM and session cleanup; failure preserves the signed-in state, selection and production data for retry.
- Production Compose coverage verifies all three legal destinations are reachable in the Settings list and logout requires confirmation. Existing API/JVM coverage verifies distinct server-first logout/account-delete behavior and failure preservation.
- The final Gate G verification passes 74/74 API-35 instrumentation tests plus Android JVM, Debug/Release assembly and Lint. The complete API suite passes 300/300 tests and TypeScript typecheck; ESLint has no errors and retains one unrelated pre-existing E2E warning.

G05 completes Gate G implementation. Physical browser, FCM, process-death and destructive production-account checks remain Gate I verification; Gate H now owns caregiver Analytics consent and the privacy-safe Firebase Analytics wrapper.
