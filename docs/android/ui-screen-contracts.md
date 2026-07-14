# Android UI and Screen Contracts

**Pinned reference:** `main@1d9d19e`
**Primary locale/time zone:** `ja_JP` / `Asia/Tokyo`

This file defines what “high-fidelity reproduction” means at screen level. It supplements `ui-fidelity-spec.md`; it does not replace live comparison with the pinned iOS build.

## 1. Information architecture

### Entry

1. Mode selection
2. Patient mode: six-digit link code -> patient shell
3. Caregiver mode: auth choice -> login or signup/confirmation -> caregiver shell

### Patient shell

Three persistent tabs: Today, History, Settings. Tabs are created lazily and remain alive after first visit. A hidden tab cannot receive input or accessibility focus.

### Caregiver shell

Five persistent tabs in this order: Today, Medications, Inventory, History, Link/Settings. Today is the initial tab. The selected patient's ID is persisted; a sole patient is auto-selected, an invalid stored ID is cleared, and zero/multiple-with-no-selection states remain explicit.

## 2. Canonical visual tokens

Android tokens are derived from `Shared/AppConstants.swift` and role UI aliases, not Material defaults.

| Semantic token | Light reference | Dark reference/behavior |
|---|---|---|
| primary teal | RGB `(0, 140, 128)` | RGB `(0, 158, 153)` |
| teal text | RGB `(0, 110, 102)` | RGB `(138, 230, 222)` |
| caregiver blue | RGB `(31, 122, 209)` | same unless contrast testing requires an approved semantic variant |
| orange | RGB `(240, 107, 0)` | semantic role color |
| indigo | RGB `(87, 82, 199)` | semantic role color |
| patient danger | RGB `(219, 46, 51)` | semantic danger color |
| caregiver danger | RGB `(209, 41, 41)` | semantic danger color |
| screen background | RGB `(242, 250, 252)` | RGB `(36, 46, 48)` |
| card background | secondary grouped surface | RGB `(56, 69, 71)` |
| elevated background | tertiary grouped surface | RGB `(69, 79, 82)` |
| card stroke | primary text at 10% | theme-aware |
| patient/caregiver shadow | black 7% / 6% | preserve hierarchy without muddy elevation |

Shape family is named and centralized: common cards 18dp, caregiver bottom bar 18dp, patient bottom bar 28dp, tab selections 16–18dp, tutorial card 16dp. Unexplained screen-local color, radius or elevation literals are a review failure.

## 3. Copy and localization contract

- `ios/MedicationApp/Resources/Localizable.strings` at the pinned commit is the source for Japanese wording.
- Android production UI uses `strings.xml` resources. User-visible Japanese literals in Kotlin are prohibited except deterministic fixture data.
- Formatting placeholders are typed and tested; Android plural/number formatting must preserve the same meaning.
- Backend error messages are not displayed directly when canonical app copy exists.
- `Localizable.strings` currently contains hundreds of feature strings; copy parity is verified by key groups per vertical slice, not by ad hoc visual reading.

## 4. Screen inventory and required states

Each screen ID maps to parity requirements. Every listed state needs a deterministic Compose fixture and, where visually meaningful, an iOS reference capture.

### Entry and authentication

| Screen | iOS reference | Required states/actions |
|---|---|---|
| UI-001 Mode select | `ModeSelectView.swift` | default, dark, large text, patient/family selection, analytics consent prompt, back restoration |
| UI-002 Patient link | `LinkCodeEntryView.swift` | empty, 1–5 digits, valid six digits, submitting, validation, expired/not-found, forbidden, network, rate limit, success |
| UI-003 Caregiver auth choice | `CaregiverAuthChoiceView.swift` | login/signup actions, mode reset, dark/large text |
| UI-004 Login | `CaregiverLoginView.swift` | empty, invalid, loading, auth failure, callback landing, keyboard/IME |
| UI-005 Signup/confirmation | `CaregiverSignupView.swift` | validation, password mismatch, loading, session success, confirmation required, resend cooldown, 429, resend success |

### Patient mode

| Screen | iOS reference | Required states/actions |
|---|---|---|
| UI-100 Patient shell/tutorial | `PatientReadOnlyView.swift`, `GuidedTutorialOverlay` | three live tabs; four tutorial steps; skip/back/next/final permission; persisted completion; 200% font |
| UI-101 Today | `PatientTodayView.swift`, view model | initial load, empty, typical, long names, pending/taken/missed, insufficient, updating, retry, success/partial, notification highlight |
| UI-102 Dose detail | current `PatientTodayDoseDetailView` | navigation title; medication, dosage, time and status header; notes/empty notes; per-intake quantity; cache-first loading overlay and isolated retryable medication-detail error; dismiss resets transient detail state. Current iOS does not render separate strength or inventory cards here. |
| UI-103 PRN | current `PrnMedicationListView` | entry exists only when active PRN medication exists; active list, dosage/count, instruction-or-notes, confirmation, insufficient, submitting overlay, success-only dismissal and in-flow error. Current iOS has no reachable empty PRN list screen. |
| UI-104 Patient history | current `HistoryMonthView.patientSimpleHistory` | title/subtitle, today progress ring and pills, no-plan/partial/complete/missed encouragement, Monday-first current week, recent today/yesterday summaries, loading/retry/retention |
| UI-105 Retained history detail contract | `HistoryDayDetailView.swift`, day API | no longer reachable from current patient UI; keep scheduled/PRN/recorder/empty/retry/retention behavior as caregiver UI-206 foundation |
| UI-106 Patient settings | patient settings in `PatientReadOnlyView.swift` | master/slot/re-reminder preferences, permission denied, analytics consent, legal/support, unlink confirmation/failure/success |

