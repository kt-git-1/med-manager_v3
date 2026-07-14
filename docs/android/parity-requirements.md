# Android Parity Requirements Matrix

This file tracks product parity against `main@1d9d19e`. Status is conservative: a working happy path remains `PARTIAL` until state, visual, emulator, and physical-device evidence is complete. `RECHECK_REQUIRED` means older implementation evidence was invalidated by the new baseline.

## Shared foundation

| ID | Requirement | iOS/backend references | Android status | Acceptance evidence |
|---|---|---|---|---|
| SH-001 | Restore last patient/caregiver mode | `SessionStore.swift`, `SessionStoreTests.swift` | IMPLEMENTED | Android unit tests; device verification pending |
| SH-002 | Encrypt access/refresh tokens at rest | `SessionKeychainStore`, `SessionStore.swift` | IMPLEMENTED | Keystore AES-GCM implementation; physical-device verification pending |
| SH-003 | Refresh caregiver Supabase session before expiry | `AuthService.swift`, `SessionStore.swift` | IMPLEMENTED | Near-expiry refresh, concurrent coalescing, selection preservation, and failure transition tested; physical verification pending |
| SH-004 | Refresh patient session through API | `/api/patient/session/refresh`, `APIClient.swift` | IMPLEMENTED | Contract/parser/repository tests pass; physical lifecycle verification pending |
| SH-005 | Map auth/API errors to safe Japanese messages | `APIError.swift`, `AuthService.swift` | IMPLEMENTED | General HTTP families plus inventory, patient-limit, retention, and rate-limit contracts tested; specialized screen UI remains in feature rows |
| SH-006 | Apply endpoint-specific auth and unauthorized handling | `APIClient.send`, `APIClientTests` | IMPLEMENTED | Explicit PUBLIC/PATIENT/CAREGIVER policies; public 401/403/404/422/429 never attach tokens, refresh, retry or invalidate sessions; protected patient one-refresh/one-retry and caregiver 401-only invalidation tests pass; physical verification pending |
| SH-007 | Restore state after process death | iOS session behavior | IMPLEMENTED | Keystore persistence, new-instance restoration, expired-token rejection, and emulator force-stop/cold launch verified; physical device pending |
| SH-008 | Shared light/dark design tokens | `AppTheme`, `PatientUI`, `CaregiverUI` | IMPLEMENTED | iOS semantic palette, surface hierarchy, role/status/slot colors, shadows, automatic system theme, and token tests complete; remaining legacy patient consumers migrate within their feature rows and physical visual verification is pending |
| SH-009 | Never restore a patient session from a prior installation | `SessionStore.preparePatientSessionForCurrentInstallation`, session tests | IMPLEMENTED | Backup disabled; API 26–30 and 31+ cloud/device-transfer rules exclude all app domains; no-backup installation marker clears any restored regular/secure preferences; marker-loss instrumentation and API 35 uninstall/reinstall evidence pass. Physical OEM transfer verification remains |
| SH-010 | Production copy is localized and baseline-traceable | `Localizable.strings` | IMPLEMENTED | Patient, entry/auth, repository/session/network fallback and notification copy is resource-backed; typed presentation messages select resource keys, safe backend validation detail remains explicit raw data by contract, and no Japanese literal remains in production Kotlin |

## Entry and authentication

