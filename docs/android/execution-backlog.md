# Android Ordered Execution Backlog

**Baseline:** published iOS 1.0.6 Build 51, `main@432b34c`
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

- [x] Patient functional and production-component screenshot regressions pass before caregiver work.
- [x] Architecture supports caregiver repositories without duplicating session/network logic.

Functional/JVM/Compose/instrumentation gates pass, including production-component image capture. Current-baseline visual regression evidence is now being accumulated under C01; UI-001 comparison found and closed status-bar safe-area and dark primary-content drift, and its 2.0-font family action plus Analytics decision actions now have reachability regressions.

## Gate C — Current patient parity

- [x] C01 Re-capture entry/auth and patient iOS states. The original `main@1cf8aef` umbrella item is superseded and closed by current-runtime C37–C48 evidence against the later `main@3e52fb2` baseline; UI-001–106 emulator-verifiable states now have explicit source/runtime evidence owners.
- [x] C02 Reverify Mode Select, auth choice/login/signup/callback/resend.
- [x] C02 Repair confirmation-resend lifecycle parity: separate in-flight progress and duplicate lock, with cooldown only after confirmation-required, success or 429.
- [x] C02 Record the deterministic UI-002 six-digit, submitting, validation, expired, forbidden, network and rate-limit/fallback Android state matrix.
- [x] C02 Match UI-005 error/info/resend visual hierarchy and record invalid-email, password-mismatch, loading, confirmation, resend-loading/success/429 states.
- [x] C02 Define login/signup Next/Done IME traversal and record UI-004 loading plus invalid-credential feedback states.
- [x] C02 Record UI-003 dark rendering and login/signup/mode-reset reachability at 200% font scale.
- [x] C03 Reverify Patient Today states/actions/recording-window/inventory/PRN/detail.
- [x] C03 Record UI-103 insufficient-inventory behavior and repair its unavailable action to the iOS teal 55% treatment.
- [x] C03 Record UI-102 dose-detail dark hierarchy and complete 200%-font content reachability.
- [x] C04 Reverify current Patient History progress/week/recent summaries, retention and freshness.
- [x] C04 Match UI-105 retained day-detail timeline/copy, isolate its failure/retention lifecycle and record the five-state API-35 matrix.
- [x] C05 Reverify Settings/reminders/tutorial/deep links/legal/logout and patient analytics-consent UI.
- [x] C06 Complete emulator-verifiable patient/caregiver font 1.0/1.3/2.0, light/dark, compact/standard/large-phone, semantics and rotation/configuration checks. This includes dark captures for Patient Today/History/Settings and all four Caregiver primary surfaces, Caregiver primary-screen action paths at dark plus 200%, Settings/tutorial 200%, merged Patient week and Caregiver calendar-day semantics, count-specific Caregiver slot-bulk plus medication-named destructive/detail actions, and production Patient/Caregiver Activity recreation.
- [x] C06 Record UI-103 PRN dark hierarchy and 200%-font action reachability on API 35.
- [x] C06 Record UI-208 loading/error/empty and dedicated code/time sheets; verify both sheets at dark plus 200% under C17.
- [x] C07 Match UI-001 mode-select and Analytics-consent on current iOS/Android compact and large-phone viewports; all primary actions remain visible and no repair is required.
- [x] C18 Match the shared scheduled-dose state copy to current iOS (`記録済み` / `飲み忘れ` / `未記録`) across patient, caregiver and spoken calendar summaries; reject the stale Android labels in API-35 regressions.
- [x] C19 Record a same-data UI-101 iOS/Android dark and maximum-text pair; match the shared patient header icon plus Today card, typography and primary-action metrics to current SwiftUI.
- [x] C20 Calibrate UI-104 typical light against the current iOS app-derived reference and production SwiftUI source; match progress/week/recent card metrics, status pills/icons and current taken-count copy.
- [x] C21 Calibrate UI-106 typical light against the current iOS app-derived reference and production SwiftUI source; match card, toggle/info/navigation row and logout metrics while preserving adaptive reachability.
- [x] C22 Record a same-data UI-201 Caregiver Today light pair; match the current caregiver avatar/header, next-slot hero/list/action, progress summary and card-level PRN entry while retaining dark/200% reachability.
- [x] C23 Source-calibrate the UI-201 four-slot timeline; match current row/card metrics, exact state copy, medicine indicators, taken-only undo, recordable-only bulk action and explicit no-plan rows.
- [x] C24 Source-calibrate UI-202 caregiver medications; match the production header/add action, 2 x 2 metrics, icon filters, medication card/symbol/detail hierarchy and inventory/edit treatments while retaining adaptive reachability.
- [x] C25 Source-calibrate UI-203 caregiver medication add/edit; match the production hero/progress, grouped basic-information rows, dosage-unit picker and descriptive scheduled/PRN choice cards while preserving validation and adaptive reachability.
- [x] C26 Source-calibrate UI-204 caregiver inventory list; match the production patient header, 2 x 2 summary, priority guide, semantic filters and rich inventory-card hierarchy while preserving refill/detail behavior.
- [x] C27 Source-calibrate UI-205 caregiver inventory detail; match the centered save contract, medication/status/remaining hierarchy, settings toggle, refill presets/input and separate absolute-correction card while preserving confirmation and exact failed-action retry behavior.
- [x] C28 Source-calibrate UI-206 caregiver history; match the caregiver header, Monday-first explained calendar, marker colors/cells, selected-day progress/pills and free-release month-navigation contract while preserving the C16 inline timeline and notification/backfill behavior.
- [x] C29 Source-calibrate UI-208 caregiver settings; match the shared patient avatar/header, grouped selection and selected-patient cards, detail/push/Analytics/legal/account hierarchy and 128-unit bottom inset while preserving the separate CG-004 data-preserving revoke action.
- [x] C30 Match UI-208 normal Settings in dark mode at maximum text; verify push, Analytics, legal, account, code and time actions remain scroll-reachable, and repair the shared caregiver shell's white dark-mode safe-area.
- [x] C31 Merge `main@1cf8aef`, audit the complete API/iOS/spec delta, pin the new source baseline and reopen invalidated rows before implementation.
- [x] C32 Implement `GET /api/patient/history/streak` as supplementary Patient History state and reproduce the current iOS streak card/copy/lifecycle.
- [x] C33 Replace the obsolete Caregiver Today next-action hero with the current status-first summary, optional PRN entry and action-owning four-slot timeline.
- [x] C37 Capture fresh current-iOS Patient History streak and Caregiver Today status references, close populated-time/icon drift, and record matched light plus dark/maximum-text pairs.
- [x] C38 Close the UI-003 current-runtime residual with fresh iOS light/dark/OS-Accessibility-XXXL and Android matched light/dark/200%-font pairs; align header, role and navigation icon semantics.
- [x] C39 Close the UI-004 current-runtime residual with fresh empty/synthetic-filled light and dark pairs plus adaptive evidence; align the form canvas, glass card, blue accent, filled fields, spacing and circular navigation.
- [x] C40 Close the UI-005 current-runtime residual with fresh empty/synthetic-filled light and dark pairs plus adaptive evidence; align signup card origin, person-add title and confirmation-lock semantics without changing C39 login geometry.
- [x] C41 Close the UI-002 non-empty current-runtime residual with fresh empty/real-field synthetic-filled light and dark pairs plus adaptive evidence; align link field/action geometry, `.xLarge` hierarchy and chevron semantics without sending a link request.
- [x] C42 Close the UI-101 current-runtime initial-loading/failure light residual with production-shell pairs; match neutral loading, the glass error card and the shared floating patient tab bar, then pass the 205-test API-35 suite.
- [x] C43 Close the remaining emulator-verifiable UI-101 light residuals with same-data production-shell inventory-partial, cached-updating, long-name and notification-target pairs; add the missing top shortage warning, app-illustration overlay, pills/slot/copy/row calibration and current-iOS exact-slot scroll semantics, then pass the 206-test API-35 suite.
- [x] C44 Close the UI-102 current-runtime residual with same-data populated/empty/loading/error light, dark and largest-text pairs; replace outlined detail cards, teal status/progress and centered retry treatment with the current SwiftUI card, green status, neutral loader and leading iOS-blue retry hierarchy.
- [x] C45 Close the UI-103 current-runtime residual with same-data list/loading/error/insufficient light, dark and largest-text pairs; replace the Android-only bottom sheet with the current full navigation surface and align the medication symbol, card typography, blocking app-image overlay, top error capsule and back semantics.
- [x] C46 Close the UI-104 current-runtime residual with deterministic no-plan/loading/failure/retention light plus no-plan dark and largest-text pairs; align the clock identity, zero-progress copy, neutral loader, error action and full-screen retention actions with current iOS.
- [x] C47 Reconcile UI-105 reachability with current iOS; remove the obsolete patient selected-history saved state and bottom sheet, retain typed/shared day foundations for caregiver UI-206, and prove all patient notification dates stay on Today with exact-slot highlighting.
- [x] C48 Close the UI-106 current-runtime residual with top/lower, permission-denied top/guidance, logout confirmation, dark and largest-text pairs; match system-green toggles and destructive confirmation while preserving the stricter server-first Android logout contract.
- [x] C49 Close the UI-201 Caregiver Today exceptional-state residual with loading/error/empty/PRN confirmation light, empty/PRN dark and largest-text pairs; replace the stale compact empty/error and bottom-sheet PRN surfaces with the current iOS onboarding, recovery and full navigation contracts.
- [x] C50 Close the UI-202 Caregiver Medication List current-runtime residual with loading/error/no-patient/no-selection/empty/populated/filter-empty light, empty/populated dark and largest-text pairs; align recovery actions, empty onboarding and blank filtered results with current iOS.
- [x] C51 Close the UI-203 Caregiver Medication Form current-runtime residual with add/edit/PRN/weekly/validation/delete-confirmation light plus dark and largest-text pairs; align period/schedule hierarchy, creation-only inventory, exact validation and destructive actions with current iOS.
- [x] C52 Close the UI-204 Caregiver Inventory List current-runtime residual with loading/error/no-patient/no-selection/empty/populated/filter-empty/lower-content light plus empty/populated dark and largest-text pairs; align recovery routing, empty onboarding, out-filter semantics and adaptive reachability with current iOS.
- [x] C53 Close the UI-205 Caregiver Inventory Detail current-runtime residual with scheduled top/lower, PRN, tracking-off, refill/correction confirmation, failure and updating light plus dark and largest-text pairs; align live status/action state, preset selection, retry placement and blocking update presentation with current iOS.
- [x] C54 Close the UI-206 Caregiver History current-runtime residual with patient failure/no-patient/selection, month loading/failure, populated calendar/timeline, day loading/empty/failure, backfill confirmation and updating light plus dark and largest-text pairs; preserve header/month context through loading and recovery.
- [x] C55 Reconcile UI-207 PDF reachability against current iOS, prove the initial-release billing-off and Patient-mode absence contracts, replace the clipped horizontal preset strip with the native menu pattern, align exact copy and two-page A4 report semantics, recover from on-device generation/share failures, and record lock/picker/dark-200%/render evidence.
- [x] C56 Reconcile UI-208 Settings against current `main@3e52fb2`: move zero-patient creation behind its CTA/sheet, disable blank submission, add the immediate post-create code guide, replace multiple-patient radio rows with a menu and missing-selection guidance, suppress patient-scoped push at zero patients, and refresh light/dark/200% evidence.
- [x] C57 Rerun the complete expanded 259-test suite on API 26/33/35; repair compact-viewport lazy-list reachability assumptions without narrowing UI behavior, then pass 777/777 plus JVM 186/186, Lint and Debug/Release assembly.
- [x] C58 Merge `main@3e52fb2`, review the complete API/iOS delta, preserve API push ordering after parallel history/inventory effects, and match current iOS nonblocking Patient/Caregiver post-record reconciliation plus overdue-delete `MISSED` restoration.
- [x] C59 Align the master plan/completion audit to `main@3e52fb2`, close the superseded C01 umbrella row, add the missing privacy-first Firebase live-verification runbook and prove release runtime/signing gates fail closed while secret-free asset/APK checks pass.
- [x] C60 Define the executable physical-device gate across old-supported, current-reference and non-Google OEM targets, including notifications/FCM/Doze/process state, TalkBack/adaptive UI, session/backup, browser/share, destructive/network and exact-artifact evidence rows.
- [x] C61 Rebaseline to the published iOS 1.0.6 Build 51 at `main@432b34c`: merge the full source, validate the 322-test API contract, port actual-time/late-dose behavior across Patient Today/History and Caregiver Today, complete medication form/inventory/push-routing parity, and pass 265/265 UI tests on API 26/33/35 plus JVM/Lint.
- [x] C62 Reopen C61 with a complete published-source audit instead of relying on legacy screenshot tests: match expandable Patient History, compact Patient Today, exact Caregiver Today, grouped Caregiver History, published medication registration defaults and inventory detail styling/reachability; its initial tutorial conclusion was superseded and corrected by C63. Pass 202/202 JVM tests, Lint and 267/267 UI tests on API 26/33/35.
- [x] C63 Correct the tutorial fidelity contract after direct Swift/Kotlin comparison: replace production-screen previews with dedicated published Patient/Caregiver sample screens, match all 14 fixed states and compact overlay geometry/copy, add dark 200% reachability and reference fixtures, then pass 202/202 JVM tests, Lint and 272/272 UI tests on API 26/33/35.
- [x] C64 Run the final local requirement/evidence audit across all 69 parity rows; replace medication-form validation/calculator display strings crossing from the data layer with typed codes/models and Android resources; pass JVM 202/202, Lint, Debug/Release assembly, Release APK compatibility, Play assets, full API-35 UI 272/272 and the affected 25-test slice on API 26/33/35. Keep the remaining six `PARTIAL` rows explicitly external.
- [x] C65 Start V01 on a SHARP A302SH non-Google OEM physical target at Android 15/API 35; install the 1.0.6 Debug artifact without overwriting an existing package, correct compact-device signup reachability verification, pass caregiver auth 20/20 and the complete physical UI suite 272/272, and record exact `PASS`/`NOT_RUN`/`BLOCKED` evidence without promoting Debug results to Play/FCM/TalkBack acceptance.
- [x] C66 Continue V01 on the same physical target: verify the role surface with real system dark plus 200% font and scroll both actions, verify mode-to-caregiver-auth-to-mode at increased display size, preserve local role state through background/process reclaim/force-stop, correct the compact-width caregiver header orphan, then pass the expanded physical UI suite 273/273 plus JVM/Lint/build/APK/Play-asset gates. Keep TalkBack, exact task-card removal and authenticated/Play/Firebase rows open.
- [x] C67 Continue V01 with the retained dedicated QA identities: complete configured caregiver login and ephemeral patient link, verify all five/three primary tabs at default and real dark/200% font, restore both sessions through applicable background/process/task-removal/force-stop states, execute notification denial/system-settings recovery and a privacy-safe old-date notification tap, remove medication identity from local notification Intent/body copy, and pass 273/273 physical UI plus 203/203 JVM/Lint/Debug/Release/APK/asset gates. Fresh Debug reinstall restores neither role session nor mode.
- [x] C68 Add a bounded `android.permission.DUMP` Debug-only reminder diagnostic that delegates to the production scheduler/receiver and is absent from Release; on A302SH measure one background primary and foreground secondary at 36 s, task-removed cold tap into Patient Today/noon, forced-deep-idle delivery at 79 s, and cancellation with no delivery through 96 s. Pass 273/273 physical UI, 206/206 JVM, Lint, Debug/Release/APK/asset gates and restore the device to light/font-1.0/280-dpi/idle-active/TalkBack-off/notification-denied with no alarm or notification.
- [x] C69 Continue the same A302SH slice without health-data mutation: open exact Privacy/Terms/Support URLs and return from both role Settings surfaces; confirm live ephemeral linking-code copy plus Android chooser without sending; prove a denied 20-second target stays silent through 74 seconds; document App Standby deferring one reminder through 100 seconds and delivering once after exit; prove force-stop and uninstall remove active alarms and stay silent through 96/97 seconds; reinstall the configured Debug artifact with no app alarm/notification.
- [x] C70 Execute the safe read-only `XP-007` network transition on the dedicated QA Caregiver: prove online baseline, same-process cached offline retention/stale/Retry, cold offline role restoration and validated-Wi-Fi recovery; correct the missing Caregiver Today patient-list Retry action; pass its 21-test class, the transiently interrupted inventory test 1/1, a clean full physical UI rerun 274/274, JVM 206/206, Lint, Debug/Release assembly, Release APK compatibility and Play assets. Keep mutation interruption, uncertain-write and destructive network procedures open.
- [x] C71 Close the safe same-installation role-isolation slice on A302SH: prove Caregiver explicit logout persists through force-stop/relaunch and later reauthentication, consume one process-only ephemeral link code into the Patient shell, prove Patient explicit logout, then verify Caregiver requires account login and Patient requires a new link code with neither role exposing the other's data. Leave both sessions logged out at role selection; perform no health-data or destructive mutation.
- [x] C72 Audit the remaining safe input slice against source and A302SH behavior; add the missing Patient-link numeric IME `Done` contract with exact-six-digit/not-loading guards; prove a non-existent six-digit code submits through the physical IME while five digits do not submit; pass 275/275 physical UI, 206/206 JVM, Lint and Debug/Release assembly without retaining any code or touching health data.
- [x] C73 Close the remaining safe Caregiver form input slice: add guarded Patient-create `Done`; add explicit medication name/strength/unit/dose and supply-days/inventory traversal with non-saving terminal actions; fix system Back so an unsaved medication editor returns to its list; prove medication IME and unchanged live item count on the dedicated QA production account, prove Patient-create/Back with physical synthetic fixtures, and pass 278/278 UI plus 206/206 JVM/Lint/build/APK/asset gates without a server mutation.
- [x] C74 Harden every health/destructive mutation owner with a repository-level nonblocking single-flight boundary; clear busy state on coroutine cancellation without retrying or inventing success; preserve confirmed medication writes before regimen reconciliation; add six high-latency/cancellation regression families; and pass 278/278 A302SH UI, 212/212 Debug JVM, 209/209 Release JVM, Lint, Debug/Release assembly, Release APK compatibility and Play assets without a production mutation. Keep real interrupted-write outcome and server-idempotency verification in V01.
- [x] C75 Add the missing server/client idempotency contract for PRN and caregiver inventory adjustment: nullable UUID-v4 client mutation keys preserve legacy iOS requests, Android reuses one key for the same in-process uncertain operation, scheduled-dose insert ownership suppresses concurrent duplicate effects, and API regression tests cover replay/race/validation. Pass API 332/332, Debug JVM 216/216, Release JVM 213/213, Lint, Debug/Release assembly, Release compatibility, Play assets and A302SH synthetic UI 278/278 without production data mutation. Keep migration deployment and real interrupted-network proof in V01.
- [x] C76 Register the production-package Android Firebase app; store its four values only in GitHub Actions secrets/ephemeral local environment; add a fail-closed internally consistent Firebase runtime gate; generate the standard Android Firebase resources missing from the manual initialization path; physically prove consent-off suppression, fixed-enum consent-on upload, caregiver/Patient shared disable-reset-reenable behavior, DebugView parameter inspection and Realtime aggregation on A302SH; and pass Debug JVM 216/216, Release JVM 213/213, Lint, both APKs, Release compatibility, Play assets and physical synthetic UI 278/278. Keep processed Events/Explore, FCM and signed-Play evidence external.
- [x] C77 Reaudit the server FCM envelope before physical delivery; correct `DOSE_MISSED` so Android, like `DOSE_TAKEN`, receives only high-priority data and renders generic app-owned copy while iOS retains its notification/APNs envelope; add patient-name exclusion regression coverage; and pass the targeted push suite 16/16 plus the complete API suite 333/333, typecheck and ESLint. Keep physical FCM delivery/routing in V01.
- [x] C78 Add a Release-excluded, `android.permission.DUMP`-protected Firebase Messaging diagnostic that never emits the token; prove a configured A302SH can obtain an FCM token, immediately disable auto-init, delete the token and clear app-owned token state; repeat cleanup, uninstall the app, and verify the diagnostic is absent from Release. Keep authenticated backend registration and FC-002–FC-010 delivery/routing evidence in V01.
- [x] C79 Execute the authenticated FC-001 production-contract preflight with the retained dedicated QA account: grant notification permission, restore the Caregiver session, enable the real Settings switch and prove Firebase token acquisition reaches backend sync; record the live production `422 platform must be ios` response against `origin/main@432b34c`, confirm validation precedes upsert, then disable, delete token/local push state and remove the session/app/temp artifacts. Keep FC-001 open until the already-tested `android-dev` API contract is merged and deployed; never mislabel Android as iOS.
- [x] C80 Recheck the delayed production Analytics report read-only: confirm Platform now includes `android`, the Android 1.0.6 app row is successfully processed with 100% available data, and the C76 privacy-safe fixed event names are present. Record displayed counts only as property-wide aggregates, not Android-only attribution, and leave temporary Explore creation/removal as the separate controlled Console-write gate.
- [x] C81 Strengthen the automatable TalkBack boundary: tag and assert ordered Patient/Caregiver bottom navigation labels, `Role.Tab` and selected state; assert both tutorial pane titles and absence of the live navigation tree; harden one inventory dialog test with an explicit readiness wait; pass targeted accessibility 23/23, four physical shards totaling 280/280, Debug JVM 216/216, Release JVM 213/213, Lint and Release assembly. Keep spoken order, real-finger double-tap and two-finger scrolling in V01.
- [x] C82 Check in a disposable-target four-shard connected UI runner with strict target/shard validation, existing-install refusal, single-shard resume and cleanup on every exit; use it on clean API 26/33/35 AVDs and pass 280/280 per API (840/840 total), Debug JVM 216/216, Release JVM 213/213, Lint and Release assembly. Keep emulator evidence separate from physical/TalkBack/FCM/Play acceptance.
- [x] C34 Accept strict caregiver `DOSE_MISSED` payloads alongside `DOSE_TAKEN` and verify identical exact-date/slot History routing.
- [x] C35 Rerun the full API/JVM/lint/build/API-35 matrix and close all C31 `RECHECK_REQUIRED` rows.
- [ ] V01 Complete the remaining assisted spoken TalkBack traversal, real destructive/mutation-interruption paths, old-supported physical target, Google/reference target, Analytics Explore, Firebase FCM and exact signed-Play artifact rows. C65-C82 cover authenticated appearance, core lifecycle, permission, local reminder lifecycle, safe I/O/IME/offline/role isolation, mutation hardening, physical Analytics through processed Events, both Android FCM envelopes, token acquisition/cleanup, the authenticated production registration failure boundary, automated navigation/tutorial accessibility semantics and a fresh API 26/33/35 emulator matrix. They do not close spoken real-finger operation, migration deployment, real interrupted-write recovery, signed Play, FCM registration/delivery until the Android API contract is deployed, or cross-device physical acceptance.

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

