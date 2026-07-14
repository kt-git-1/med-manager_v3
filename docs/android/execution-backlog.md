# Android Ordered Execution Backlog

**Baseline:** `main@1d9d19e`
**Work branch:** `android-dev`
**Rule:** Work top to bottom. A later item may start only when its dependency/gate is satisfied.

This is the operational plan used for future “進めて” requests. Status truth remains in `parity-requirements.md`.

## Gate A — Rebaseline repair

### A01 Public request auth policy

- [x] Introduce `RequestAuthPolicy.PUBLIC/PATIENT/CAREGIVER` in the HTTP layer.
- [x] Make patient link exchange explicitly `PUBLIC`.
- [x] Test that stale patient/caregiver tokens are not sent.
- [x] Test that link 401/403/404/422/429 does not clear either stored session.
- [x] Preserve protected patient one-refresh/one-retry behavior.
- [x] Update `SH-006` and `AU-002` evidence.

**Gate:** API client tests pass for public and protected policies.

### A02 Link copy/error parity

- [x] Map invalid six digits, expired/not-found, forbidden, network, rate-limit and generic failures to pinned localization keys.
- [x] Move link production copy to `strings.xml`.
- [x] Add Compose states and copy assertions.
- [x] Capture current iOS/Android light, dark and 2.0 font states.

### A03 Installation/session safety

- [x] Review manifest backup/data-extraction configuration for all supported API levels.
- [x] Verify force-stop/process death restores an active installation.
- [x] Verify uninstall/reinstall does not restore mode/token/selected patient.
- [x] Verify OS backup/restore path cannot restore decryptable session secrets.
- [ ] Record physical-device evidence for `SH-007/SH-009`.

### A04 Mutation freshness model

- [x] Define monotonic revisions for dose, medication and inventory changes.
- [x] Define consumers: patient/caregiver Today, History, Inventory and notification scheduler.
- [x] Ensure events are not lost when a destination tab has not yet been created.
- [x] Ensure duplicate collection does not cause duplicate API mutations.
- [x] Unit-test visible, hidden, first-visit and process-recreated consumers.

### A05 Post-record reminder rebuild

- [x] Emit scheduled-dose-changed only after individual success or bulk `updatedCount > 0`.
- [x] Render success and clear mutation progress before reminder maintenance.
- [x] Rebuild asynchronously; report maintenance error without changing mutation success.
- [x] Do not rebuild for PRN or zero-update bulk except where current iOS explicitly requires it.
- [x] Test tomorrow, month boundary, year boundary, secondary reminders and disabled slots.
- [x] Recheck `PT-005`, `PT-006`, `PT-011`, `PT-013`, `PH-006`.

### A06 Persistent patient tabs and routing

- [x] Create each patient tab lazily, once.
- [x] Preserve state and scroll after switching.
- [x] Hide hit testing and accessibility for inactive tabs.
- [x] Route patient local reminders to Today and exact slot highlight.
- [x] Consume history freshness on visible/next-visible History.
- [x] Recheck `PT-014`, `XP-002`, tutorial behavior and large text.

### Gate A exit

- [x] All A items pass JVM/Compose/instrumentation tests.
- [x] `test`, debug/release assembly, lint and `git diff --check` pass.
- [x] Affected matrix rows no longer say `RECHECK_REQUIRED` unless device-only evidence remains explicitly separated.
- [x] No caregiver product implementation was built on an obsolete shared contract.

Gate A's shared-contract exit is complete. A03 physical-device/OEM-transfer evidence remains explicitly deferred to V1 and does not reopen the implemented contract.

## Gate B — Shared refactor without behavior drift

### B01 Typed wire layer

- [x] Select and configure Kotlin serialization consistently.
- [x] Separate endpoint DTOs, domain models and UI models.
- [x] Port current success/optional/error fixtures before deleting manual parsers.
- [x] Keep exact top-level response differences documented in `api-contracts.md`.

### B02 State and navigation ownership

- [x] Separate app session, selected caregiver patient, feature data, local notification settings and navigation targets.
- [x] Add saved-state/process-recreation coverage where values are not server-restorable.
- [x] Prevent Compose from reading raw tokens or deciding endpoint policy.

### B03 Resources and components

- [x] Move all production copy from Kotlin to resources per screen group.
- [x] Split `PatientHomeScreen.kt` into shell/Today/History/Settings/tutorial components and state holders.
- [x] Preserve current tests and add production-component screenshot fixtures.
- [x] Keep theme tokens centralized and remove unexplained screen literals.

Patient Compose, repository, session/auth/network fallback and local notification copy are resource-backed. Safe backend validation strings remain explicit `Raw` presentation values by contract; opaque server/internal errors map to typed local resources.

### Gate B exit

- [ ] Patient functional and screenshot regressions pass before caregiver work.
- [x] Architecture supports caregiver repositories without duplicating session/network logic.

Functional/JVM/Compose/instrumentation gates pass, including production-component image capture. Current-baseline visual regression evidence is now being accumulated under C01; UI-001 comparison found and closed status-bar safe-area and dark primary-content drift, and its 2.0-font family action now has a reachability regression test.

