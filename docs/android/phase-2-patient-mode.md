# Android Phase 2: Patient Mode

> **Rebaseline notice (2026-07-14):** Historical implementation evidence below predates `main@1d9d19e`. `PT-005`, `PT-006`, `PT-011`, `PT-013`, `PT-014`, `PH-006` and `XP-002` must be rechecked for post-record reminder rebuilding, next-day retention, cross-tab history freshness, persistent lazy tabs and role-correct notification routing before this phase is treated as current.

**Status: IMPLEMENTED / VERIFICATION IN PROGRESS. This is not final iOS parity completion.**

This file records the first working path. Completion is governed by the `PT-*`, `PH-*`, and relevant `XP-*` rows in `parity-requirements.md`.

## 2026-07-14 A04 mutation-freshness evidence

- `MutationFreshnessStore` owns monotonic dose, medication and inventory revisions independently of any screen or tab.
- Explicit consumer mappings cover patient/caregiver Today, caregiver Medications, patient/caregiver History, caregiver Inventory and notification scheduling.
- `FreshnessCursor` serializes refreshes and consumes a revision only after a successful refresh. New cursors intentionally require a first authoritative load; mutations published during a refresh remain pending.
- Successful patient individual, positive-count bulk and PRN recordings advance dose/inventory revisions. Blocked/failed and zero-update paths do not.
- Patient History now refreshes on first visit and on the next visible visit after a dose mutation, without refreshing merely because a cached tab is reselected.
- JVM tests cover domain increments, relevant-domain filtering, first visit, hidden destination, destination created after mutation, process recreation, concurrent collectors, refresh failure and mutation-during-refresh.

A04 establishes the shared contract. A05 binds reminder maintenance; A06 supplies persistent lazy patient tabs; caregiver bindings remain part of C1–C4.

## 2026-07-14 A05 post-record reminder evidence

- Scheduled individual success and bulk success with `updatedCount > 0` advance a dedicated notification-plan revision only after visible success state is committed and mutation progress is cleared.
- PRN success advances dose/inventory freshness but not notification-plan freshness. Zero-update bulk and failed writes advance neither scheduled nor notification-plan state.
- An application-scope `FreshnessCursor` invokes `PatientReminderMaintenanceCoordinator` outside the UI mutation coroutine. The coordinator is mutex-serialized and guarded by active patient session plus enabled notification settings.
- History/plan fetch or AlarmManager replacement failure produces a separate maintenance warning while retaining the successful dose status and success message. A later successful rebuild clears the warning.
- Deterministic tests cover master/session guards, success/failure callbacks, duplicate-safe revisions, tomorrow, month boundary, year boundary, disabled slots and a future secondary reminder after its primary time has passed.
- A failed quiet Today refresh after a successful record preserves the optimistic/server-accepted status and success message.

`PT-005`, `PT-006`, `PT-011`, `PT-013` and `PH-006` return to `IMPLEMENTED`. Live API recording and physical AlarmManager delivery remain before `VERIFIED`.

## 2026-07-14 A06 retained-tab and local-route evidence

- Patient Today is composed initially. History and Settings are composed only on first selection and then remain mounted under stable `key(PatientTab)` identities.
- The visible tab is z-ordered and opaque. Hidden visited tabs are transparent, lower-z, pointer-intercepted and `clearAndSetSemantics`, matching iOS `opacity`/`allowsHitTesting`/`accessibilityHidden` behavior.
- Compose tests prove a destination is absent before first visit, each tab is created once, local remembered state survives repeated switching, lazy-list scroll position is retained, and hidden actions are absent from the merged semantics tree.
- History's duplicate-safe freshness cursor runs on first visible visit and after a dose revision only when visible or next selected.
- Patient local notification payloads always select Today and preserve the exact slot highlight for four seconds. The payload date no longer incorrectly routes patient reminders to History.
- Existing tutorial navigation still selects/loads its required retained tab; the 200% font-scale tutorial test remains green.

`PT-014` is `IMPLEMENTED`. `XP-002` is `PARTIAL` until caregiver remote push routing and process-death/physical notification taps are verified.

## Implemented foundation

- Patient-only bottom navigation: Today, History, Settings
- Today's scheduled doses from `GET /api/patient/today`
- Dose cards with time, medication snapshot, and status
- Confirmed dose recording through `POST /api/patient/dose-records`
- Ten-minute local reminder using AlarmManager and notification permission
- Current-month history from `GET /api/patient/history/month`
- Patient session unlink from Settings
- Loading, empty, success, and API error states

