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

Additional known gaps include server-resolved slot times, next-action ordering, exact loading/updating overlays, history calendar layout, dark mode, large-text verification, and physical-device evidence.
