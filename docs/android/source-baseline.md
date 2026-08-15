# Android Port Source Baseline

**Baseline date:** 2026-08-15
**Development branch:** `android-dev`
**Reference main commit:** `432b34c` (`chore(ios): prepare production TestFlight build 51`), published iOS 1.0.6
**Previous reference:** `3e52fb2f5367052bae8e664eee2c1043de76c377` (`Bump iOS build to 40 for production TestFlight`)
**Baseline merge checkpoint:** C61 merge commit `36a6d4d` containing `main@432b34c`

This file pins the source material used to reproduce the current product on Android. A later change on `main` does not silently change the Android contract. It starts a new rebaseline procedure.

## 1. Branch isolation

- Android code and Android port documents are changed only on `android-dev`.
- iOS and API changes are authored and released from `main`/`staging`, not from the Android workstream.
- `main` is merged into `android-dev` only at an explicit rebaseline checkpoint.
- Android is merged back only after the release gates in `android-port-master-plan.md` pass.
- A parity fix must not edit iOS merely to make Android easier to match. A product/API defect is fixed on `main` first and then rebaselined.

## 2. Authority order

When sources disagree, use the first applicable source below and record the disagreement.

1. Backend route/service behavior and passing backend tests at the pinned commit
2. Shipping iOS behavior and passing iOS tests at the pinned commit
3. `ios/MedicationApp/Resources/Localizable.strings` for user-facing Japanese copy
4. Product specifications under `specs/`
5. Android documents in this directory
6. Existing Android behavior

The older feature specifications are intent documents, not always the current runtime contract. For example, `specs/0115-slot-bulk-dose-recording/spec.md` says caregivers do not use bulk recording, while the current API and iOS app do. Android follows the current API/iOS behavior and records the conflict here rather than reviving the obsolete rule.

## 3. Canonical source map

| Concern | Canonical sources |
|---|---|
| App entry, mode restoration, auth routing | `RootView.swift`, `SessionStore.swift`, `SessionStoreTests.swift` |
| HTTP/session behavior | `APIClient.swift`, `APIClientTests.swift`, `APIError.swift` |
| Japanese copy | `Resources/Localizable.strings` |
| Shared visual tokens | `Shared/AppConstants.swift`, `PatientUI`, `CaregiverUI` |
| Patient shell/tutorial/settings | `PatientReadOnlyView.swift` |
| Patient Today | `PatientTodayView.swift`, `PatientTodayViewModel.swift`, Today tests |
| Caregiver shell/tutorial | `CaregiverHomeView.swift` |
| Patient management | `PatientManagementView.swift` and child views |
| Caregiver Today | `CaregiverTodayView.swift`, `CaregiverTodayViewModel.swift`, `TodayCaregiverFlowTests.swift` |
| Medication/regimen | `MedicationListView.swift`, `MedicationFormView*`, medication/regimen routes |
| Inventory | `InventoryListView.swift`, `InventoryDetailView.swift`, inventory routes |
| History/PDF | History views/view model, PDF feature files, history routes |
| Local patient reminders | notification feature files and notification tests |
| Caregiver push | push settings, `DeviceTokenManager`, push routes, push tests |
| Analytics/privacy | `AnalyticsService.swift`, `docs/android/firebase-analytics.md`, settings consent UI |

## 4. Main changes since the previous Android baseline

The prior pinned source was `main@1d9d19e`. C31 reviewed the complete `1d9d19e..1cf8aef` API/iOS/spec delta and merged it without cherry-picking. The following table records each initial recheck impact and its C32–C35 closure; older Android evidence alone did not close these rows.

