# Android Port Master Development Plan

**Status:** iOS 1.0.6 emulator parity complete; physical/release gates open
**Development branch:** `android-dev`
**Reference:** published iOS 1.0.6 Build 51, `main@432b34c`
**Stack:** Kotlin, Jetpack Compose, Material 3

## 1. Outcome

Build a native Android app that reproduces the pinned iOS product's behavior, business rules, information architecture, Japanese copy, accessibility intent and visual identity. Android-native system interaction is used only where the platform requires it.

Completion means evidence-backed parity, not the presence of similar screens.

## 2. Non-negotiable rules

1. Android work stays on `android-dev` until the release gates pass.
2. Backend routes/tests are the business-rule authority; current iOS behavior/tests are the client authority.
3. Pin a source SHA. Never chase a moving `main` implicitly.
4. Define auth, request, response, error, time-zone and idempotency contracts before UI.
5. Model loading, cached/updating, empty, content, validation, offline, retry, auth, forbidden, conflict, rate-limit, partial success and retention states where applicable.
6. A successful write is not rolled back visually by a failed follow-up read.
7. Product copy comes from localization resources; no production UI hardcodes Japanese in Kotlin.
8. Production components power fixtures and screenshot tests. Screenshot-only duplicate UI is prohibited.
9. Visual and accessibility verification occurs per vertical slice, not at the end.
10. Any intentional Android difference is recorded with rationale and acceptance criteria.
11. New product ideas are separated from parity work and require explicit approval.
12. A requirement is complete only at `VERIFIED`, with physical-device evidence where required.

## 3. Architecture boundaries

Dependency direction:

`Compose route -> state holder -> use case/repository -> typed data source -> API/platform`

- Compose does not construct URLs, parse JSON, inspect tokens or encode backend policy.
- Every endpoint uses an explicit `PUBLIC`, `PATIENT` or `CAREGIVER` auth policy.
- DTOs mirror wire shape. Domain models express app meaning. UI models contain formatted display state.
- Session, selected patient, preferences, freshness revisions and navigation targets have distinct storage/state owners.
- The backend owns authorization, inventory, entitlement, retention, linking and record idempotency.
- Android owns Keystore-backed secrets, runtime permissions, local alarms, FCM token lifecycle, content URI sharing, system back and process recreation.

## 4. Required documents

- `source-baseline.md`: pinned truth and change control
- `api-contracts.md`: auth/HTTP/domain contracts
- `ui-screen-contracts.md`: information architecture and screen-state requirements
- `ui-fidelity-spec.md`: visual comparison process
- `parity-requirements.md`: status and evidence ledger
- `current-gap-audit.md`: current implementation delta
- Phase notes: implementation evidence, not higher authority than the files above

## 5. Vertical-slice procedure

Every slice follows this order:

1. **Pin references**: list iOS views/view models/DTOs/tests, API routes/services/tests and relevant localization key groups.
2. **Capture current iOS**: deterministic screenshots for content and exceptional states before writing Compose.
3. **Write contract tests**: method/path/auth/body/response/error/time-zone fixtures.
4. **Define state machine**: events, state transitions, cancellation, retries, optimistic behavior and invalidation consumers.
5. **Implement data/domain layer**: no UI until contracts pass.
6. **Build shared components**: tokens, accessibility semantics and deterministic fixtures.
7. **Implement navigation/interaction**: back, permission, keyboard, lifecycle, process recreation and deep links.
8. **Run automated gates**: unit, contract, Compose, lint, debug/release builds.
9. **Compare visuals**: matched screenshots, side-by-side and overlay/diff; fix material deltas immediately.
10. **Verify real behavior**: emulator, safe live API smoke, then physical device.
11. **Update evidence**: change matrix status only after recording results.

## 6. Rebaselined execution phases

### R0 — Baseline integrity and regression repair

Goal: keep the Android foundation conformant with the explicitly pinned published `main@432b34c` product contract before release verification.

1. Add explicit per-request auth policies; make link exchange public/no-auth and non-invalidating.
2. Add link-error localization fixtures and canonical UI mapping.
3. Prove uninstall/reinstall and restore cannot resurrect a patient token.
4. Add a shared data-freshness revision/event model for Today, History and Inventory.
5. Move scheduled-dose reminder rebuilding off the record critical path; rebuild only after actual scheduled changes.
6. Test next-day and month-boundary reminder retention.
7. Preserve lazy tab instances/state and block hidden-tab input/accessibility.
8. Recapture changed patient iOS states and recheck affected patient matrix rows.
9. Preserve immediate Patient/Caregiver mutation feedback while authoritative post-write reconciliation runs outside the blocking UI path.