The UI establishes the patient tab structure and shared teal identity. Patient Today's typical light state has passed the current-main paired high-fidelity comparison; the remaining state matrix and device/accessibility checks are still governed by Gate C.

## Residual verification requirements

- Live API recording and concurrent inventory behavior
- Dark mode, 130%/200% font, TalkBack and compact/large devices
- Physical AlarmManager delivery, process death and notification taps
- The remaining current-main paired screen captures in C01/C04/C05

PRN recording, slot bulk/partial inventory, history-day detail, recurring notification settings and tutorial coachmarks are implemented and covered by automated tests; they are no longer missing implementation items.

## 2026-07-14 C03 current Patient Today parity evidence

- `TodayContent` now follows the current iOS information hierarchy: icon/date header, next-dose hero, orange PRN entry, planned section progress, then canonical slot/dose cards.
- The next-dose hero shows slot/time, pill and medication totals, medication rows with status icons, the primary bulk action, recording-window state and inventory warnings. Completed schedules render the iOS-equivalent all-done card.
- PRN medications moved from an always-expanded block to a scrollable modal list opened by the iOS-equivalent entry card; existing single-flight, inventory and confirmation contracts remain intact.
- `PatientModePreview` is deterministic at 2026-07-14 12:00 JST and mirrors the UI-101 sample data. It no longer depends on wall-clock timing, so reference captures are repeatable.
- Patient bottom navigation now uses calendar/history/settings icons and the current `今日` label. Teal and orange card borders match the iOS emphasis system while retaining Material touch behavior.
- API-35 Compose coverage asserts next/PRN/planned order, bulk recording, partial inventory warning, PRN sheet/action, canonical details, success/warning coexistence and the existing empty/updating states.
- Paired evidence is recorded in `evidence/c03-20260714/`; `./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest` passes with all 34 instrumentation tests.

## Phase 2A contract hardening evidence

### PT-001 patient Today contract

- `PatientDose` now retains the complete iOS `ScheduleDoseDTO` contract: patient and medication IDs, ISO-8601 schedule, effective status, recorder type, name, dosage text, quantity, strength value/unit, and optional notes.
- Missing optional status, recorder, and notes fields are parsed safely; required identity and medication snapshot fields remain strict.
- Repository loading sorts exactly like iOS: pending, missed, taken; then scheduled time.
- The existing centralized patient-auth refresh/retry path applies to the Today request.
- Fixture tests cover the complete response, optional fields, and authorization header.

### PT-002 patient slot-time contract

- Android fetches `/api/patient/slot-times` and validates all four `HH:mm` values.
- A scheduled dose is assigned by exact patient-configured Tokyo time first, then the same iOS fallback ranges: morning 04–10, noon 11–15, evening 16–20, bedtime 21–03.
- Today grouping now consumes the resolved `MedicationSlot`; the former standalone hour-range function has been removed.
- When slot-time synchronization alone fails, Today still loads using the last known values, or the backend canonical defaults on first load (`08:00`, `13:00`, `19:00`, `22:00`).
- Tests cover custom exact times, every fallback range, malformed server values, repository propagation, and endpoint-only failure fallback.

`PT-001` and `PT-002` are `IMPLEMENTED`. A real patient session with non-default slot times, matched Today screenshots, lifecycle checks, and physical-device verification remain before `VERIFIED`.

### PT-003 next-action selector foundation

- The iOS candidate contract is mirrored exactly: slot, scheduled time, remaining count, recording-window state, and recordable-inventory state.
- Selection excludes completed, inventory-blocked, and past/closed candidates, then selects the earliest eligible schedule.
- Tests cover current-window precedence, past-slot skipping, future selection, inventory blocking, and no-action results.

This selector foundation is connected to inventory-backed production candidates and the Today UI in the Phase 2B slice below.

## Phase 2B Today recording vertical slice

### Medication and inventory contract

- Android now retains the complete iOS `MedicationDTO` surface used by Today, including regular/PRN state, instructions, lifecycle dates, inventory settings/quantity/out state, next schedule, and regimen metadata.
- `isOutOfStock` and `isInsufficientForDose` use the same iOS rules; a nonzero quantity that is still below `doseCountPerIntake` is treated as insufficient.
- Today loads schedule, patient slot times, and medications into one repository state, then builds inventory-aware next-slot candidates.