| ID | Requirement | References | Android status | Missing/acceptance |
|---|---|---|---|---|
| AU-001 | Mode-select information hierarchy and visuals | `ModeSelectView.swift` | IMPLEMENTED | Current `main@1d9d19e` iOS default/consent/dark/largest-text references and Android API 35 light/dark/2.0-font comparisons are recorded; comparison closed edge-to-edge inset and dark primary-content drift, and a 2.0-font scroll/action test passes. Canonical copy, hierarchy, colors, cards and actions pass; Android analytics-consent, viewport-pair and physical verification pending |
| AU-002 | Patient six-digit public linking flow | `LinkCodeEntryView.swift`, `APIClientTests`, `/api/patient/link` | IMPLEMENTED | Explicit public auth policy; typed invalid/not-found/authorization/network/generic UI states; canonical `strings.xml` copy; Compose semantics/copy assertions; current light/dark/large-text simulator evidence. Live-code and physical-device verification remain |
| AU-003 | Caregiver auth choice | `CaregiverAuthChoiceView.swift` | IMPLEMENTED | Current-main iOS and Android API 35 captures match section order, card geometry, role colors and back behavior; canonical-copy and choice navigation tests pass. Dark/large-text/physical verification remains |
| AU-004 | Caregiver login | `CaregiverLoginView.swift`, `AuthService.login` | IMPLEMENTED | Current-main empty-form captures are paired; comparison replaced the Android full-width form back button with an iOS-equivalent 52 dp circular navigation action. Compose destination/error-state, Supabase login and confirmation-link landing tests pass; real credential/physical verification remains |
| AU-005 | Caregiver signup and password confirmation | `CaregiverSignupView.swift` | IMPLEMENTED | Compose form, local email/password/confirmation validation, Supabase signup, and confirmation state tests complete; real email delivery pending |
| AU-006 | Confirmation email callback routing | `SessionStore.handleIncomingURL` | IMPLEMENTED | HTTPS App Links and custom scheme routing, allowlist/rejection tests, stale-session clearing, emulator cold-launch check complete; domain association/physical verification pending |
| AU-007 | Resend confirmation cooldown and 429 handling | `resendSignupConfirmation`, signup view | IMPLEMENTED | Supabase resend payload, 60-second UI cooldown, status 429 message, and repository resend test complete; live rate-limit verification pending |

## Patient Today

| ID | Requirement | References | Android status | Missing/acceptance |
|---|---|---|---|---|
| PT-001 | Load today's patient schedule | `PatientTodayViewModel`, `/api/patient/today` | IMPLEMENTED | Full iOS-equivalent dose/snapshot contract, optional fields, auth-header path, status/time sorting, repository state, fixtures, and contract tests complete; real-session/visual verification pending |
| PT-002 | Use server-resolved patient slot times | `NotificationPreferencesStore`, `/api/patient/slot-times` | IMPLEMENTED | Four-value typed contract, strict HH:mm validation, Tokyo exact-match plus canonical range fallback, repository integration, resilient cached/default fallback, and grouping replacement complete; real custom-time verification pending |
| PT-003 | Match iOS next-action/slot ordering | `PatientTodayNextSlotSelector`, `PatientTodayView` | IMPLEMENTED | iOS-equivalent selector, inventory-backed candidates, recording-window filtering and current UI-101 next-hero/planned ordering have API-35 Compose and paired light evidence; dark/large-text/physical verification pending |
| PT-004 | Render pending/taken/missed states | `PatientTodayView` | IMPLEMENTED | Distinct pending/taken/missed/inventory-blocked labels, status icons, slot ordering, accent cards and typical light comparison complete; remaining state matrix/physical verification is tracked by C06 |
| PT-005 | Record individual scheduled dose | `/api/patient/dose-records`, `PatientTodayViewModel` | IMPLEMENTED | Success updates visible state and clears progress before dose/history/notification-plan revisions; reminder maintenance runs in application scope and cannot reverse success; live API/physical verification pending |
| PT-006 | Record slot in bulk | `/api/patient/dose-records/slot`, notification tests | IMPLEMENTED | Positive `updatedCount` updates only recordable doses and advances history/notification maintenance; zero update emits no scheduled change; partial response semantics and next-day/month/year tests pass; live patient-window verification pending |
| PT-007 | Preserve partial-success semantics | `SlotBulkRecordResponseDTO`, inventory spec | IMPLEMENTED | All response counts parsed; sufficient doses update while insufficient doses remain pending, with explicit partial-result message and tests |
| PT-008 | Show out-of-stock/insufficient inventory | `MedicationDTO`, `PatientTodayViewModel` | IMPLEMENTED | Full medication/inventory contract, quantity-per-dose comparison, blocked individual/bulk/PRN actions, warnings, and contract/repository/UI tests complete |
| PT-009 | List and record PRN medication | PRN DTO/routes and Today tests | IMPLEMENTED | Current iOS-style orange entry card opens a scrollable PRN sheet; filtering, instructions, confirmation, patient-scoped request, progress/error/success and inventory blocking have Compose coverage and light evidence; real-session verification pending |
| PT-010 | Present medication/dose detail | `PatientTodayDoseDetailView` | IMPLEMENTED | Tappable dose cards and modal detail with status, schedule, notes fallback, quantity, strength, and inventory plus Compose coverage; final visual/physical verification pending |
| PT-011 | Refresh on foreground and after actions without losing successful state | current Today view models | IMPLEMENTED | Foreground/quiet authoritative refresh, successful-state preservation on failed follow-up read, monotonic History freshness and non-destructive reminder-maintenance warning are tested; persistent tab instances remain separately tracked by PT-014 |
| PT-012 | Today empty/error/updating overlays | `PatientTodayBaseView` | IMPLEMENTED | Initial progress, non-destructive refresh banner, empty/error/retry/success states, per-action progress, and global interaction blocking implemented; matched visual and large-text verification pending |
| PT-013 | Rebuild local reminders after scheduled recording | `SchedulingRefreshCoordinator`, `NotificationPlanBuilderTests` | IMPLEMENTED | Dedicated notification-plan revision after individual/positive bulk success; application-scope duplicate-safe rebuild; PRN/zero-update exclusion; failure warning preserves success; tomorrow/month/year/secondary/disabled-slot tests pass; physical alarms pending |
| PT-014 | Preserve patient tab instances and invalidate cross-tab data | `PatientReadOnlyView.loadedTabs`, `.doseRecordsUpdated` | IMPLEMENTED | Today is initial; History/Settings are composed on first visit and retained by stable keys; local state and lazy-list scroll survive switching; hidden roots are transparent, lower-z, pointer-blocked and semantics-cleared; History consumes dose revision only while visible/next-visible. Compose tests pass |