**Exit:** all recheck rows return to at least `IMPLEMENTED`; build/test/lint pass; no new caregiver UI yet.

### R1 — Shared production architecture

1. Replace feature-boundary `JSONObject` handling with typed serialization.
2. Introduce role-aware repositories/state holders and selected-patient persistence API.
3. Move every user-visible Kotlin literal into resources.
4. Split oversized patient UI into routes/components/state owners without changing behavior.
5. Establish screenshot fixtures, fake clock, deterministic Tokyo calendar and fake permission/push adapters.

**Exit:** shared architecture supports both roles without auth or policy branching in Compose.

### P1 — Entry/auth and patient parity verification

Reverify existing entry/auth and patient features against current iOS:

- Mode select and caregiver auth flows
- Patient linking/session restoration
- Patient Today individual/bulk/PRN/detail
- Patient history month/day/retention
- Patient notification settings, tutorial, deep links, accessibility and dark theme

This is a verification/repair phase, not a declaration that earlier code is automatically accepted.

### C1 — Caregiver shell and patient management

1. Five-tab persistent shell and lazy tab lifetime
2. Patient list/create/select and sole-patient auto-selection
3. Time presets and selected-patient propagation
4. Linking-code issue/copy/share
5. Revoke versus permanent delete semantics
6. Caregiver tutorial steps that operate on the real flow
7. No-patient and data-unavailable states shared across tabs

### C2 — Medication and regimen

1. Medication list filters/empty states
2. Add/edit regular and PRN medication forms
3. Date, dose, strength, notes and inventory validation
4. Daily/weekday regimen CRUD using patient slot presets
5. Mutation invalidation of Today, Inventory, History and notification schedule

### C3 — Caregiver Today and inventory

1. Today monitoring and status aggregation
2. Individual proxy record/delete
3. Caregiver bulk recording of older missed slots without patient window restriction
4. PRN recording/deletion
5. Mutation-success/follow-up-refresh-failure preservation
6. Inventory list/filter/detail/enable/quantity/adjust/refill
7. Low-stock badge propagation

### C4 — Caregiver history, PDF, settings and account lifecycle

1. Month/day history and mutation freshness
2. Remote push exact date/slot navigation/highlight
3. Retention role differences
4. PDF presets/custom validation/on-device generation/share
5. Push enable/disable and token lifecycle
6. Legal/support, logout and server-first account deletion

### X1 — Analytics, privacy and cross-platform hardening

1. Firebase Analytics with collection off by default
2. Explicit consent and reset-on-disable
3. Exact fixed-enum event parity; no identity, patient, medication, dose, inventory, date/time, free-text or token parameters
4. Preview/test/`disableAnalytics` suppression
5. DebugView, Realtime, Events and Explore verification procedure
6. FCM process-death routing, offline behavior, app links and Android backup/data extraction rules
7. Google Play billing remains out of scope until a Play-specific backend claim contract is approved; do not send StoreKit payloads from Android

### V1 — Release verification

1. Full automated regression for debug and release variants
2. Physical device matrix, TalkBack, 2.0 font, dark mode, notification delivery/taps, Doze and lifecycle
3. Performance and network-failure runs
4. Security/privacy review and dependency scan
5. Data safety and health-app declarations
6. Signed internal/closed test, feedback repair and rollout plan

### Current checkpoint — C78 (2026-08-17)