- [x] E01 Medication list/no-patient/empty/filter/content/error states.
- [x] E02 Regular and PRN add/edit form with complete validation.
- [x] E03 Daily/weekday slot regimen create/update/disable.
- [x] E04 Inventory fields and medication lifecycle state.
- [x] E05 Mutation invalidation and current iOS visual comparison.

## Gate F — Caregiver Today and inventory

- [x] F01 Today load/status aggregation and no-patient/error states.
- [x] F02 Individual proxy record/delete.
- [x] F03 Bulk proxy record including older missed slots outside the patient window.
- [x] F04 PRN record/delete and inventory errors.
- [x] F05 Preserve successful mutation UI when follow-up refresh fails.
- [x] F06 Inventory list/filter/detail/enable/adjust/refill and low-stock badge.
- [x] F07 Cross-tab Today/History/Inventory revision tests.

## Gate G — History, PDF, push and settings

- [x] G01 Caregiver month/day history, retention and exact push target/highlight.
  - [x] Rebaseline UI-206 day detail against current iOS: unified timestamp timeline, exact loading/empty/retry states, lifecycle-safe repeated selection and API-35 C16 evidence.
- [x] G02 PDF free lock, presets/custom validation, on-device generation and content-URI share.
- [x] G03 FCM permission/token register/unregister/retry/disable lifecycle using `platform=android`.
- [x] G04 Push privacy and dedup behavior; account deletion cleanup.
- [x] G05 Legal/support/logout/account deletion complete settings flow.