## Patient History and Settings

| ID | Requirement | References | Android status | Missing/acceptance |
|---|---|---|---|---|
| PH-001 | Current-day progress summary | current `HistoryMonthView.patientSimpleHistory` | IMPLEMENTED | Current iOS-equivalent progress ring, taken/remaining/missed pills, no-plan/partial/complete/missed encouragement priority and deterministic API-35 Compose coverage complete; paired iOS/physical verification pending |
| PH-002 | Current-week adherence summary | `PatientHistoryWeekRange`, `PatientHistoryEncouragement` | IMPLEMENTED | Monday-first seven-day range, taken-day count, upcoming/status icons and encouragement thresholds render in the current teal card; fixed-clock capture and Compose coverage complete |
| PH-003 | Recent patient history summaries | current `HistoryMonthView.patientSimpleHistory` | IMPLEMENTED | Today/yesterday titles, active-slot subtitle, PRN/no-plan fallback and done/pending/missed status treatment match current iOS; old month/day endpoint/detail code is retained for caregiver history foundation |
| PH-004 | Handle history retention lock | `HistoryRetentionLockView.swift` | IMPLEMENTED | Structured 403 mapping, month/day repository state, generic-error suppression, cutoff/date messaging, lock surfaces, mapper/repository/Compose tests complete; entitlement/physical verification pending |
| PH-005 | Patient notification preferences | current `PatientSettingsView` | IMPLEMENTED | Current UI exposes the master toggle only, preserves detailed slot/re-reminder values internally, requests Android 13 permission on enable and shows denied guidance; persistence and Compose coverage complete, physical verification pending |
| PH-006 | Rebuild notification schedule on settings/lifecycle/recording | scheduling coordinator | IMPLEMENTED | Settings/foreground replacement plus scheduled-record application-scope maintenance; master/session guards, full-plan replacement, failure reporting and Tokyo tomorrow/month/year boundary coverage complete; physical AlarmManager delivery pending |
| PH-007 | Revoke server session before local unlink | `/api/patient/session` | IMPLEMENTED | Confirmed server-first DELETE, success-only local unlink/notification cancellation, failure preservation, repository and Compose tests complete; real-session verification pending |
| PH-008 | Legal/support links and mode change behavior | patient settings iOS UI | IMPLEMENTED | Exact privacy/terms/support HTTPS destinations, external browser intents, linked-state copy, destructive confirmation, and return-to-mode-select semantics complete; physical browser verification pending |

## Caregiver mode

