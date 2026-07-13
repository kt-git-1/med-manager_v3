# Android Parity Requirements Matrix

This file tracks product parity. Status is conservative: a working happy path remains `PARTIAL` until state, visual, emulator, and physical-device evidence is complete.

## Shared foundation

| ID | Requirement | iOS/backend references | Android status | Acceptance evidence |
|---|---|---|---|---|
| SH-001 | Restore last patient/caregiver mode | `SessionStore.swift`, `SessionStoreTests.swift` | IMPLEMENTED | Android unit tests; device verification pending |
| SH-002 | Encrypt access/refresh tokens at rest | `SessionKeychainStore`, `SessionStore.swift` | IMPLEMENTED | Keystore AES-GCM implementation; physical-device verification pending |
| SH-003 | Refresh caregiver Supabase session before expiry | `AuthService.swift`, `SessionStore.swift` | IMPLEMENTED | Near-expiry refresh, concurrent coalescing, selection preservation, and failure transition tested; physical verification pending |
| SH-004 | Refresh patient session through API | `/api/patient/session/refresh`, `APIClient.swift` | IMPLEMENTED | Contract/parser/repository tests pass; physical lifecycle verification pending |
| SH-005 | Map auth/API errors to safe Japanese messages | `APIError.swift`, `AuthService.swift` | IMPLEMENTED | General HTTP families plus inventory, patient-limit, retention, and rate-limit contracts tested; specialized screen UI remains in feature rows |
| SH-006 | Handle unauthorized session consistently | `APIClient.send`, `SessionStore.handleAuthFailure` | IMPLEMENTED | Proactive failure, refresh failure, single retry, second-401 invalidation tested; physical verification pending |
| SH-007 | Restore state after process death | iOS session behavior | IMPLEMENTED | Keystore persistence, new-instance restoration, expired-token rejection, and emulator force-stop/cold launch verified; physical device pending |
| SH-008 | Shared light/dark design tokens | `AppTheme`, `PatientUI`, `CaregiverUI` | IMPLEMENTED | iOS semantic palette, surface hierarchy, role/status/slot colors, shadows, automatic system theme, and token tests complete; remaining legacy patient consumers migrate within their feature rows and physical visual verification is pending |

## Entry and authentication

| ID | Requirement | References | Android status | Missing/acceptance |
|---|---|---|---|---|
| AU-001 | Mode-select information hierarchy and visuals | `ModeSelectView.swift` | IMPLEMENTED | Canonical copy, illustrations, hierarchy, colors, cards, actions, screenshot comparison, and Compose interaction tests complete; dark/large-text/physical verification pending |
| AU-002 | Patient six-digit linking flow | `LinkCodeEntryView.swift`, `/api/patient/link` | IMPLEMENTED | High-fidelity structure, six-digit sanitization, disabled/loading/error/success logic, canonical messages, comparison capture, repository and Compose tests complete; real-code/physical verification pending |
| AU-003 | Caregiver auth choice | `CaregiverAuthChoiceView.swift` | IMPLEMENTED | Login/signup choice hierarchy, canonical copy, back action, and Compose interaction tests complete; final cross-platform visual tuning pending |
| AU-004 | Caregiver login | `CaregiverLoginView.swift`, `AuthService.login` | IMPLEMENTED | Compose form, disabled/loading/error states, Supabase login, and confirmation-link login landing complete; real credential/physical verification pending |
| AU-005 | Caregiver signup and password confirmation | `CaregiverSignupView.swift` | IMPLEMENTED | Compose form, local email/password/confirmation validation, Supabase signup, and confirmation state tests complete; real email delivery pending |
| AU-006 | Confirmation email callback routing | `SessionStore.handleIncomingURL` | IMPLEMENTED | HTTPS App Links and custom scheme routing, allowlist/rejection tests, stale-session clearing, emulator cold-launch check complete; domain association/physical verification pending |
| AU-007 | Resend confirmation cooldown and 429 handling | `resendSignupConfirmation`, signup view | IMPLEMENTED | Supabase resend payload, 60-second UI cooldown, status 429 message, and repository resend test complete; live rate-limit verification pending |

## Patient Today

