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
- [x] Record A302SH physical-device evidence for `SH-007/SH-009` under C67 and fail-close the Release backup contract under C88; exact Play reinstall/OEM transfer and remaining device classes stay in `RG-006/RG-007` rather than this obsolete umbrella row.

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
- [x] C83 Recheck `origin/main`/`origin/staging` drift without merging iOS-only staging commits; align Android CI with the C82 contract by validating the bounded runner and running Debug plus Release JVM suites before the existing build/Lint/Release-compatibility/Play-asset gates; verify production runtime and signing remain fail-closed without approved inputs.
- [x] C84 Replace the length-only Supabase client-key gate with explicit publishable/legacy-anon validation; reject `sb_secret_...`, `service_role`, wrong-issuer, opaque-long and malformed inputs; add a synthetic-only Gradle contract task as a production-runtime dependency and CI gate; pass both JVM variants, Lint, Release compatibility and Play assets without reading or emitting a real credential.
- [x] C85 Make `bundleSignedRelease` verify the generated AAB structure, full JAR signature, single signer and registered upload-certificate SHA-256; support exact signed/unsigned Release APK filenames; add a synthetic success/mismatch/unsigned/partially-signed CI contract; pass a complete synthetic Gradle signed-bundle integration, remove every generated key/artifact, then pass the normal unsigned JVM/Lint/APK/asset gates.
- [x] C86 Resolve and inventory the exact Release runtime dependency graph; require Firebase Analytics/Messaging/Installations; fail closed on unapproved ads, billing, Install Referrer, crash/performance and third-party attribution/analytics SDKs; explicitly distinguish Firebase Analytics' known advertising-ID/Privacy Sandbox support transitives from actual app permission use; require the policy from Release compatibility, signed-bundle and hosted-CI gates; pass both JVM variants, Lint, Release compatibility and Play assets.
- [x] C87 Enable strict dependency locking only for `releaseRuntimeClasspath`; check in the complete generated lock state; make it an explicit Release SDK policy input; prove the checked-in state resolves and missing state fails closed; document the intentional dependency-update/review procedure without locking unrelated Debug/test configurations.
- [x] C88 Parse the actual merged Release APK manifest and fail closed on debug/test/profileable flags, backup/cleartext relaxation, Firebase auto-init, permission/exported-component/FileProvider/auth-link drift and weakened permission guards; add eleven synthetic contracts plus the actual APK gate to hosted CI; keep exact signed-AAB/Play inspection external.
- [x] C89 Strict-lock bundletool CLI; validate the generated AAB, dump and recheck its protobuf base manifest with C88, reject non-base modules and embedded private configuration/key material, print structural counts/hash, require the gate from `bundleSignedRelease` and hosted CI, and keep release-owner signing/Play scan external.
- [x] C90 Verify the configured upload keystore before AAB generation: require the selected alias to be a usable private-key entry whose certificate matches `PLAY_UPLOAD_CERT_SHA256`; reject wrong certificate/alias/store password/key password, malformed fingerprint, missing store and trusted-certificate-only aliases; run the contract in hosted CI and pass a complete synthetic Gradle signed-bundle path without treating its key or artifact as production evidence.
- [x] C91 Generate one machine-readable exact-artifact ledger only after the complete signed-bundle graph passes; fail on dirty Android/consumed asset inputs or invalid app/version/source/hash/signer/module/dependency identity; record commit, baseline, versions, AAB/manifest/certificate hashes, structure, dependency lock/SDK inventory and gate set; exercise the policy in hosted CI while keeping the release-owner artifact and Play evidence external.
- [x] C92 Atomically package the exact signed AAB and C91 ledger with `SHA256SUMS` in a commit/version-named three-file handoff; revalidate production/clean/base/gate/hash/certificate identity, reject tampered sources or existing packages, symlinks, missing/extra files and incomplete gates; make `bundleSignedRelease` end at that handoff and run the contract in hosted CI.
- [x] C93 Generate a universal APK Set from the exact AAB with strict-locked bundletool and an ephemeral test key; require exactly `toc.pb` and `universal.apk`, atomically extract and prove that APK's test signer, then reapply Release manifest/SDK/permission/exported/App Links/16 KB policy. Add the gate to signed evidence and hosted CI without placing the test-only APK in the Play handoff or claiming Play app-signing/install evidence.
- [x] C94 Build the complete synthetic-signed APK Set once and select exact base/ABI/Japanese/density split quartets for API 26 arm64, API 33 x86_64 and the observed API 35 A302SH specification; validate deterministic split identities including bundletool master variants, package/certificate/DEX/native/16 KB structure and the full base policy; physically install the A302SH quartet without launching, verify exact installed split/version identity, remove it, and keep Play app signing/generated splits/track installation external.
- [x] C95 Add fail-closed production and Play-installed App Links release gates: require exact redirect-free `www` HTTP/JSON/cache/package/relation/Play app-signing certificate identity, require a physical API-31+ package installed by Google Play with the same signer and only the canonical domain in `verified` state, and reject 30 malformed/web/device fixtures. Remove the redirecting apex host from the Release manifest after confirming Android requires a Digital Asset Links file without redirects on every declared host. Keep the currently 404 production deployment and Play-installed execution external until the Android API contract is merged and the release owner supplies the Play app-signing certificate.
- [x] C96 Add a fail-closed committed `android-dev` to `main` merge-surface gate: require fresh `origin/main` as exact ancestor/merge-base; allow only Android, `docs/android`, reviewed `.gitignore`/Android CI and the exact 30-file API contract; reject iOS/unreviewed scope, ignore override, deletion/rename, private/generated artifacts, unsupported modes and bounded-file/tree overflow. Exercise one accepted and ten rejected temporary Git repositories, retain the pre-gate 385,664,936-byte high-fidelity evidence tree under explicit limits, print fresh counts on every run, and execute the ancestry-aware task in hosted CI without authorizing the merge or closing Play/physical gates.
- [x] C97 Make the Android mutation-idempotency migration an executable legacy-upgrade contract: statically allow only the four reviewed additive SQL statements; refuse remote databases by default; upgrade an isolated PostgreSQL pre-migration fixture with six retained rows; prove nullable/default-free columns, patient-scoped index order, legacy duplicate-null compatibility, cross-patient key reuse, same-patient duplicate rejection and schema cleanup. Add the contract before normal migrations in API CI and intentionally expand C96 to the two reviewed workflows and exact 32-file API allowlist without claiming production deployment or real interruption evidence.
- [x] C98 Add a privacy-safe deployment observation gate without deploying production: require explicit matching preflight/postdeploy mode, a second remote opt-in, TLS/public-schema restriction and a database-enforced read-only transaction; expose counts only; verify exact absent preflight state or exact successful Prisma checksum/columns/valid indexes/zero duplicate groups postdeploy state. Exercise two accepted/twelve rejected fixtures plus a real full-migration postdeploy pass, add both phases to API CI, and expand C96 to the exact 34-file API allowlist while keeping deployment and uncertain-response recovery external.
- [x] C99 Replace drifting narrative-only residual tracking with one canonical `release-gates.json`: inventory the exact six PARTIAL requirements and ten external gates, encode authority/dependency/evidence/done conditions, reconcile the stale SH-007/SH-009 row, and fail CI on status, checkbox, prerequisite, baseline, checkpoint, evidence-path or coverage drift. Keep every real external gate unchecked until its matching evidence exists.
- [x] C100 Add the fail-closed RG-002 production execution path without running it: manual `main` dispatch only, exact full-SHA and mode confirmation, default read-only preflight, reviewer attestation plus independent release arm for writes, migration-before-Vercel ordering, postdeploy/health/App Links checks, pinned CLI, count-only evidence and explicit cleanup. Disable `main` Git auto-deploy, lock the reviewed workflow/config/secret/variable contract in API CI, expand C96 to exactly three workflows/37 API files, and leave RG-002 unchecked until the protected environment exists and an authorized run supplies real evidence.
- [x] C101 Remediate the dependency audit exposed by C100 without broad application upgrades: refresh only the lock-resolved Next.js/fast-uri/nanoid/postcss/sharp and brace-expansion/js-yaml chains to patched versions, prove `npm audit --audit-level=high` passes with zero High/Critical findings across runtime and build dependencies, run it in API CI and before any production database access, preserve and document the remaining Moderate transitive findings, expand C96 to 38 API files and rerun the complete API/migration/build contracts.
- [x] C102 Align the supported API runtime instead of tolerating Node/Prisma/Vercel drift: pin package/Vercel to `22.x`, move all five API CI/E2E/production setups to Node 22, align Prisma CLI/client/adapter 7.9.1 and Node 22 types, upgrade the already-modular FCM-only Firebase Admin path to 14.2.0, resolve the Node crypto JWK type boundary, reduce Moderate findings 13 -> 6, and fail CI on runtime/package/workflow/trigger drift with one accepted/nineteen rejected fixtures. Expand C96 only to four workflows/42 API files and rerun DB/API/build/E2E gates without executing production.
- [x] C103 Fail fast on unusable physical UI targets: require explicit awake/unlocked device signals before installation and before each shard without changing its lock/power policy; prove awake success, Doze/keyguard/unknown-state rejection before Gradle and exact cleanup/status preservation on shard failure with a synthetic runner contract; execute it in hosted Android CI. Keep the fresh unlocked A302SH four-shard rerun in V01 physical evidence rather than making manual device state part of this runner implementation contract.
- [x] C104 Authenticate every production database TLS endpoint: pin the reviewed Supabase Root 2021 CA for API runtime with CA/hostname verification, normalize away connection-string SSL overrides, reject unreviewed production hosts, and make the protected workflow download/fingerprint/use/delete the same CA through exact `verify-full` plus an absolute root path. Prove strict audit/secret/order/cleanup contracts and live read-only TLS; leave Supabase incoming-SSL enforcement and all migration/deploy work in RG-002 because enabling enforcement restarts the database.
- [x] C105 Audit the actual Play ownership boundary before assuming signing or tracks exist: verify read-only that the selected account reaches developer signup, classify Medical / Medication and Treatment Management as a Health app, require Organization—not Personal—ownership with D-U-N-S/legal/payment/website/contact verification, record the current one-time US$25 fee without paying it, and make every signup/agreement/payment/identity/app/key action explicitly owner-controlled and privacy-safe.
- [x] C106 Close the automatable Play User Data/Health review boundary: add a stable unauthenticated `/account-deletion` page, disclose exact deleted/retained categories and security handling, link it from privacy/support/footer, and enforce a source/release-mode contract for verified Organization identity plus reusable region-independent caregiver review access to the retained dedicated QA patient without storing or reading credentials. Keep legal identity, final credential/artifact verification, deployment and Console submission external.
- [x] C107 Rebuild the Japanese Play listing handoff from the latest post-parity production Compose evidence: replace the stale caregiver next-dose marketing surface, pin all eight exact evidence sources, enforce package/app-name/billing/ads/privacy/health/deletion disclosures against shipping Android configuration, and prove every padded JPEG still derives from its mapped source at pixel level. Keep live URL, Play preview, exact signed-artifact comparison and Console submission external.
- [x] C108 Remove the manual macOS-only screenshot export gap: add a JDK-backed deterministic 90%-quality renderer, an explicit source-updating task, byte-for-byte committed-output verification and synthetic determinism/invalid-canvas/invalid-quality contracts. Regenerate all eight C107 images through it and keep Play preview/exact-artifact comparison external.
- [x] C109 Complete the fresh awake/unlocked A302SH four-shard regression at 280/280 and make its result durable: require exactly one parseable nonempty zero-failure/error/skip XML per shard, preserve all four reports plus an aggregate TSV, reject missing/skipped evidence, and clean both packages on every exit. Record one non-reproducible OEM/platform `libhwui` RenderThread crash, the implicated test's clean-install 10/10 isolation pass and the subsequent full pass without weakening closed-test crash/ANR monitoring.
- [x] C110 Bind the source-controlled Play handoff to the exact signed release: upgrade the evidence ledger to schema v2 with hashes for Japanese listing, source map, icon, feature graphic and ordered eight screenshots; make handoff creation re-hash those repository inputs and reject schema downgrade, missing fields/assets, substituted hashes and reordered screenshots. Keep release-owner signing, upload, Console preview and declarations external.
- [x] C111 Exercise the complete schema-v2 ledger-generation path with an isolated committed Git tree, disposable PKCS12 signer, AAB-shaped signed ZIP, production `keytool` certificate extraction, dependency/store hashes and dirty-listing rejection. Make RG-005 directly retain C110-C111 and their evidence without treating the synthetic signer as release-owner proof.
- [x] C112 Feed the production schema-v2 generator output directly into `prepare_handoff`; require the exact renamed AAB, byte-identical ledger and SHA256SUMS, prove idempotent re-entry, and reject a store change made after ledger generation. Make hosted CI track both implementations and RG-005 retain the bridge evidence.
- [x] C113 Add a read-only retained-handoff verifier for upload time: require the ledger-derived directory name, exact three files, canonical schema-v2 JSON, checksums, AAB identity and current ordered store hashes; reject extra/checksum/noncanonical/store/rename/symlink drift and document the exact command in the runbook.
- [x] C114 Add a fail-closed Google Play upload receipt verifier: accept only the official v3 Bundle resource or bundles-list envelope, require one exact `versionCode` plus upload-payload SHA-256 match against a freshly reverified C113 handoff, and atomically retain a deterministic secret-free receipt outside the three-file handoff. Reject malformed/duplicate/wrong-hash responses, unsafe paths, output conflicts and handoff drift; run the contract in hosted Android CI while keeping real Play API access and upload external.
- [x] C115 Remove the Android CI action-runtime deprecation gap: replace mutable Node 20 action majors with the signed official checkout v6.1.0, setup-java v5.7.0 and setup-gradle v6.3.0 release commits pinned by full SHA; restrict the workflow to `contents: read`, disable checkout credential persistence, retain full ancestry and Temurin 17, and reject twenty-three action/permission/runner/trigger/runtime drifts in a dedicated hosted contract. Require a warning-free rerun without changing other workflows or external release-gate status.
- [x] C116 Add a fail-closed Google Play Internal-track receipt verifier: revalidate the existing C114 upload receipt against the same handoff/Bundle response, require the official post-commit releases-list plus fresh inspection-edit Track schemas, `qa`, one exact `versionCode`, `RELEASE_LIFECYCLE_STATE_PUBLISHED` and `completed`, then atomically retain a deterministic secret-free chained receipt outside the handoff. Reject schema/path/receipt/version/track/lifecycle/status drift in hosted CI while keeping real Play access, publication, signing and installation external.
- [x] C117 Bind Google Play generated APK signing metadata to that exact C116 version: validate the official `generatedapks.list` signing-key groups, production targeting and base-master split presence; require the complete normalized app-signing certificate set to equal App Links and remain disjoint from the retained upload certificate; atomically retain only fixed identity, response hashes, public fingerprints and aggregate counts. Reject thirty-six response/chain/certificate/split/path drifts in hosted CI while keeping real API retrieval, APK download and device installation external.
- [x] C118 Bind downloaded Play base-master APK bytes to the complete C117 chain: require globally unique response download IDs and exactly one base-master selection per app-signing-key group; verify bounded safe ZIP/manifest/DEX content, production package and exact version, one matching signer and a v2-or-newer embedded signature with Android SDK tools; require stable APK bytes across inspection and revalidate C117 before output; retain only fixed identity, C117/tool/public certificate/APK hash and aggregate facts. Reject forty-six chain/selection/artifact/package/version/tool/signature/TOCTOU/output drifts in hosted CI while keeping real Play download, installer identity and physical install/update external.
- [x] C119 Bind one physical Play-installed package directly to C118 without installing or launching it: require an explicitly selected authorized API-31+ non-emulator target, Google Play installer, exact version and complete app-signing set, at least one optimized split and only the canonical verified App Link; stream and hash installed `base.apk` bytes and match exactly one C118 hash/size row; repeat package/base/C118 checks after verification; write an atomic serial/path/account-free receipt. Reject forty receipt/device/package/version/path/byte/App-Link/output drifts in hosted CI while keeping the real install, prior-version update and behavioral device matrix external.
- [x] C120 Add an owner-side read-only GitHub production control-plane preflight: require the reviewed protected `main` SHA, exactly one active default-branch workflow, one protected `android-api-production` environment, required reviewers with self-review prevention, protected-branch-only deployment, exactly three secret names/four variable names and an explicit safe or armed release value; accept the token only through an environment variable and never print token/values/raw metadata. Exercise seven accepted and thirty-four rejected fixtures in API CI, bind C120 into RG-002/current merge scope, and retain the live absent-environment/workflow plus health/App-Links/deletion observations without creating or changing external state.
- [x] C121 Remove the hosted API Node 20 action-runtime warnings found after C120: verify official checkout v7.0.1, setup-node v7.0.0 and upload-artifact v7.0.1 release/tag SHAs plus `node24` metadata; pin all thirteen API CI/E2E/Production action uses by full SHA, set `contents: read`, disable checkout credential persistence, and expand the Node runtime contract to reject twenty-five mutable/old/extra action, count, permission, credential and runtime drifts. Require exact hosted API CI/E2E conclusions and zero action-runtime annotations in RG-002/RG-010.
- [x] C122 Re-establish UI evidence from the unmodified published iOS 1.0.6 Build 51 runtime instead of pre-release captures: recapture the exact Patient Today preview, match Android progress/hero/time/medicine/disabled-action presentation, retain raw/normalized/side-by-side/overlay evidence, add an exact fixture regression and regenerate Play screenshot 02. Keep the other seven store surfaces open for the same published-build recapture audit.
- [x] C123 Rebaseline UI-001 Mode Select from a clean unmodified published iOS runtime and production API-35 Compose: verify the shared layout/type/illustrations/copy, replace Android's bottle header with the published pills glyph, make badge symbols neutral like SwiftUI, retain raw/normalized/side-by-side/overlay evidence, add the exact fixture regression and regenerate Play screenshot 01. Keep the remaining six store surfaces open.
- [x] C124 Rebaseline UI-104 Patient History from the unchanged published iOS runtime and production Compose: align filled clocks, shared 34/17 Patient header, streak accent/dark-teal hierarchy and exact `5/7日` fixture; retain the production seven-day/`回分` contracts over preview-only shortcuts; refresh C122 Today after the shared header change; retain exact comparison evidence and regenerate Play screenshots 02–03. Keep the remaining five store surfaces open.
- [x] C34 Accept strict caregiver `DOSE_MISSED` payloads alongside `DOSE_TAKEN` and verify identical exact-date/slot History routing.
- [x] C35 Rerun the full API/JVM/lint/build/API-35 matrix and close all C31 `RECHECK_REQUIRED` rows.
- [ ] V01 Complete the assisted spoken TalkBack traversal, real destructive/mutation-interruption paths, old-supported physical target, Google/reference target, Analytics Explore, Firebase FCM and exact signed-Play artifact rows. C65-C124 cover authenticated appearance, core lifecycle, permission, local reminder lifecycle, safe I/O/IME/offline/role isolation, mutation hardening, isolated legacy-upgrade/read-only deployment-audit and manual fail-closed production-release/control-plane contracts, Play policy/review-access, a reproducible source-bound Japanese listing with Mode Select, Patient Today and Patient History freshly rebaselined to the published build, complete disposable-signer generator-to-handoff integration, retained upload-time revalidation, exact Play Bundle/Published-Internal/generated-signing/downloaded-base-APK/installed-package receipt contracts and least-privilege immutable Node 24 Android/API workflow runtimes, a zero-High/Critical dependency audit, aligned Node 22/Prisma/Firebase runtime, verified Supabase CA/hostname TLS boundaries and an explicit Play Organization-account prerequisite, plus a machine-verified residual ledger, fail-fast physical runner readiness, a fresh durable A302SH 280/280 rerun, physical Analytics through processed Events, both Android FCM envelopes, token acquisition/cleanup, the authenticated production registration failure boundary, automated navigation/tutorial accessibility semantics, a fresh API 26/33/35 emulator matrix, aligned hosted CI, privileged-client-key rejection, synthetic signed-AAB/upload-keystore/APK verification, machine-readable exact-artifact evidence, atomic checksum handoff, AAB-derived universal/device-split install-surface verification, strict production/installed App Links contracts and the committed main-merge surface, strictly locked Release SDK policy and validated APK/AAB manifest/content policies. They do not close spoken real-finger operation, creation/protection of the production GitHub environment, server-side SSL enforcement, verified Play organization ownership/legal-name publication, authorized production migration/deployment, final reusable review-credential verification, real interrupted-write recovery, release-owner-signed/uploaded/published/generated/downloaded Play bytes or execution of their installed receipt, live URL/Play listing preview, current vendor-disclosure/Console review, App Links/FCM production verification until the Android API contract is deployed, six Moderate Firebase Storage/uuid transitive findings, Play closed-test crash/ANR monitoring, cross-device physical acceptance, or the remaining five published-build UI recaptures.

### C99 canonical residual release gates

- [ ] RG-001 Firebase Analytics Explore verification
- [ ] RG-002 Production API migration and deploy
- [ ] RG-003 Real mutation interruption recovery
- [ ] RG-004 Production App Links and Android FCM
- [ ] RG-005 Play Organization account, signing and exact AAB handoff
- [ ] RG-006 Play Internal install and update verification
- [ ] RG-007 Old-supported and Google-reference physical devices
- [ ] RG-008 Assisted spoken TalkBack traversal
- [ ] RG-009 Play Console Data safety and Health declarations
- [ ] RG-010 Closed test, final rebaseline and main merge

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

Execute the **Gate I physical-device and Play release matrix** using `play-release-runbook.md` once its external inputs exist. C82 closes the current complete cross-API emulator matrix at 280/280 on API 26/33/35 (840/840 total) with a checked-in bounded runner; C83 aligns hosted CI with that runner contract and both JVM variants while retaining build/Lint/Release-compatibility/Play-asset gates. Production Firebase deployment, a release-owner-managed upload key, Play Console access and the two missing physical device classes remain required; do not mark device-only rows verified from emulator or hosted-CI evidence.
