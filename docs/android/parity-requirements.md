# Android Parity Requirements Matrix

This file tracks product parity. Status is conservative: a working happy path remains `PARTIAL` until state, visual, emulator, and physical-device evidence is complete.

## Shared foundation

| ID | Requirement | iOS/backend references | Android status | Acceptance evidence |
|---|---|---|---|---|
| SH-001 | Restore last patient/caregiver mode | `SessionStore.swift`, `SessionStoreTests.swift` | IMPLEMENTED | Android unit tests; device verification pending |
| SH-002 | Encrypt access/refresh tokens at rest | `SessionKeychainStore`, `SessionStore.swift` | IMPLEMENTED | Keystore AES-GCM implementation; physical-device verification pending |
| SH-003 | Refresh caregiver Supabase session before expiry | `AuthService.swift`, `SessionStore.swift` | IMPLEMENTED | Near-expiry refresh, concurrent coalescing, selection preservation, and failure transition tested; physical verification pending |
| SH-004 | Refresh patient session through API | `/api/patient/session/refresh`, `APIClient.swift` | IMPLEMENTED | Contract/parser/repository tests pass; physical lifecycle verification pending |
| SH-005 | Map auth/API errors to safe Japanese messages | `APIError.swift`, `AuthService.swift` | PARTIAL | Full backend error-code matrix missing |
| SH-006 | Handle unauthorized session consistently | `APIClient.send`, `SessionStore.handleAuthFailure` | IMPLEMENTED | Proactive failure, refresh failure, single retry, second-401 invalidation tested; physical verification pending |
| SH-007 | Restore state after process death | iOS session behavior | PARTIAL | Stored session exists; Android process-death test missing |
| SH-008 | Shared light/dark design tokens | `AppTheme`, `PatientUI`, `CaregiverUI` | SCAFFOLDED | Token extraction and dark-mode parity incomplete |

## Entry and authentication

| ID | Requirement | References | Android status | Missing/acceptance |
|---|---|---|---|---|
| AU-001 | Mode-select information hierarchy and visuals | `ModeSelectView.swift` | SCAFFOLDED | Current UI is simplified; comparison capture required |
| AU-002 | Patient six-digit linking flow | `LinkCodeEntryView.swift`, `/api/patient/link` | PARTIAL | Success works; exact UI/error/rate-limit parity missing |
| AU-003 | Caregiver auth choice | `CaregiverAuthChoiceView.swift` | NOT_STARTED | Login/signup choice UI required |
| AU-004 | Caregiver login | `CaregiverLoginView.swift`, `AuthService.login` | PARTIAL | Logic exists; UI parity and real credential smoke missing |
| AU-005 | Caregiver signup and password confirmation | `CaregiverSignupView.swift` | NOT_STARTED | Validation and confirmation state required |
| AU-006 | Confirmation email callback routing | `SessionStore.handleIncomingURL` | NOT_STARTED | App Links/custom scheme decision and tests required |
| AU-007 | Resend confirmation cooldown and 429 handling | `resendSignupConfirmation`, signup view | NOT_STARTED | Required before auth phase completion |

## Patient Today

| ID | Requirement | References | Android status | Missing/acceptance |
|---|---|---|---|---|
| PT-001 | Load today's patient schedule | `PatientTodayViewModel`, `/api/patient/today` | PARTIAL | Typed parsing exists; auth retry/fixtures/contract tests missing |
| PT-002 | Use server-resolved patient slot times | `NotificationPreferencesStore`, `/api/patient/slot-times` | NOT_STARTED | Current hour-based grouping must be replaced |
| PT-003 | Match iOS next-action/slot ordering | `PatientTodayNextSlotSelector`, `PatientTodayView` | NOT_STARTED | Selection and visibility tests required |
| PT-004 | Render pending/taken/missed states | `PatientTodayView` | PARTIAL | Basic state exists; exact per-slot/card behavior incomplete |
| PT-005 | Record individual scheduled dose | `/api/patient/dose-records` | PARTIAL | Happy path exists; idempotency/inventory/auth states missing |
| PT-006 | Record slot in bulk | `/api/patient/dose-records/slot` | NOT_STARTED | Confirmation and results required |
| PT-007 | Preserve partial-success semantics | `SlotBulkRecordResponseDTO`, inventory spec | NOT_STARTED | `updatedCount` and `insufficientCount` UI required |
| PT-008 | Show out-of-stock/insufficient inventory | `MedicationDTO`, `PatientTodayViewModel` | NOT_STARTED | Must not be UI-only enforcement |
| PT-009 | List and record PRN medication | PRN DTO/routes and Today tests | NOT_STARTED | Confirmation, quantity, updating states required |
| PT-010 | Present medication/dose detail | `PatientTodayDoseDetailView` | NOT_STARTED | Sheet/dialog parity required |
| PT-011 | Refresh on foreground and after actions | `handleAppear`, foreground refresh | PARTIAL | Initial/action refresh incomplete; lifecycle refresh missing |
| PT-012 | Today empty/error/updating overlays | `PatientTodayBaseView` | PARTIAL | Exact states and interaction blocking incomplete |

## Patient History and Settings

| ID | Requirement | References | Android status | Missing/acceptance |
|---|---|---|---|---|
| PH-001 | Month calendar with four slot statuses | `HistoryMonthView.swift` | SCAFFOLDED | Current list must be replaced by calendar |
| PH-002 | Always-visible status legend | history spec 004 | NOT_STARTED | Taken/missed/pending legend required |
| PH-003 | Navigate to day details | `HistoryDayDetailView.swift`, day route | NOT_STARTED | Scheduled and PRN detail required |
| PH-004 | Handle history retention lock | `HistoryRetentionLockView.swift` | NOT_STARTED | Code/cutoff/retention handling required |
| PH-005 | Patient notification preferences | notification feature files | NOT_STARTED | Master, slots and re-reminder settings required |
| PH-006 | Rebuild notification schedule on settings/app lifecycle | scheduling coordinator | NOT_STARTED | Deterministic plan and scheduler tests required |
| PH-007 | Revoke server session before local unlink | `/api/patient/session` | IMPLEMENTED | Real-session verification pending |
| PH-008 | Legal/support links and mode change behavior | patient settings iOS UI | NOT_STARTED | Exact destinations and browser behavior required |

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
| XP-002 | Notification tap deep link to exact date/slot | NOT_STARTED |
| XP-003 | In-context patient/caregiver tutorials | NOT_STARTED |
| XP-004 | Analytics event parity with test/preview suppression | NOT_STARTED |
| XP-005 | Dynamic type/font scale and screen-reader semantics | SCAFFOLDED |
| XP-006 | Dark mode visual parity | NOT_STARTED |
| XP-007 | Offline/retry behavior | PARTIAL |
| XP-008 | Physical-device matrix | NOT_STARTED |
| XP-009 | Google Play Data safety and health declarations | NOT_STARTED |
| XP-010 | Closed-test and signed production release | NOT_STARTED |
