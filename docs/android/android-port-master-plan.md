# Android Port Master Development Plan

**Status:** Local implementation complete; external release verification in progress
**Development branch:** `android-dev`
**Reference:** `main@3e52fb2`
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

Goal: keep the Android foundation conformant with the explicitly pinned `main@3e52fb2` product contract before release verification.

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

### Current checkpoint — C59 (2026-07-16)

- R0–C4 and the automated portion of X1 are implemented against `main@3e52fb2`.
- Current iOS/Android emulator-verifiable UI states are recorded through C56; C57 passes the complete 259-test suite on API 26/33/35, and C58 revalidates current post-record behavior.
- The repeatable privacy-first Firebase procedure is `firebase-analytics.md`; live Console evidence still requires the four Android Firebase values and a physical device.
- V1 remains open for physical-device/Doze/TalkBack/OEM evidence, a release-owner signed AAB, Play Internal/Closed testing, Console declarations and the final pre-merge main rebaseline.

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