### Individual and slot-bulk recording

- Individual recording is blocked before the request when the current medication quantity cannot satisfy one dose; the backend remains the authoritative concurrent check.
- `/api/patient/dose-records/slot` has a typed request/response covering all counts, pill/med totals, slot time, four-slot summary, and optional recording group.
- Slot cards show the number of recordable and insufficient doses, expose one updating state per slot, and preserve server partial-success semantics.
- A partial result marks only inventory-sufficient doses taken; insufficient doses remain pending and the result message names both counts.
- A zero-update result never performs an optimistic local status transition.

### PRN recording

- Active, unarchived PRN medications appear in their own Today section with instructions.
- Recording requires confirmation and calls the patient-scoped `/api/patients/{patientId}/prn-dose-records` route with server-default time and quantity.
- Submission is single-flight and inventory insufficient PRN medications are disabled locally while remaining server-authoritative.

### Verification

- Contract fixtures cover medication inventory/regimen data, slot-bulk partial response, request bodies, and patient-scoped PRN routing.
- Repository tests cover local inventory prevention, partial bulk state transitions, success messages, and PRN progress cleanup.
- A production-component Compose test covers next-slot presentation, insufficient warning, disabled individual action, eligible bulk count/action, PRN instructions, and PRN action.

`PT-003` through `PT-009` are `IMPLEMENTED`. Matched iOS screenshots, real patient inventory concurrency, live recording-window behavior, lifecycle refresh, and physical-device verification remain before `VERIFIED`.

## Gate B rebaseline evidence (2026-07-14)

- Patient endpoints now decode with Kotlin serialization wire DTOs and map into domain models; raw `JSONObject` parsing no longer exists in `PatientApi`.
- Contract tests cover complete and optional payloads, unknown-field tolerance, required-field failure, and both current/legacy history top-level keys.
- App session state, caregiver patient selection, patient feature data, notification preferences, and patient navigation now have distinct owners.
- Patient tab/detail/history navigation survives saved-instance-state recreation; mutation confirmation dialogs intentionally do not survive recreation.
- Compose UI reads neither credentials nor `RequestAuthPolicy`; endpoint policy remains in data sources.
- All Compose UI, patient repository, session/auth/network fallback and local notification copy is resource-backed through typed presentation messages. Safe backend validation detail remains explicit raw data by contract.
- Patient UI is physically split into Shell, navigation state, Today, History, Settings, Tutorial and shared component files.
- Deterministic Today, History and Settings capture fixtures render the production components rather than screenshot-only duplicates.
- `./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest` passed with 34 emulator tests on API 35 after adding UI-001 200% font-scale action reachability and production caregiver-auth flow coverage.

## Phase 2B Today completion slice

### PT-010 dose detail

- Every scheduled-dose card opens a modal detail surface built from the already loaded schedule and medication contracts.
- The surface mirrors the current iOS production hierarchy: centered navigation-equivalent title, medication/dosage/Tokyo schedule/status header, memo with an explicit empty fallback, and per-intake quantity.
- Current iOS no longer renders separate strength or inventory cards on this patient surface. A paired iOS XCUITest and API-35 Compose test verify the exact Japanese copy, date/status treatment, fractional quantity and absence of those stale cards.

### PT-011 authoritative refresh

- The Today tab observes lifecycle `ON_RESUME` and reloads schedule, slot times, medications, and inventory after returning from the background.
- Individual, bulk, and PRN success paths perform a quiet authoritative resync after their immediate UI response.
- Quiet refresh keeps the success/partial-success message visible and retains the optimistic state if the follow-up request alone fails.
- Repository coverage proves a second Today fetch consumes server truth without losing the success message.

### PT-012 complete screen states

- Initial loading uses a centered progress state; refresh with existing content uses a non-destructive updating banner.
- Empty, inline error/retry, success, partial-success, individual progress, slot progress, and PRN progress states are distinct.
- While a screen refresh is active, scheduled-dose, reminder, bulk, and PRN actions are disabled to prevent overlapping mutations.
- Today navigation, cards, detail surfaces, and supporting text now consume semantic light/dark theme colors rather than light-only constants.
- Slot bulk actions are exposed only inside the backend-equivalent recording window (scheduled time minus 30 minutes through plus 60 minutes) and require an explicit confirmation showing medication and pill totals.

`PT-010`, `PT-011`, and `PT-012` are `IMPLEMENTED`. Real API recordings, matched iOS light/dark captures, process/background timing on a physical device, large text, and TalkBack remain before `VERIFIED`.