## Gate H — Analytics and privacy

- [x] Add Firebase Android configuration through non-secret environment-aware setup.
- [x] Collection defaults off; no automatic collection before consent.
- [x] Consent can be changed in both roles; disabling resets analytics data.
- [x] Port only fixed event names/enum parameters from `AnalyticsService.swift`.
- [x] Reject patient/caregiver IDs, medication data, dose status/time/date, inventory, email, free text, notification content and tokens at the wrapper boundary.
- [x] Suppress in previews, screenshot fixtures and tests.
- [x] Execute the consent-off/on/reset, DebugView and Realtime procedure in `docs/android/firebase-analytics.md` with the production-package Firebase app and a physical device; C76 records the privacy-reviewed result.
- [ ] Create/remove the temporary fixed-enum-only Explore under explicit Console-write control before promoting `XP-004` to `VERIFIED`; processed Events passed in C80.
- [x] Align Play Data safety input basis and privacy policy with actual collection; final Console submission remains Gate I.

## Gate I — Release and merge

- [x] C58 rebaseline: `main@3e52fb2` is merged into `android-dev` at `2fb4a9f`; the complete eight-file API/iOS delta and its tests were reviewed (2026-07-16).
- [x] Resolve all `RECHECK_REQUIRED` rows; remaining `PARTIAL` rows are explicitly external/visual/device release gates.
- [x] Current cross-version API 26/33/35 matrix passes the complete suite at 280/280 per API (840/840) with 0 skipped/failed after C82, using the checked-in four-shard disposable-target runner. Historical API-35 large-phone coverage also passes 108/108; physical devices remain separate.
- [ ] Execute the C60 `physical-device-matrix.md` on the required old-supported, current reference and current non-Google OEM targets; the matrix/proof format itself is complete.
- [ ] Execute its Patient notification and Caregiver FCM Doze/delivery/tap/task-removal/process-reclamation rows on the exact Play Internal artifact.
- [x] Automated font 2.0, dark mode, compact/standard/large phone, semantics and rotation/configuration tests.
- [ ] Execute the C60 full TalkBack traversal and OEM configuration/lifecycle rows; do not promote emulator semantics evidence to a physical pass.
- [x] Security, dependency, privacy and initial performance reviews; external dependency upgrades and signed-release profiling remain recorded residuals.
- [x] Add a secret-free, fail-closed production signing configuration and Play release runbook.
- [x] Make the signed-AAB task fail closed on incomplete production Firebase/runtime configuration and add repeatable application-ID/SDK/permission/16-KB APK+ELF compatibility inspection (`i04-play-preflight-20260715.md`).
- [x] Map implementation data flows to a draft Play Data safety and Health apps declaration worksheet; final signed-AAB/Console submission remains pending.
- [x] Prepare paste-ready Japanese Play metadata, live account-deletion URL, eight policy-sized synthetic phone screenshots, a 512 px RGBA icon and a 1024 x 500 feature graphic; Console preview/approval remains a release-owner gate.
- [x] Add a fail-closed Digital Asset Links endpoint after live production hosts returned 404; production Play signing fingerprint, deploy and Play-installed verification remain pending.
- [x] Define and implement the cross-role offline contract: in-memory last-known snapshots, explicit stale/retry UI, empty-snapshot handling, scope isolation and no offline mutation queue.
- [ ] Signed internal then closed Play test.
- [ ] Production Firebase, app links, signing, Data safety and health declarations.
- [ ] Merge Android work into main without overwriting newer iOS/API files.

## Immediate next item

Execute the **Gate I physical-device and Play release matrix** using `play-release-runbook.md` once its external inputs exist. C82 closes the current complete cross-API emulator matrix at 280/280 on API 26/33/35 (840/840 total) with a checked-in bounded runner and repeats Debug/Release JVM, Lint and Release assembly gates. Production Firebase deployment, a release-owner-managed upload key, Play Console access and the two missing physical device classes remain required; do not mark device-only rows verified from emulator evidence.