## Gate C — Current patient parity

- [ ] C01 Re-capture entry/auth and patient iOS states at `main@1d9d19e`.
- [x] C02 Reverify Mode Select, auth choice/login/signup/callback/resend.
- [x] C03 Reverify Patient Today states/actions/recording-window/inventory/PRN/detail.
- [x] C04 Reverify current Patient History progress/week/recent summaries, retention and freshness.
- [x] C05 Reverify Settings/reminders/tutorial/deep links/legal/logout and patient analytics-consent UI.
- [ ] C06 Complete patient TalkBack, font 1.0/1.3/2.0, light/dark and physical-device checks. API-35 emulator dark captures for Today/History/Settings, 200% reachability for all three primary screens, and merged History day semantics are complete; 130%, full TalkBack traversal, remaining state captures and physical-device verification remain.

## Gate D — Caregiver patient-management vertical slice

### D01 Shell and selection

- [x] Five tabs in current iOS order, Today initial.
- [x] Lazy persistent tab lifetime and hidden-tab isolation.
- [x] Load patients; auto-select sole patient; clear invalid stored selection.
- [x] Shared no-patient and data-unavailable states.

### D02 Patient management contracts/UI

- [x] List/create/select with 50-character/nonblank validation and patient-limit response.
- [x] Edit four slot preset times and propagate freshness.
- [x] Issue one-time 15-minute code; copy/share system sheet.
- [x] Revoke preserves data and clears current selection/redirect state.
- [x] Delete waits for server cascade success, then invalidates dependent data.
- [x] Server-first caregiver account deletion and local reset.

### D03 Caregiver tutorial

- [x] Reproduce all current tutorial steps and pinned copy.
- [x] Operate on the real tab/registration flow.
- [x] Push permission final step and “later” path.
- [x] 2.0 font and TalkBack focus verification.

## Gate E — Medication and regimen

- [ ] E01 Medication list/no-patient/empty/filter/content/error states.
- [ ] E02 Regular and PRN add/edit form with complete validation.
- [ ] E03 Daily/weekday slot regimen create/update/disable.
- [ ] E04 Inventory fields and medication lifecycle state.
- [ ] E05 Mutation invalidation and current iOS visual comparison.

## Gate F — Caregiver Today and inventory

- [ ] F01 Today load/status aggregation and no-patient/error states.
- [ ] F02 Individual proxy record/delete.
- [ ] F03 Bulk proxy record including older missed slots outside the patient window.
- [ ] F04 PRN record/delete and inventory errors.
- [ ] F05 Preserve successful mutation UI when follow-up refresh fails.
- [ ] F06 Inventory list/filter/detail/enable/adjust/refill and low-stock badge.
- [ ] F07 Cross-tab Today/History/Inventory revision tests.

## Gate G — History, PDF, push and settings

- [ ] G01 Caregiver month/day history, retention and exact push target/highlight.
- [ ] G02 PDF free lock, presets/custom validation, on-device generation and content-URI share.
- [ ] G03 FCM permission/token register/unregister/retry/disable lifecycle using `platform=android`.
- [ ] G04 Push privacy and dedup behavior; account deletion cleanup.
- [ ] G05 Legal/support/logout/account deletion complete settings flow.

## Gate H — Analytics and privacy

- [ ] Add Firebase Android configuration through non-secret environment-aware setup.
- [ ] Collection defaults off; no automatic collection before consent.
- [ ] Consent can be changed in both roles; disabling resets analytics data.
- [ ] Port only fixed event names/enum parameters from `AnalyticsService.swift`.
- [ ] Reject patient/caregiver IDs, medication data, dose status/time/date, inventory, email, free text, notification content and tokens at the wrapper boundary.
- [ ] Suppress in previews, screenshot fixtures and tests.
- [ ] Verify DebugView, then Realtime/Events/Explore instructions in `docs/firebase-analytics.md`.
- [ ] Align Play Data safety and privacy policy with actual collection.

## Gate I — Release and merge

- [ ] Final merge/rebaseline of then-current `main` into `android-dev`.
- [ ] Resolve all `RECHECK_REQUIRED` rows.
- [ ] Full emulator/API-level and physical-device matrices.
- [ ] Notification Doze/delivery/tap/process-death tests.
- [ ] TalkBack, font 2.0, dark mode, compact/large phone and rotation/configuration tests.
- [ ] Security, dependency, privacy and performance reviews.
- [ ] Signed internal then closed Play test.
- [ ] Production Firebase, app links, signing, Data safety and health declarations.
- [ ] Merge Android work into main without overwriting newer iOS/API files.

## Immediate next item

Begin **Gate E medication and regimen** on the completed Gate D caregiver shell/patient-management/tutorial foundation. The ten-step tutorial drives the real five tabs, scrolls to production registration/time/code surfaces, persists skip/finish, requests notification permission at the final primary action and remains operable at 200% font with pane semantics. C01/C06 visual/physical residuals, FCM transport and Gate H Firebase Analytics remain tracked.