## Phase 2C patient history vertical slice

### 2026-07-14 C04 current-iOS rebaseline

- Current iOS no longer exposes the month calendar or day-detail navigation in patient mode. Android now renders the same simplified hierarchy: Today progress, This Week and Recent Records.
- Today's card derives its denominator from active morning/noon/evening/bedtime slots, uses taken/remaining/missed pills and applies the iOS no-plan, missed, complete, partial and start message priority.
- The Monday-first seven-day card counts fully recorded days, renders taken/pending/missed/none icons and applies the same recorded-count/consecutive-day encouragement thresholds.
- Recent Records summarizes today and yesterday with active slot names, PRN-only/no-plan fallback, and done/pending/missed state treatment.
- The production preview accepts a fixed History destination and fixed Tokyo date/data, so UI-104 screenshots do not depend on the wall clock.

### Retained month/day foundation

- Android implements the full `/api/patient/history/day` contract, including scheduled medication ID/name/dosage/quantity/time/slot/status/recorder and PRN medication/time/quantity/actor.
- The previous month calendar and day-detail components remain available in code for the current caregiver history contract, but are no longer reachable from the patient tab.
- Scheduled records still identify patient/caregiver recording, while PRN records identify their actor separately; API and Compose tests remain green.

### PH-004 history retention

- `HISTORY_RETENTION_LIMIT` remains a typed non-auth error and never clears the patient session.
- Month and day loads expose cutoff date and retention days separately from generic errors.
- Lock surfaces state the allowed number of recent days and the first viewable date; normal calendar/detail content is suppressed while locked.

### Verification

- API fixtures continue to cover month slot summaries, PRN counts and day scheduled/PRN recorder contracts.
- Repository tests cover current-month freshness, month/day retention state and populated day details.
- Android 15 Compose tests cover current progress/empty/missed states, week count, recent summaries, retained day rows/recorders and retention copy.
- `./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest` passes with 36/36 instrumentation tests; deterministic light evidence is recorded under `evidence/c04-20260714/`.

`PH-001` through `PH-004` are `IMPLEMENTED` against current main. Real historical patient data, entitlement changes, paired iOS captures, large text, TalkBack and physical-device verification remain before `VERIFIED`.

## Phase 2C patient settings and notifications

### PH-005 notification preferences

- Patient notification preferences persist independently from session secrets: master enabled, all four slot toggles, and the 15-minute re-reminder toggle.
- Slot and re-reminder controls are disabled when the master switch is off, matching the preference hierarchy rather than silently scheduling disabled notifications.
- Enabling notifications on Android 13+ requests `POST_NOTIFICATIONS`; denial restores the master setting to off.

### PH-006 deterministic schedule rebuild

- `PatientNotificationPlanBuilder` mirrors the iOS rules: Tokyo calendar, today through six days ahead, pending slots only, enabled slots only, patient-configured slot times, and an optional second entry exactly 15 minutes later.
- Past entries, taken/missed/none slots, disabled slots, dates beyond seven days, and all entries when master is off are excluded deterministically.
- Rebuild replaces the complete AlarmManager plan so removed slots and disabled re-reminders do not leave stale alarms.
- Settings changes and app foreground trigger a rebuild; disabling master or unlinking cancels all stored request codes.
- Successful scheduled individual/positive bulk recording advances a notification-plan revision after immediate UI success; application-scope maintenance rebuilds asynchronously.
- PRN and zero-update bulk do not rebuild scheduled alarms. Maintenance failure is reported separately without reversing the accepted medication record.

### PH-007 server-first unlink and PH-008 support routes

- Destructive unlink requires confirmation and calls `DELETE /api/patient/session` before any local session mutation.
- Only server success disables notifications, clears the patient session, and returns to mode selection. Failure remains on settings with an inline error.
- Privacy, terms, and support use the canonical production destinations and Android external browser intents:
  - `https://www.okusuri-mimamori.com/privacy`
  - `https://www.okusuri-mimamori.com/terms`
  - `https://www.okusuri-mimamori.com/support`

### Verification

- Pure JVM tests cover master-off behavior, seven-day filtering, pending/enabled filtering, custom Tokyo times, chronological ordering, and exact 15-minute secondary delay.
- Repository tests cover successful and failed server revocation.
- Android 15 Compose tests cover settings content, canonical destinations, scroll behavior, and explicit unlink confirmation.