| ID | Requirement | References | Android status | Missing/acceptance |
|---|---|---|---|---|
| PT-001 | Load today's patient schedule | `PatientTodayViewModel`, `/api/patient/today` | IMPLEMENTED | Full iOS-equivalent dose/snapshot contract, optional fields, auth-header path, status/time sorting, repository state, fixtures, and contract tests complete; real-session/visual verification pending |
| PT-002 | Use server-resolved patient slot times | `NotificationPreferencesStore`, `/api/patient/slot-times` | IMPLEMENTED | Four-value typed contract, strict HH:mm validation, Tokyo exact-match plus canonical range fallback, repository integration, resilient cached/default fallback, and grouping replacement complete; real custom-time verification pending |
| PT-003 | Match iOS next-action/slot ordering | `PatientTodayNextSlotSelector`, `PatientTodayView` | IMPLEMENTED | iOS-equivalent selector, inventory-backed production candidates, recording-window filtering, next-slot rendering, and boundary/Compose tests complete; visual/physical verification pending |
| PT-004 | Render pending/taken/missed states | `PatientTodayView` | IMPLEMENTED | Distinct pending/taken/missed/inventory-blocked labels, slot ordering, cards, and Compose rendering complete; final iOS visual tuning pending |
| PT-005 | Record individual scheduled dose | `/api/patient/dose-records` | IMPLEMENTED | Auth retry, confirmation, updating state, immediate success state, server error mapping, and inventory prevention complete; real idempotency smoke pending |
| PT-006 | Record slot in bulk | `/api/patient/dose-records/slot` | IMPLEMENTED | Typed request/response, per-slot progress, eligible-count action, result messages, and contract/repository/UI tests complete; real recording-window smoke pending |
| PT-007 | Preserve partial-success semantics | `SlotBulkRecordResponseDTO`, inventory spec | IMPLEMENTED | All response counts parsed; sufficient doses update while insufficient doses remain pending, with explicit partial-result message and tests |
| PT-008 | Show out-of-stock/insufficient inventory | `MedicationDTO`, `PatientTodayViewModel` | IMPLEMENTED | Full medication/inventory contract, quantity-per-dose comparison, blocked individual/bulk/PRN actions, warnings, and contract/repository/UI tests complete |
| PT-009 | List and record PRN medication | PRN DTO/routes and Today tests | IMPLEMENTED | Active PRN filtering, instruction display, confirmation, patient-scoped request, progress/error/success, and inventory blocking complete; real-session verification pending |
| PT-010 | Present medication/dose detail | `PatientTodayDoseDetailView` | IMPLEMENTED | Tappable dose cards and modal detail with status, schedule, notes fallback, quantity, strength, and inventory plus Compose coverage; final visual/physical verification pending |
| PT-011 | Refresh on foreground and after actions | `handleAppear`, foreground refresh | IMPLEMENTED | Lifecycle `ON_RESUME` loading and authoritative post-individual/bulk/PRN resync preserve success feedback; repository test complete, physical lifecycle verification pending |
| PT-012 | Today empty/error/updating overlays | `PatientTodayBaseView` | IMPLEMENTED | Initial progress, non-destructive refresh banner, empty/error/retry/success states, per-action progress, and global interaction blocking implemented; matched visual and large-text verification pending |

## Patient History and Settings

