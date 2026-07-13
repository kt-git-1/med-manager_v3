# Android Phase 2: Patient Mode

**Status: PARTIAL / SCAFFOLDED. This is not iOS parity completion.**

This file records the first working path. Completion is governed by the `PT-*`, `PH-*`, and relevant `XP-*` rows in `parity-requirements.md`.

## Implemented foundation

- Patient-only bottom navigation: Today, History, Settings
- Today's scheduled doses from `GET /api/patient/today`
- Dose cards with time, medication snapshot, and status
- Confirmed dose recording through `POST /api/patient/dose-records`
- Ten-minute local reminder using AlarmManager and notification permission
- Current-month history from `GET /api/patient/history/month`
- Patient session unlink from Settings
- Loading, empty, success, and API error states

The UI establishes the patient tab structure and a preliminary teal identity. It has not passed the high-fidelity process in `ui-fidelity-spec.md`.

## Missing parity requirements

- PRN medication recording
- Slot-level bulk recording and partial inventory messaging
- Detailed history-day screen
- Configurable recurring notification schedule
- Patient tutorial/coachmarks

These remain part of later parity work because they depend on medication detail, slot-time, and notification scheduling surfaces beyond the Phase 2 core flow.

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

## Phase 2B Today completion slice

### PT-010 dose detail

- Every scheduled-dose card opens a modal detail surface built from the already loaded schedule and medication contracts.
- The surface includes medication name, dosage text, Tokyo schedule, effective status, notes with an explicit empty fallback, per-intake quantity, strength/unit, and inventory when tracking is enabled.
- A Compose test verifies status, notes, fractional quantity formatting, strength, and inventory content.

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

## Phase 2C history calendar vertical slice

### PH-001 month calendar and PH-002 legend

- The former reverse chronological card list has been replaced by a Sunday-first seven-column month calendar.
- Each day retains and renders all four slot summary values independently; missing slots remain visible as the neutral state rather than being discarded.
- PRN counts are shown on their calendar day, month navigation reloads the requested server month, and every date is selectable.
- A persistent four-item legend above the calendar maps taken, missed, pending, and no-plan states to the same semantic colors used by day indicators.

### PH-003 day detail

- Android implements the full `/api/patient/history/day` contract, including scheduled medication ID/name/dosage/quantity/time/slot/status/recorder and PRN medication/time/quantity/actor.
- Selecting a calendar date opens a modal detail surface immediately, then shows loading, content, empty, retention, or retryable error state.
- Scheduled records identify patient/caregiver recording, while PRN records identify their actor separately.

### PH-004 history retention

- `HISTORY_RETENTION_LIMIT` remains a typed non-auth error and never clears the patient session.
- Month and day loads expose cutoff date and retention days separately from generic errors.
- Lock surfaces state the allowed number of recent days and the first viewable date; normal calendar/detail content is suppressed while locked.

### Verification

- API fixtures cover month slot summaries and PRN counts plus day scheduled/PRN recorder contracts.
- Repository tests cover month/day retention state and populated day details.
- Android 15 Compose tests cover month heading/navigation, all legend labels, day selection, PRN badge, scheduled/PRN detail rows, recorder labels, and retention copy.

`PH-001` through `PH-004` are `IMPLEMENTED`. Real historical patient data, entitlement changes, iOS comparison captures, large text, TalkBack, and physical-device month/day navigation remain before `VERIFIED`.

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
- Compose tests cover canonical copy and all tutorial actions; the caregiver tutorial remains intentionally scoped to Phase 3.

`XP-002` is `IMPLEMENTED`; the patient portion of `XP-003` is `IMPLEMENTED` while the matrix remains `PARTIAL` until caregiver parity. Physical notification taps, process-death routing, large text, TalkBack, and matched iOS tutorial captures remain before `VERIFIED`.

### XP-005 patient accessibility hardening

- Calendar dates no longer expose only a day number and unlabeled colored dots. Each selectable date announces the full Japanese date, morning/noon/evening/bedtime status, and PRN count.
- The history title is marked as a semantic heading and the tutorial overlay announces itself as a named pane with current progress.
- A 200% font-scale instrumentation test verifies the final tutorial step keeps its title and skip/back/permission actions operable.
- TalkBack semantics and font scaling are covered for the highest-risk patient surfaces; the matrix remains `PARTIAL` pending the complete screen audit, caregiver UI, and physical-device screen-reader verification.

Additional known gaps include server-resolved slot times, next-action ordering, exact loading/updating overlays, history calendar layout, dark mode, large-text verification, and physical-device evidence.