`PH-005` through `PH-008` are `IMPLEMENTED`. Physical notification permission, Doze/alarm delivery, external browser return, real server revocation, iOS comparison captures, large text, and TalkBack remain before `VERIFIED`.

## 2026-07-14 C05 current Patient Settings parity

- Settings now follows current iOS card order and copy: gear header, master notification toggle, linked status, analytics consent, explained legal/support links, permission-denied guidance and red logout action.
- Obsolete visible slot/re-reminder toggles were removed from this screen; their persisted values and scheduler behavior remain intact for notification-plan generation.
- Android records explicit analytics consent in a dedicated preference, defaults it to off and exposes no patient, medication, dose, email or free-text fields. This is consent readiness only: Firebase SDK configuration, wrapper enforcement, disabling/reset and console verification remain Gate H.
- Notification denial is shown only after the app has requested permission; returning from system settings refreshes the state. Enabling the master toggle still gates Android 13 permission before saving the enabled state.
- Privacy, terms and support retain the canonical HTTPS destinations and now show the current explanatory subtitles. Logout retains server-first revocation and only clears local mode/notifications after success.
- `PatientSettingsContentTest` covers analytics toggle callback, three destinations, explicit logout confirmation and denied-permission disabling/guidance.
- Full gate passes with 37/37 API-35 instrumentation tests; deterministic light evidence is under `evidence/c05-20260714/`.

## Phase 2D notification routing and patient tutorial

### XP-002 exact notification destination

- Every scheduled notification carries its canonical `YYYY-MM-DD` date, medication slot, and primary/secondary sequence.
- Notification content PendingIntents use a date/slot-derived request code so multiple notifications cannot overwrite each other's destination.
- Cold and warm Activity intents validate date and slot before publishing a one-time repository target.
- A target for today switches to Today, reloads current data, and highlights the exact slot. A historical target switches to History, loads the correct month, and opens the exact day detail.
- Malformed dates and unknown slots are rejected without changing navigation state.

### XP-003 patient real-screen tutorial

- First patient-mode use displays four steps over the production screen: Today, History, Settings/notifications, and notification permission education.
- Step progress changes the real selected tab rather than displaying disconnected mock screens.
- Back, next, skip, progress indicators, and persisted one-time completion are implemented.
- The final action requests Android notification permission and only enables notifications when permission is granted.
- Compose tests cover canonical copy and all tutorial actions; caregiver tutorial coverage is recorded in Phase 3.
- Production-shell integration tests drive the real tab sequence, persist both Skip and final completion, and verify the final action crosses the notification-permission boundary exactly once.
- The overlay now follows the current iOS senior-friendly contract: exact copy, per-step icon, 18% scrim, 16 dp elevated card, 48 dp icon treatment, circular back action, icon-bearing primary action and 104 dp clearance above the persistent patient navigation bar.
- Current-iOS and API-35 Android default-size captures for all four steps are paired under `evidence/c01-20260714/`. Android fixtures render each state independently over the production patient preview and capture the complete device display to prevent transition or alpha artifacts.

`XP-002` and the patient portion of `XP-003` are `IMPLEMENTED`. Default-size, completion/skip and largest-text matched tutorial evidence is complete; physical notification taps, process-death routing and TalkBack remain before `VERIFIED`.

### XP-005 patient accessibility hardening

- Current patient history exposes a semantic heading plus textual progress, encouragement, week and recent-record summaries without relying on color alone.
- The tutorial overlay announces itself as a named pane with current progress.
- A 200% font-scale instrumentation test scrolls the production tutorial to the final controls, verifies skip/back/permission actions remain operable, and emits a direct device-display reference capture.
- 200% font-scale instrumentation also verifies Today's primary bulk/planned content, History's recent/today content, and Settings' analytics/logout controls remain reachable through their production scroll containers.
- Each History week cell merges weekday, date and status into a single localized TalkBack announcement instead of exposing decorative fragments.
- API-35 dark captures for the three primary patient screens and a 200% Today capture are recorded under `evidence/c06-20260714/`.
- TalkBack semantics and font scaling are covered for the highest-risk patient surfaces; the matrix remains `PARTIAL` pending 130%, complete screen traversal, caregiver UI, matched iOS variants and physical-device screen-reader verification.

Additional known gaps are the remaining current-iOS paired captures, complete dark-state comparison, full large-text/TalkBack audits and physical-device evidence.