- R0–C4 and the automated portion of X1 are implemented against published `main@432b34c`.
- C62 reopened the initial C61 conclusion with a direct source audit and closed the expandable History, Today summary, medication form and inventory gaps. C63 then corrected the tutorial implementation after direct comparison with `PatientTutorialSampleView`, `CaregiverTutorialSampleView` and `GuidedTutorialOverlay`: Android now uses dedicated published sample screens for all 14 steps, exact fixed copy/data and the compact role-colored guide card. The current suite passes 202/202 JVM tests, Lint and 272/272 UI tests on API 26/33/35.
- C64 audits all 69 parity rows against current code/evidence: 63 implementation rows are complete and six external rows remain. It closes the only new local finding by replacing medication-form validation strings and inventory-calculation presentation text from the data layer with typed values formatted by Android resources. Debug/Release builds, Release APK compatibility, Play assets and the affected cross-API slice pass after the correction.
- C65-C68 execute the available A302SH/API 35 physical slice. C68 closes local primary/secondary, foreground/background, task-removed tap, forced Doze and wait-through-target cancellation with generic slot-only content through a Release-excluded Debug diagnostic. The final gate passes 273/273 Compose and 206/206 JVM tests, Lint, both APK assemblies, Release compatibility and Play assets.
- C69 extends the same device slice without source or health-data mutation: exact legal URLs and Back restoration pass from Patient/Caregiver Settings, live ephemeral linking-code copy/chooser opens without sending, denied/force-stop/uninstall reminder branches remain silent through their windows, and local App Standby is documented as a single delivery deferred until exit.
- C70 executes the safe read-only `XP-007` transition on the dedicated QA Caregiver: online baseline, same-process cached offline retention with stale/Retry, cold offline role restoration with initial recovery actions, and validated-Wi-Fi Retry recovery all pass. The run exposed a missing Caregiver Today patient-list Retry action; the production UI and regression test were corrected. Final gates pass 274/274 Compose, 206/206 JVM, Lint, Debug/Release assembly, Release APK compatibility and Play assets.
- C71 proves the same-installation role-isolation contract on the configured Debug artifact. Caregiver logout persists through force-stop/relaunch and later reauthentication; one process-only ephemeral link code enters Patient mode; Patient logout then makes Caregiver require account login and Patient require a new code. Neither role surface exposes the other's data, and the device ends at role selection with both sessions logged out.
- C72 closes the Patient-link OEM IME gap discovered by direct source/device audit. The six-digit numeric field now exposes `Done`, submits only when exactly six digits and not loading, and remains inert for incomplete/loading states. A302SH production-runtime testing with a non-existent code reaches the typed expired/not-found response without tapping the button; five digits remain local and disabled. Final gates pass 275/275 Compose and 206/206 JVM tests, Lint and Debug/Release assembly.
- C73 closes the remaining safe Caregiver form input slice. Patient creation now exposes guarded `Done`; medication basic fields explicitly traverse name -> strength -> unit -> dose, supply days -> initial inventory, and both group terminals clear focus without saving. A direct A302SH production-account pass exercises the medication path with a local-only draft and proves the visible medication-list count is unchanged. The audit also found and fixed system Back leaving the editor/app instead of returning to the medication list. Patient-create Done/guarding and Back behavior pass as physical instrumentation fixtures without creating or deleting server data. Final gates pass 278/278 Compose and 206/206 JVM tests, Lint, Debug/Release assembly, Release APK compatibility and Play assets.
- C74 closes the local single-flight/cancellation implementation gap exposed by auditing the still-open physical destructive/network matrix. Patient individual/bulk/PRN/revoke, Caregiver Today individual/delete/bulk/PRN, medication save/delete, inventory, patient/create/settings/link/destructive, and History backfill mutations now share one nonblocking repository mutation boundary per owner. Cancellation clears the visible busy state and releases the boundary, but never retries or reports success. Six high-latency regression families prove cross-type duplicate rejection, no automatic replay, local-state preservation and explicit manual retry. Confirmed medication core writes publish freshness before regimen reconciliation. The safe A302SH synthetic UI suite passes 278/278; Debug JVM 212/212, Release JVM 209/209, Lint, both APK assemblies, Release compatibility and Play assets pass. Real interrupted-write ambiguity and server idempotency remain V01 physical/API evidence.
- C75 audits the server boundary behind that client single-flight contract. Scheduled individual-dose creation now assigns side-effect ownership to the sole successful natural-key insert, while PRN and caregiver inventory-adjust requests accept a nullable UUID-v4 client mutation identifier. Android retains one identifier for the same in-process uncertain PRN/refill/correction operation and sends a new identifier for changed intent; legacy iOS requests remain valid. API replay/race/validation tests and Android body/retry tests pass. Full gates pass API 332/332, Debug JVM 216/216, Release JVM 213/213, Lint, both APK assemblies, Release compatibility, Play assets and A302SH synthetic UI 278/278. The migration is committed but deliberately not deployed from this local gate; real interrupted-network behavior remains V01 evidence.
- C76 executes the privacy-first Firebase gate instead of treating configuration as an indefinite external placeholder. The Android app is registered for the production package and its four values live only in GitHub Actions secrets or ephemeral local environment. Physical testing exposed that manual `FirebaseOptions` initialization alone leaves Analytics disabled with `Missing google_app_id`; Gradle now generates the standard Firebase resources while manifest collection and FCM auto-init remain off. A302SH proves pre-decision/refusal suppression, consent-on fixed-enum upload, both-role shared toggles, reset/resume, no custom identity/health/free-text keys, no Firebase user ID, DebugView `tab_name` inspection and Realtime aggregation. Final gates pass Debug JVM 216/216, Release JVM 213/213, Lint, both APKs, Release compatibility, Play assets and A302SH synthetic UI 278/278. Processed Events/Explore and FCM remain external evidence.
- C77 reaudits the FCM sender before generating physical traffic and finds that missed-dose delivery still supplied the iOS patient-specific notification envelope to Android. `DOSE_MISSED` now follows the existing `DOSE_TAKEN` platform branch: Android receives only strict navigation data with high priority and renders generic local copy, while iOS remains unchanged. The regression proves the Android data payload excludes the display name. Targeted push tests pass 16/16 and the complete API gate passes 333/333, typecheck and ESLint; physical FCM remains V01.
- C78 adds a reproducible Firebase Messaging handshake preflight without exposing or retaining an FCM token. Its Debug-only receiver requires `android.permission.DUMP`, reports status codes only, disables auto-init, deletes the Firebase token and clears app-owned token state; Release manifest inspection proves it is absent. A configured A302SH returns `TOKEN_READY` then `CLEANUP_READY`, a second cleanup succeeds, and uninstall leaves no app package. Debug JVM 216/216, Release JVM 213/213, Lint, Release assembly/compatibility and Play assets pass. Authenticated backend registration and actual FCM delivery remain V01.
- The repeatable privacy-first Firebase procedure is `firebase-analytics.md`; DebugView/Realtime evidence is `evidence/h07-20260817/README.md`, while processed Events/Explore still require a later observation.
- The physical release procedure is `physical-device-matrix.md`; its row-level evidence still requires old-supported, current-reference and non-Google OEM targets plus the exact signed Play Internal artifact.
- C65-C78 now cover one non-Google OEM/API 35 Debug target, including local reminder lifecycle, safe browser/link-code I/O, link/auth/create/medication IME behavior, read-only offline recovery, same-installation role isolation, synthetic mutation single-flight/cancellation hardening, local PRN/inventory server-idempotency contracts, live privacy-first Analytics, privacy-safe Android envelopes for both FCM event types and non-retaining FCM token acquisition/cleanup. V1 remains open for API 26–28 and Google/reference devices, migration deployment plus real destructive/mutation-interruption behavior, complete spoken TalkBack, Analytics Events/Explore, authenticated Firebase FCM registration/delivery, a release-owner signed AAB, Play Internal/Closed testing, Console declarations and the final pre-merge main rebaseline.

