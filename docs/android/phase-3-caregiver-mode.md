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