### Caregiver mode

| Screen | iOS reference | Required states/actions |
|---|---|---|
| UI-200 Caregiver shell/tutorial | `CaregiverHomeView.swift` | five persistent tabs, lazy first load, selection restoration, low-stock badge, push/history deep link, full tutorial sequence |
| UI-201 Today | `CaregiverTodayView.swift` | no patient, data unavailable, empty, content, pending/taken/missed, individual/bulk/PRN, delete, insufficient, mutation-success/refresh-failure preservation |
| UI-202 Medication list | `MedicationListView.swift` | no patient, empty onboarding, all/scheduled/PRN/ended filters, long names, inventory status, loading/updating/retry |
| UI-203 Medication form | `MedicationFormView.swift` | add/edit, scheduled/PRN, validation, date range, daily/weekday, slots, inventory, notes, keyboard, unsaved dismissal |
| UI-204 Inventory list | `InventoryListView.swift` | no patient, empty, all/low/out filters, badge/metrics, loading/updating/retry |
| UI-205 Inventory detail | `InventoryDetailView.swift` | tracking off/on, quantity, refill/adjust, validation, saving, success/failure |
| UI-206 Caregiver history | caregiver/history views | no patient, month/day, exact push destination/highlight, stale-data refresh, retention and PDF action |
| UI-207 PDF period/share | PDF feature files | free lock, presets, custom range errors, generating overlay, success share, generation failure; absent in patient mode |
| UI-208 Link/settings | `PatientManagementView.swift` | load error, zero/one/multiple patients, create/limit, selection, time presets, code issue/share, revoke/delete, push, analytics, legal, logout/account delete |

## 5. Interaction-state rules

### Successful mutation followed by failed refresh

The write result is final. Keep the last rendered content, clear the mutation spinner, show success feedback, then report the refresh problem non-destructively. Never replace a successful screen with an empty state because the follow-up GET failed.

### Tab visibility and lifetime

- Construct a tab on first selection.
- Keep its state holder and scroll state alive thereafter.
- Hidden tabs use no hit testing and are accessibility-hidden.
- Selecting a tab may trigger a freshness check, but must not flash an initial loader over cached content.
- Dose mutation increments a shared history revision. Visible History refreshes; hidden History consumes the revision on next selection.

### Patient recording

- Patient individual/bulk recording has explicit confirmation and no undo.
- Patient slot action is available only in the server-equivalent recording window.
- At least one server-updated scheduled dose triggers reminder-plan rebuild after immediate success rendering.
- PRN updates history freshness but does not remove unrelated scheduled reminders.

### Caregiver recording

- Caregiver may record/cancel individual doses and record eligible older missed slots.
- Patient window restrictions are not reused for caregiver proxy actions.
- Every mutation invalidates caregiver Today, History and Inventory as appropriate.

### Destructive actions

- Revoke preserves patient data but invalidates linked patient-device sessions.
- Delete permanently removes the patient only after server success.
- Account deletion performs server deletion before local tokens, selected patient and push state are erased.
- Android back during a destructive request does not duplicate the request or expose a completed screen as pending.

## 6. Accessibility contract

- Minimum interactive target: 48x48dp.
- Default, 1.3x and 2.0x font scales are required. At 2.0x, scrolling/wrapping is acceptable; primary actions must remain reachable.
- State is never color-only. Status text/icon and TalkBack semantics are required.
- Dose semantics include medication, time, status and action availability.
- Calendar-day semantics include full date, all four slot states and PRN count.
- Selected tabs expose selected state; hidden tabs and tutorial sample layers are not announced.
- Modal/sheet/tutorial surfaces expose pane titles and logical focus order.
- Motion respects Android reduced-animation settings where available; feedback is not dependent on animation.

## 7. Screenshot protocol

Use identical deterministic data on both platforms:

- Japanese locale, Tokyo time zone
- Same clock instant and calendar month
- Same names, dosage, slot times, status, inventory and entitlement
- Compact phone and larger phone
- Light and dark themes
- Font scales 1.0 and 2.0; 1.3 where truncation risk exists
- System bars/insets normalized for comparison

For each state, retain:

1. iOS reference screenshot and source SHA
2. Android screenshot and build SHA
3. side-by-side image
4. normalized overlay/diff
5. written disposition for material differences

Pixel difference is diagnostic. Acceptance is based on hierarchy, spacing rhythm, typography, shapes, semantic color, visibility rules and interaction equivalence. Platform system dialogs, share sheets, browser UI, keyboard and back behavior may be Android-native.

## 8. UI acceptance gate

A screen can be `VERIFIED` only when:

- All required states use the production component tree.
- Japanese copy matches resource keys.
- No unresolved material visual difference remains.
- TalkBack, 48dp targets and font-scale checks pass.
- Keyboard, back, insets, scrolling and process recreation are checked.
- Emulator and physical-device evidence is linked from the parity matrix.