## 7. Automated quality gates

Run from `android/` unless noted:

- Unit/contract tests for debug and release variants
- Compose instrumentation tests on the supported emulator API range
- `lint`
- `assembleDebug` and release-like assembly
- `git diff --check`
- Backend contract/integration tests when Android depends on changed server semantics

High-risk flows additionally require tests for process death, concurrent refresh, double tap/idempotency, stale follow-up response, Tokyo date boundary, permission denial and accessibility tree state.

## 8. Phase exit gate

A phase exits only when:

- Every scoped row has current automated evidence.
- No scoped row remains `RECHECK_REQUIRED`, `PARTIAL` or `BLOCKED` without an approved phase boundary.
- Required screen states have matched current iOS captures.
- Debug and release-like build/test/lint pass.
- Safe live API smokes pass for the phase's mutations and error families.
- Emulator and required physical-device checks pass.
- No P0/P1 defect remains.
- Documentation names exact evidence and source/build SHAs.

## 9. Merge-to-main gate

Android is ready to merge only when:

- All release-scope rows are `VERIFIED`.
- The final rebaseline against then-current `main` produces no unresolved contract/UI drift.
- Release signing, Firebase Android app configuration and Play Console declarations are production-ready.
- Closed testing meets the agreed stability threshold.
- Main merge contains Android files/docs only plus intentionally shared changes already present on main; it does not backflow stale iOS/API files.