| Change | Current contract | Android impact |
|---|---|---|
| Public link exchange | `POST /api/patient/link` is sent without Authorization. A failure on this public request must not clear an existing session. A caregiver token is rejected by the backend. | Add per-request auth policy and tests. `AU-002`, `SH-006` are recheck-required. |
| Link error localization | Validation, expired/not-found, auth/forbidden, network and other failures have stable Japanese UI messages. | Remove accidental raw backend/exception copy from link UI. |
| Patient installation safety | iOS discards Keychain-only patient tokens after reinstall using an installation marker. | Android must prove uninstall/reinstall cannot restore a patient token; `allowBackup=false` is necessary but requires device evidence. |
| Patient record follow-up | Successful scheduled individual/bulk recording updates UI immediately, then rebuilds the seven-day reminder plan off the mutation critical path. | Do not wait for reminder maintenance before success feedback. Rebuild only after at least one scheduled record changes. |
| Next-day reminder retention | Rebuilding after a dose must retain valid notifications on the next day and across a month boundary. | Add deterministic boundary tests before changing scheduler code. |
| Cross-tab history freshness | Successful individual, bulk, PRN and delete operations signal that history is stale. History refreshes when active/visible. | Define a shared invalidation revision/event; do not rely only on app restart. |
| Lazy tab lifetime | Patient and caregiver tabs are instantiated lazily and remain alive after first visit. Hidden tabs do not receive taps/accessibility focus. | Navigation must preserve scroll/data/form state and avoid unnecessary reload flicker. |
| Caregiver proxy bulk | Caregivers can bulk-record an older missed slot; the patient-only `-30/+60` recording window does not block caregiver proxy recording. | Separate patient and caregiver eligibility policy. Do not share a UI-only window guard. |
| Caregiver mutation stability | After a successful mutation, a refresh failure preserves rendered data and shows an error instead of replacing the screen with empty/error state. | Repository state must distinguish mutation success from follow-up refresh failure. |
| Inventory bulk performance | Bulk inventory changes, adjustments and alert transitions are applied in bounded database work while preserving all-or-nothing inventory behavior. | Android response/UX semantics stay the same; add timeout-safe loading and no client-side inventory authority. |
| Patient deletion integrity | Deletion removes slot-time revisions and all dependent records in server-defined order. | UI waits for server success, then clears selection and invalidates dependent screens. No client cascade. |
| Account deletion cleanup | Server deletion also disables/removes caregiver device registrations. | Android performs the server delete before local reset and treats local FCM cleanup as defense in depth. |
| Caregiver Today/History refresh | Caregiver mutations notify history and the currently visible tab refreshes without destructive state reset. | Required in Phase 3C repository design. |
| Patient recording streak | `GET /api/patient/history/streak` returns `currentStreakDays`, `isAtLeast` and `todayStatus`. Patient History inserts a `連続記録` card after today's progress; streak failure is supplementary and must not replace usable history. | C32 added the typed endpoint, isolated lifecycle, exact card/copy and API-35 evidence; `PH-009` is `IMPLEMENTED`. |
| Status-focused Caregiver Today | Current iOS removes the next-dose hero, medicine list and top bulk action. The screen now leads with `今日の服薬状況`, then optional PRN and the four-slot timeline; recording remains on eligible timeline rows. | C33 removed obsolete hierarchy/next styling, retained mutations and captured production UI; `CG-008` is `IMPLEMENTED`. |
| Missed-dose caregiver push | The server sends privacy-minimal `type=DOSE_MISSED` payloads one hour after an unrecorded scheduled slot. iOS accepts both `DOSE_TAKEN` and `DOSE_MISSED`, validates the same patient/date/slot fields and opens caregiver History. | C34 added the strict two-type parser and identical exact History routing; `XP-002` is `IMPLEMENTED`. |
| Public guide and release operations | Public-site guide, screenshots, cron configuration notes and release checklist changed without adding an Android runtime contract. | Preserve through the merge; no Android feature row changes solely for these files. |

### C58 delta: `main@1cf8aef..3e52fb2`

C58 merged and reviewed the complete eight-file API/iOS delta rather than copying isolated Swift behavior. It adds three current contracts:

| Change | Current contract | Android impact |
|---|---|---|
| Patient post-record feedback | Individual, slot-bulk and PRN success is committed to the visible Today state immediately. The authoritative Today refresh runs afterward without replacing the usable screen with a blocking progress overlay. | Successful Android mutations now start a silent reconciliation only after a real update; failures and zero-update bulk results do not trigger a false success refresh. |
| Caregiver post-record feedback | Individual, delete, PRN and fully successful slot-bulk mutations keep their optimistic result and success message interactive while authoritative data reloads. Partial slot-bulk remains visibly refreshing because server inventory results are authoritative. | Caregiver Today uses nonblocking same-patient reconciliation for complete success and preserves the existing visible recovery path for partial inventory results. Deleting a record restores `MISSED` when its scheduled time is over one hour past, otherwise `PENDING`, before reconciliation. |
| Dose-write performance | API history/event and inventory effects run concurrently where independent, while caregiver push remains ordered after both succeed. Response payloads, errors, idempotency and inventory authority are unchanged. | No DTO or endpoint change. Android must not infer inventory locally; the complete API suite and typecheck are the contract gate. |

### C61 delta: `main@3e52fb2..432b34c` (published iOS 1.0.6 Build 51)

C61 merged the complete published source into `android-dev`; it did not copy isolated Swift files. The release contract added or materially changed these Android surfaces:

| Change | Published contract | Android status |
|---|---|---|
| Actual dose time and late threshold | Today/history payloads include optional `takenAt`. A record is late at exactly 60 minutes or more after its scheduled time. | Implemented and contract-tested in the wire/domain boundary. |
| Patient recording lifetime | A scheduled dose opens 30 minutes before its time and stays recordable until 04:00 at the start of the following Tokyo day, exclusive. | Implemented in one shared policy with boundary tests. |
| Patient next action | A late-but-recordable slot remains in `今日の記録`, while a later upcoming slot owns `次のお薬`. | Implemented in the selector and Patient Today UI. |
| Patient Today and history | Show four-slot progress, actual record time, delay, late state and post-record scroll-to-top. | Implemented; API 35 Patient Today 27/27 and History 19/19 UI tests passed. |
| Caregiver Today | Show actual record time, late status, recorder and a late-dose alert without blocking proxy actions. | Implemented; API 35 Caregiver Today 20/20 UI tests passed. |
| Medication form and inventory | Guided medication entry, inventory calculator, field-local validation and redesigned inventory editing. Slot times must be strictly morning &lt; noon &lt; evening &lt; bedtime. | Implemented: strict slot ordering, scheduled-supply calculator and the action-first refill/correction editor match the published flow. |
| Caregiver push routing | Select the payload's linked patient before opening the exact destination. Scheduled-dose push copy keeps the time-slot label even when late. | Verified: the strict payload route selects the linked patient before opening exact History date/slot; API push copy remains server-authoritative. |
| History availability | Current history remains reachable without a billing prompt under the initial billing-off release policy. | Verified in the billing-off cross-API regression. |

### C62 corrective source-level audit

The first C61 conclusion was reopened after direct Swift/Kotlin comparison showed that several Android tests still asserted older layouts. The corrective audit replaced those assumptions with the published expandable Patient History, compact Patient Today and exact Caregiver Today summaries, grouped Caregiver History, blank new-medication dose-count default and inventory detail colors. Its matrix passed 202/202 JVM tests, Lint and 267/267 instrumentation tests on API 26, 33 and 35.

### C63 tutorial fidelity correction

The C62 tutorial conclusion was reopened after direct comparison with the private published `PatientTutorialSampleView`, `CaregiverTutorialSampleView` and shared `GuidedTutorialOverlay`. Published iOS renders dedicated simplified fixtures; it does not inject synthetic repositories into production tabs. Android now follows that contract with three Patient sample destinations mapped across four steps, ten Caregiver sample destinations, exact fixed Japanese copy/data, matching role icons and a compact bottom guide card. The active live screen remains hidden from interaction and accessibility. Light fixtures for all 14 steps were visually inspected; dedicated dark/200% reachability and tutorial action tests pass. The complete matrix passes 202/202 JVM tests, Lint and 272/272 instrumentation tests on API 26, 33 and 35 (816/816).

### C64 local completion audit

C64 enumerated all 69 parity rows against current sources, tests and release verifiers. Sixty-three implementation rows are complete; the six remaining `PARTIAL` rows require live Firebase, physical-device/TalkBack/OEM or release-owner Play evidence and are not treated as missing emulator implementation. The audit found one local boundary defect: medication validation and supply-calculation presentation text crossed from the data layer as Japanese strings. It now crosses as typed validation codes and calculation values, with the Compose layer selecting `strings.xml` resources. JVM 202/202, Lint, Debug/Release assembly, Release APK compatibility, Play asset validation, API 35 UI 272/272 and the affected medication-form class 25/25 on API 26/33/35 pass after the correction.

The later staging-only privacy-safe Analytics change and Build 52 are not part of this published baseline and must enter through a future explicit rebaseline if released.

## 5. Rebaseline procedure

Run this procedure whenever API or iOS behavior changes on `main`.

1. Ensure `android-dev` is clean and all current Android work is committed or intentionally preserved.
2. Record the old and new `main` SHAs.
3. Merge `main` into `android-dev`; do not cherry-pick individual runtime files without their tests.
4. Review `git diff <old-main>..<new-main>` for `api/`, `ios/`, `specs/`, legal/privacy and Firebase changes.
5. Update this file and `api-contracts.md`.
6. Mark affected matrix rows `RECHECK_REQUIRED` before implementation.
7. Update DTO fixtures and behavioral tests first.
8. Recapture changed iOS screen states using the pinned build.
9. Re-run the affected vertical-slice gates.
10. Move a row back to `IMPLEMENTED` or `VERIFIED` only with new evidence.

## 6. Baseline acceptance checklist

- [x] `android-dev` contains published `main@432b34c` through the C61 merge checkpoint `36a6d4d`.
- [x] The main delta was reviewed across API, iOS, and tests.
- [x] Runtime/spec conflicts are explicitly identified.
- [x] All affected Android contract tests have been updated for actual-time/late-dose behavior, slot ordering, medication supply calculation, action-first inventory editing and patient-first push routing.
- [x] All affected emulator-verifiable Android implementation rows passed 202/202 JVM tests, Lint and 272/272 UI tests on API 26, 33 and 35 after the C63 tutorial fidelity correction. Physical/TalkBack/OEM evidence remains the separate V01 gate.
- [x] Current iOS source/runtime references have been captured for every emulator-verifiable scoped state through C37–C56; physical/TalkBack/OEM variants remain an explicit V1 gate.