| ID | Requirement | References | Android status | Missing/acceptance |
|---|---|---|---|---|
| PH-001 | Month calendar with four slot statuses | `HistoryMonthView.swift` | IMPLEMENTED | Sunday-first seven-column calendar, four per-slot indicators, PRN counts, month navigation, day selection, semantic dark colors, contract and Compose tests complete; matched visual/physical verification pending |
| PH-002 | Always-visible status legend | history spec 004 | IMPLEMENTED | Taken/missed/pending/none legend remains visible above every loaded calendar and uses the same semantic colors as day cells; Compose coverage complete |
| PH-003 | Navigate to day details | `HistoryDayDetailView.swift`, day route | IMPLEMENTED | Typed day endpoint, modal loading/error/empty states, scheduled and PRN rows, status/time/quantity/recorder display, retry and contract/Compose tests complete; real-session verification pending |
| PH-004 | Handle history retention lock | `HistoryRetentionLockView.swift` | IMPLEMENTED | Structured 403 mapping, month/day repository state, generic-error suppression, cutoff/date messaging, lock surfaces, mapper/repository/Compose tests complete; entitlement/physical verification pending |
| PH-005 | Patient notification preferences | notification feature files | IMPLEMENTED | Persisted master, four slot toggles, 15-minute re-reminder, Android 13 permission gate, disabled-state hierarchy, and Compose coverage complete; physical permission verification pending |
| PH-006 | Rebuild notification schedule on settings/app lifecycle | scheduling coordinator | IMPLEMENTED | Deterministic Tokyo seven-day pending-only plan, custom slot times, enabled slots, secondary sequence, sorted replacement scheduling, settings/foreground triggers, cancel-all, and unit tests complete; physical alarm verification pending |
| PH-007 | Revoke server session before local unlink | `/api/patient/session` | IMPLEMENTED | Confirmed server-first DELETE, success-only local unlink/notification cancellation, failure preservation, repository and Compose tests complete; real-session verification pending |
| PH-008 | Legal/support links and mode change behavior | patient settings iOS UI | IMPLEMENTED | Exact privacy/terms/support HTTPS destinations, external browser intents, linked-state copy, destructive confirmation, and return-to-mode-select semantics complete; physical browser verification pending |

## Caregiver mode

| ID | Requirement group | References | Android status |
|---|---|---|---|
| CG-001 | Caregiver tab shell and selected-patient behavior | `CaregiverHomeView.swift` | NOT_STARTED |
| CG-002 | Patient list/create/select | `PatientManagementView`, `/api/patients` | NOT_STARTED |
| CG-003 | Linking code issue/share | `PatientLinkCodeView`, linking-code route | NOT_STARTED |
| CG-004 | Revoke/delete patient with correct semantics | revoke/delete views and routes | NOT_STARTED |
| CG-005 | Medication list and empty state | `MedicationListView.swift` | NOT_STARTED |
| CG-006 | Regular/PRN medication form and validation | `MedicationFormView*` | NOT_STARTED |
| CG-007 | Regimen schedule CRUD | regimen routes | NOT_STARTED |
| CG-008 | Caregiver Today and proxy dose record | `CaregiverTodayView*` | NOT_STARTED |
| CG-009 | Inventory list/detail/adjust | inventory views/routes | NOT_STARTED |
| CG-010 | Caregiver month/day history | caregiver/history views | NOT_STARTED |
| CG-011 | PDF period selection/report/share | PDF feature files/report route | NOT_STARTED |
| CG-012 | Push settings and self-exclusion behavior | push settings/routes | NOT_STARTED |
| CG-013 | Account deletion and session reset | settings and `/api/me` | NOT_STARTED |

## Cross-platform and release

| ID | Requirement | Android status |
|---|---|---|
| XP-001 | FCM token register/unregister lifecycle | NOT_STARTED |
| XP-002 | Notification tap deep link to exact date/slot | IMPLEMENTED | Local notification carries unique date/slot extras through PendingIntent/MainActivity/repository; today targets highlight the slot and historical targets open the exact day; parser/repository tests complete, physical notification-tap verification pending |
| XP-003 | In-context patient/caregiver tutorials | PARTIAL | Four-step patient real-screen tutorial, tab transitions, persisted completion/skip, back/next controls, and final permission action implemented with Compose tests; caregiver tutorial remains for Phase 3 |
| XP-004 | Analytics event parity with test/preview suppression | NOT_STARTED |
| XP-005 | Dynamic type/font scale and screen-reader semantics | PARTIAL | Patient calendar exposes complete date/four-slot/PRN TalkBack descriptions, headings/tutorial pane semantics added, and 200% tutorial font-scale tests pass; full patient screen audit, caregiver surfaces, and physical TalkBack verification remain |
| XP-006 | Dark mode visual parity | NOT_STARTED |
| XP-007 | Offline/retry behavior | PARTIAL |
| XP-008 | Physical-device matrix | NOT_STARTED |
| XP-009 | Google Play Data safety and health declarations | NOT_STARTED |
| XP-010 | Closed-test and signed production release | NOT_STARTED |