| ID | Requirement group | References | Android status |
|---|---|---|---|
| CG-001 | Five-tab caregiver shell and selected-patient behavior | `CaregiverHomeView.swift` | NOT_STARTED |
| CG-002 | Patient list/create/select | `PatientManagementView`, `/api/patients` | NOT_STARTED |
| CG-003 | Linking code issue/share | `PatientLinkCodeView`, linking-code route | NOT_STARTED |
| CG-004 | Revoke/delete patient with correct semantics | revoke/delete views and routes | NOT_STARTED |
| CG-005 | Medication list and empty state | `MedicationListView.swift` | NOT_STARTED |
| CG-006 | Regular/PRN medication form and validation | `MedicationFormView*` | NOT_STARTED |
| CG-007 | Regimen schedule CRUD | regimen routes | NOT_STARTED |
| CG-008 | Caregiver Today: individual/bulk/PRN proxy record and delete | `CaregiverTodayView*` | NOT_STARTED |
| CG-009 | Inventory list/detail/adjust | inventory views/routes | NOT_STARTED |
| CG-010 | Caregiver month/day history | caregiver/history views | NOT_STARTED |
| CG-011 | PDF period selection/report/share | PDF feature files/report route | NOT_STARTED |
| CG-012 | Push settings and self-exclusion behavior | push settings/routes | NOT_STARTED |
| CG-013 | Account deletion and session reset | settings and `/api/me` | NOT_STARTED |
| CG-014 | Lazy tab lifetime, hidden-tab isolation and state preservation | `CaregiverHomeView.loadedTabs`, tab visibility modifier | NOT_STARTED |
| CG-015 | Preserve successful mutation state if follow-up refresh fails | `CaregiverTodayViewModel.refreshAfterMutation` | NOT_STARTED |
| CG-016 | Allow caregiver proxy bulk for older missed slots | caregiver slot route, `slotBulkRecordService` | NOT_STARTED |
| CG-017 | Cross-tab Today/History/Inventory freshness revisions | `.doseRecordsUpdated`, `.medicationUpdated` | PARTIAL | Shared monotonic dose/medication/inventory revisions, consumer matrix and duplicate-safe cursors are implemented/tested; caregiver screens must bind their Today/History/Inventory consumers in C1–C4 |

## Cross-platform and release

| ID | Requirement | Android status | Missing/acceptance |
|---|---|---|---|
| XP-001 | FCM token register/unregister lifecycle | NOT_STARTED | Android FCM registration, retry, soft-disable, account-delete cleanup and physical delivery evidence required |
| XP-002 | Role-correct notification tap to exact date/slot | PARTIAL | Patient local reminder now always selects retained Today and highlights the exact slot for four seconds, including stale-date payloads; caregiver remote push must still target History/day/slot in C4; process-death/physical tap evidence pending |
| XP-003 | In-context patient/caregiver tutorials | PARTIAL | Four-step patient real-screen tutorial, tab transitions, persisted completion/skip, back/next controls, and final permission action implemented with Compose tests; caregiver tutorial remains for Phase 3 |
| XP-004 | Privacy-first Firebase Analytics parity | PARTIAL | Patient Settings now provides explicit off-by-default persisted consent with exact privacy copy. Firebase configuration/wrapper, caregiver consent, reset on disable, fixed safe events, preview/test suppression and DebugView/Realtime/Events/Explore verification remain Gate H |
| XP-005 | Dynamic type/font scale and screen-reader semantics | PARTIAL | At 200% font, Patient Today primary bulk/planned content, History recent/today content, Settings analytics/logout, and tutorial actions remain reachable. History weekday/date/status is one merged TalkBack label. The 130% matrix, full traversal, caregiver surfaces and physical TalkBack verification remain |
| XP-006 | Dark mode visual parity | PARTIAL | Current Patient Today, History and Settings render successfully in API-35 emulator dark mode with evidence under `evidence/c06-20260714/`; complete state-by-state iOS comparison and physical-device verification remain |
| XP-007 | Offline/retry behavior | PARTIAL | Patient retry states exist; shared stale/cache policy and caregiver paths remain |
| XP-008 | Physical-device matrix | NOT_STARTED | Supported API/device classes, notification/Doze, TalkBack, font scale and lifecycle required |
| XP-009 | Google Play Data safety and health declarations | NOT_STARTED | Complete from actual release behavior and analytics consent configuration |
| XP-010 | Closed-test and signed production release | NOT_STARTED | Production signing, Firebase Android app, Play tracks and rollout evidence required |
